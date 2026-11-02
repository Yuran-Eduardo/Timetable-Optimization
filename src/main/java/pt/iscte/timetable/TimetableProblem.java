package pt.iscte.timetable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDate;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

/** Problem definition and evaluation, fulfilling the role of the supplied NMMin example. */
public final class TimetableProblem extends AbstractIntegerProblem {
  public static final int OBJECTIVES = 2;
  public static final int GLOBAL_CONSTRAINTS = 0;
  public static final int VARIABLES_PER_OCCURRENCE = 3;
  private final DataSet data;
  private final StudyConfig config;
  private final List<LessonDomain> domains;
  private final double gapScale, seatScale;

  /** Simple scenario constructor: N source occurrences, each with date/start/room integer bounds. */
  public TimetableProblem(int numberOfOccurrences, DataSet source, StudyConfig config, RoomPolicy policy) {
    this(select(numberOfOccurrences, source), config,
        Preflight.inspect(select(numberOfOccurrences, source), policy, config));
  }

  private static DataSet select(int size, DataSet source) {
    if (size < 1 || size > source.lessons().size())
      throw new IllegalArgumentException("Requested " + size + " occurrences, available " + source.lessons().size());
    return new DataSet(List.copyOf(source.lessons().subList(0, size)), source.rooms(),
        source.featureColumns(), source.inputErrors(), source.sourceHashes());
  }

  public TimetableProblem(DataSet data, StudyConfig config, Preflight.Result validation) {
    validation.requireRunnable(config);
    this.data = data; this.config = config; domains = validation.domains();
    if (domains.size() != data.lessons().size()) throw new IllegalArgumentException("Unmatched lesson domains");
    name("Timetable-" + data.lessons().size());
    // Current jMetal uses these setters, as in the supplied NMMin.
    numberOfObjectives(OBJECTIVES);
    numberOfConstraints(GLOBAL_CONSTRAINTS);
    int decisionVariables = Math.multiplyExact(VARIABLES_PER_OCCURRENCE, data.lessons().size());
    List<Integer> lower = new ArrayList<>(decisionVariables), upper = new ArrayList<>(decisionVariables);
    for (int i = 0; i < domains.size(); i++) {
      LessonDomain domain = domains.get(i);
      if (domain.lastStartIndex() < 0 || !config.allowIncompleteData() && (domain.dates().isEmpty()
          || data.lessons().get(i).needsRoom() && domain.rooms().isEmpty()))
        throw new IllegalArgumentException("Empty local domain: " + data.lessons().get(i).id());
      lower.addAll(List.of(0, 0, 0));
      upper.addAll(List.of(Math.max(0, domain.dates().size() - 1),
          domain.dates().isEmpty() ? 0 : domain.lastStartIndex(), Math.max(0, domain.rooms().size() - 1)));
    }
    // AbstractIntegerProblem.numberOfVariables() returns bounds.size(): 3 * occurrences.
    // Each triplet defines a date index, a start index and a categorical room index.
    variableBounds(lower, upper);
    long groups = data.lessons().stream().flatMap(l -> l.groups().stream()).distinct().count();
    long days = java.time.temporal.ChronoUnit.DAYS.between(config.horizonStart(), config.horizonEnd()) + 1;
    gapScale = Math.max(1.0, (double) groups * days * (config.closingMinute() - config.openingMinute()));
    double maximumExcess = 0;
    for (int i = 0; i < domains.size(); i++) {
      int students = data.lessons().get(i).students();
      maximumExcess += domains.get(i).rooms().stream()
          .mapToInt(r -> Math.max(0, r.capacity() - students)).max().orElse(0);
    }
    seatScale = Math.max(1.0, maximumExcess);
  }
  @Override public IntegerSolution evaluate(IntegerSolution solution) {
    ScheduleMetrics metrics = calculateMetrics(data.lessons(), decode(solution), config);
    double hardViolations = metrics.hardViolations();
    double softViolations = softPenalty(metrics);
    solution.objectives()[0] = hardViolations;
    solution.objectives()[1] = softViolations;
    return solution;
  }

  public double softPenalty(ScheduleMetrics metrics) {
    return config.softWeights().get("preferred") * metrics.outsidePreferred() / data.lessons().size()
        + config.softWeights().get("gaps") * metrics.gapMinutes() / gapScale
        + config.softWeights().get("seats") * metrics.excessSeats() / seatScale;
  }

  public List<Assignment> decode(IntegerSolution solution) {
    if (solution.variables().size() != numberOfVariables()) throw new IllegalArgumentException("Wrong chromosome length");
    List<Assignment> result = new ArrayList<>();
    for (int i = 0; i < data.lessons().size(); i++) result.add(decodeOne(i, solution));
    return result;
  }
  private Assignment decodeOne(int i, IntegerSolution solution) {
    for (int k = 0; k < 3; k++) {
      int index = 3 * i + k, value = solution.variables().get(index);
      if (value < variableBounds().get(index).getLowerBound() || value > variableBounds().get(index).getUpperBound())
        throw new IllegalArgumentException("Out-of-domain gene " + index);
    }
    LessonDomain domain = domains.get(i);
    int start = config.openingMinute() + config.gridMinutes() * solution.variables().get(3 * i + 1);
    Room room = domain.rooms().isEmpty() ? null : domain.rooms().get(solution.variables().get(3 * i + 2));
    if (domain.dates().isEmpty()) return new Assignment(i, null, -1, -1, room);
    return new Assignment(i, domain.dates().get(solution.variables().get(3 * i)), start,
        start + data.lessons().get(i).duration(), room);
  }
  public DataSet data() { return data; }
  public StudyConfig config() { return config; }
  public boolean hasRoomCandidates(int index) { return !domains.get(index).rooms().isEmpty(); }

  private record ResourceDay(String resource, LocalDate date) {}
  private record SequenceKey(String group, String unit, LocalDate date) {}
  private record Overlaps(long pairs, long minutes) {}
  private record Sequences(long violations, long excessMinutes) {}
  private static final Comparator<Assignment> BY_START = Comparator.comparingInt(Assignment::start)
      .thenComparingInt(Assignment::end).thenComparingInt(Assignment::lessonIndex);

  /** Raw domain metrics, also used to validate exported schedules. */
  public static ScheduleMetrics calculateMetrics(List<Lesson> lessons,
      List<Assignment> assignments, StudyConfig config) {
    Map<ResourceDay, List<Assignment>> roomDays = new HashMap<>(), classDays = new HashMap<>();
    Map<SequenceKey, List<Assignment>> sequences = new HashMap<>();
    for (Assignment a : assignments) {
      Lesson lesson = lessons.get(a.lessonIndex());
      if (a.date() == null) continue; // Unknown dates are reported, never placed in an invented week.
      if (a.room() != null)
        roomDays.computeIfAbsent(new ResourceDay(a.room().id(), a.date()), k -> new ArrayList<>()).add(a);
      for (String group : lesson.groups()) {
        classDays.computeIfAbsent(new ResourceDay(group, a.date()), k -> new ArrayList<>()).add(a);
        sequences.computeIfAbsent(new SequenceKey(group, lesson.unit(), a.date()), k -> new ArrayList<>()).add(a);
      }
    }
    roomDays.values().forEach(day -> day.sort(BY_START));
    classDays.values().forEach(day -> day.sort(BY_START));
    sequences.values().forEach(day -> day.sort(BY_START));
    Overlaps rooms = countRoomConflicts(roomDays);
    Overlaps classes = countClassConflicts(classDays);
    Sequences blocks = countLongSequences(lessons, sequences);
    return new ScheduleMetrics(countOutsidePreferred(lessons, assignments, config),
        sumClassGaps(classDays), sumExcessSeats(lessons, assignments), rooms.pairs(), classes.pairs(),
        blocks.violations(), rooms.minutes(), classes.minutes(), blocks.excessMinutes(),
        sumSeatDeficit(lessons, assignments), assignments.stream()
            .filter(a -> lessons.get(a.lessonIndex()).duration() > 180).count(),
        assignments.stream().filter(a -> lessons.get(a.lessonIndex()).needsRoom() && a.room() == null).count(),
        lessons.stream().filter(l -> l.originalDate() == null || l.groups().isEmpty() || l.unit().isBlank()).count(),
        assignments.stream().filter(a -> !preferenceKnown(lessons.get(a.lessonIndex()), a, config)).count());
  }

  private static long countOutsidePreferred(List<Lesson> lessons, List<Assignment> schedule, StudyConfig config) {
    // A shared occurrence counts once if any participating class fails its preference.
    return schedule.stream().filter(a -> preferenceKnown(lessons.get(a.lessonIndex()), a, config))
        .filter(a -> !config.preferred(lessons.get(a.lessonIndex()), a.start(), a.end())).count();
  }

  private static boolean preferenceKnown(Lesson lesson, Assignment assignment, StudyConfig config) {
    return assignment.date() != null && !lesson.groups().isEmpty()
        && lesson.groups().stream().allMatch(config.profilesByClass()::containsKey);
  }

  private static long sumExcessSeats(List<Lesson> lessons, List<Assignment> schedule) {
    return schedule.stream().filter(a -> a.room() != null)
        .mapToLong(a -> Math.max(0, a.room().capacity() - lessons.get(a.lessonIndex()).students())).sum();
  }

  private static long sumSeatDeficit(List<Lesson> lessons, List<Assignment> schedule) {
    // Diagnostic only; insufficient capacity cannot enter a valid room domain.
    return schedule.stream().filter(a -> a.room() != null)
        .mapToLong(a -> Math.max(0, lessons.get(a.lessonIndex()).students() - a.room().capacity())).sum();
  }

  private static long sumClassGaps(Map<ResourceDay, List<Assignment>> classDays) {
    long gaps = 0;
    for (List<Assignment> day : classDays.values()) {
      if (day.isEmpty()) continue;
      int occupiedUntil = day.getFirst().end();
      for (int i = 1; i < day.size(); i++) {
        gaps += Math.max(0, day.get(i).start() - occupiedUntil);
        occupiedUntil = Math.max(occupiedUntil, day.get(i).end());
      }
    }
    return gaps; // No time before/after classes and no automatic lunch deduction.
  }

  private static Overlaps countRoomConflicts(Map<ResourceDay, List<Assignment>> roomDays) {
    long pairs = 0, minutes = 0;
    for (List<Assignment> day : roomDays.values())
      for (int i = 0; i < day.size(); i++)
        for (int j = i + 1; j < day.size() && day.get(j).start() < day.get(i).end(); j++) {
          pairs++; minutes += overlap(day.get(i), day.get(j));
        }
    return new Overlaps(pairs, minutes);
  }

  private static Overlaps countClassConflicts(Map<ResourceDay, List<Assignment>> classDays) {
    Set<Long> pairs = new HashSet<>();
    long minutes = 0;
    for (List<Assignment> day : classDays.values())
      for (int i = 0; i < day.size(); i++)
        for (int j = i + 1; j < day.size() && day.get(j).start() < day.get(i).end(); j++) {
          Assignment a = day.get(i), b = day.get(j);
          long pair = ((long) Math.min(a.lessonIndex(), b.lessonIndex()) << 32)
              | (Math.max(a.lessonIndex(), b.lessonIndex()) & 0xffffffffL);
          // Sharing several classes does not duplicate the same unordered conflict pair.
          if (pairs.add(pair)) minutes += overlap(a, b);
        }
    return new Overlaps(pairs.size(), minutes);
  }

  private static Sequences countLongSequences(List<Lesson> lessons,
      Map<SequenceKey, List<Assignment>> sequences) {
    long violations = 0, excess = 0;
    for (List<Assignment> day : sequences.values()) {
      int count = 0, end = 0, teaching = 0;
      for (Assignment a : day) {
        if (count > 0 && a.start() - end >= 30) {
          if (count >= 2 && teaching > 180) { violations++; excess += teaching - 180; }
          count = 0; teaching = 0;
        }
        count++; teaching += lessons.get(a.lessonIndex()).duration();
        end = count == 1 ? a.end() : Math.max(end, a.end());
      }
      // One long occurrence is a documented provisional exception, not a long sequence.
      if (count >= 2 && teaching > 180) { violations++; excess += teaching - 180; }
    }
    return new Sequences(violations, excess);
  }

  private static int overlap(Assignment a, Assignment b) {
    return Math.max(0, Math.min(a.end(), b.end()) - Math.max(a.start(), b.start()));
  }
}
