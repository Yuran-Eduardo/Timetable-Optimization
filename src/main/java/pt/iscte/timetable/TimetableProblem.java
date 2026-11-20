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
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

/** Problem definition and evaluation, fulfilling the role of the supplied NMMin example.
 *  Each lesson is one variable; its value is the chosen room (index in the lesson's list of possible rooms).
 *  The day and time of each lesson come from the CSV and are never changed. */
public final class TimetableProblem extends AbstractIntegerProblem {
  /** One objective per rule: 0 room clashes, 1 lessons without a room, 2 wrong room type, 3 empty seats. */
  public static final int OBJECTIVES = 4;
  public static final int GLOBAL_CONSTRAINTS = 1; // hard rule: no room clashes
  public static final int VARIABLES_PER_OCCURRENCE = 1; // only the room
  private final DataSet data; //Todas as aulas e salas lidas do CSV
  private final StudyConfig config; //Os parâmetros do ".json", como os pesos do objetivo
  private final List<LessonDomain> domains; ////Para cada aula, a lista das salas possíveis
  private final double typeScale, seatScale;
  /** Random attempts to find a free room before choosing one by hand (avoids endless loops). */
  private static final int MAX_RANDOM_TRIES = 10;
  /** Order used by repair(): lessons with fewer possible rooms (usually the biggest) go first. */
  private final List<Integer> repairOrder;

  /** Simple scenario constructor: N source occurrences, each with one room variable. */
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
      if (!config.allowIncompleteData() && data.lessons().get(i).needsRoom() && domain.rooms().isEmpty())
        throw new IllegalArgumentException("Empty local domain: " + data.lessons().get(i).id());
      // Variable i = index of the room in lesson i's list of possible rooms (0 .. number of possible rooms - 1).
      lower.add(0);
      upper.add(Math.max(0, domain.rooms().size() - 1));
    }
    variableBounds(lower, upper); // numberOfVariables() = number of lessons
    // Fixed scales that put both parts of objective 1 between 0 and 1.
    long withRoom = 0;
    for (int i = 0; i < domains.size(); i++)
      if (data.lessons().get(i).needsRoom() && !domains.get(i).rooms().isEmpty()) withRoom++;
    typeScale = Math.max(1.0, withRoom);
    double maximumExcess = 0;
    for (int i = 0; i < domains.size(); i++) {
      int students = data.lessons().get(i).students();
      maximumExcess += domains.get(i).rooms().stream()
              .mapToInt(r -> Math.max(0, r.capacity() - students)).max().orElse(0);
    }
    seatScale = Math.max(1.0, maximumExcess);
    repairOrder = new ArrayList<>();
    for (int i = 0; i < domains.size(); i++) repairOrder.add(i);
    repairOrder.sort(Comparator.comparingInt(i -> domains.get(i).rooms().size()));
  }

  /** Counts the timetables created by each run (each run uses its own thread). */
  private final ThreadLocal<Integer> created = ThreadLocal.withInitial(() -> 0);

  /** Professor's suggestion: jMetal calls createSolution() to build each new timetable of the initial population.
   *  Phase B: the first timetable of each run is greedy (every lesson in the first free room of its list:
   *  requested type first, then the smallest room). The others stay random. Then they are repaired. */
  @Override public IntegerSolution createSolution() {
    IntegerSolution solution = super.createSolution(); // random room for every lesson (jMetal default)
    int count = created.get();
    created.set(count + 1);
    if (count % config.populationSize() == 0) {
      // Greedy timetable: start every lesson in position 0 of its list and fix clashes by hand (no random tries).
      for (int i = 0; i < solution.variables().size(); i++) solution.variables().set(i, 0);
      repair(solution, false);
    } else {
      repair(solution, true);
    }
    return solution;
  }

  /** Changes the room of every lesson that clashes with a lesson already placed in the same room
   *  at the same time. Used by createSolution(), TimetableCrossover and TimetableMutation. */
  public void repair(IntegerSolution solution) {
    repair(solution, true);
  }

  /** randomTries = false: skip the random attempts and take the first free room of the list (greedy). */
  private void repair(IntegerSolution solution, boolean randomTries) {
    // Rooms already used: (room, day) -> lessons placed there so far.
    Map<ResourceDay, List<Integer>> used = new HashMap<>();
    for (int i : repairOrder) {
      Lesson lesson = data.lessons().get(i);
      List<Room> rooms = domains.get(i).rooms();
      if (!lesson.needsRoom() || rooms.isEmpty() || lesson.originalDate() == null) continue;
      int choice = solution.variables().get(i);
      if (!isFree(rooms.get(choice), lesson, used)) {
        choice = -1;
        // 1) Try a few random rooms.
        for (int t = 0; randomTries && t < MAX_RANDOM_TRIES && choice < 0; t++) {
          int r = JMetalRandom.getInstance().nextInt(0, rooms.size() - 1);
          if (isFree(rooms.get(r), lesson, used)) choice = r;
        }
        // 2) Otherwise choose by hand: the first free room in the list (best type, then smallest).
        for (int r = 0; r < rooms.size() && choice < 0; r++)
          if (isFree(rooms.get(r), lesson, used)) choice = r;
        // 3) No free room at all: keep the old one; the clash is counted in objective 0.
        if (choice < 0) choice = solution.variables().get(i);
        solution.variables().set(i, choice);
      }
      used.computeIfAbsent(new ResourceDay(rooms.get(choice).id(), lesson.originalDate()), k -> new ArrayList<>()).add(i);
    }
  }

  /** True if no lesson already placed in this room, on the same day, overlaps this lesson's time. */
  private boolean isFree(Room room, Lesson lesson, Map<ResourceDay, List<Integer>> used) {
    for (int other : used.getOrDefault(new ResourceDay(room.id(), lesson.originalDate()), List.of())) {
      Lesson o = data.lessons().get(other);
      if (o.start() < lesson.end() && lesson.start() < o.end()) return false;
    }
    return true;
  }
  @Override public IntegerSolution evaluate(IntegerSolution solution) {
    // During the search only what depends on the rooms is computed (faster).
    ScheduleMetrics metrics = roomMetrics(data.lessons(), decode(solution));
    double hardViolations = metrics.hardViolations();
    double[] values = objectiveValues(metrics);
    for (int k = 0; k < OBJECTIVES; k++) solution.objectives()[k] = values[k];
    // jMetal constraint: 0 = satisfied; negative = violated (more negative is worse).
    // With DominanceWithConstraintsComparator, NSGA-II always prefers timetables with fewer clashes.
    solution.constraints()[0] = -hardViolations;
    return solution;
  }

  /** One value per rule, all to minimise. Hard rules: 0 and 1. Soft rules: 2 and 3. */
  public double[] objectiveValues(ScheduleMetrics metrics) {
    return new double[] {
            metrics.roomConflictPairs(), // 0 (hard): pairs of lessons in the same room at the same time
            metrics.unassignedRooms(),   // 1 (hard): lessons that need a room and have none
            metrics.typeMismatches(),    // 2 (soft): lessons in a room of a different type than requested
            metrics.excessSeats()        // 3 (soft): total empty seats
    };
  }

  /** Single soft score (wrong type + empty seats, scaled 0..1), used only to pick and report the best timetable. */
  public double softPenalty(ScheduleMetrics metrics) {
    return config.softWeights().get("type") * metrics.typeMismatches() / typeScale
            + config.softWeights().get("seats") * metrics.excessSeats() / seatScale;
  }

  public List<Assignment> decode(IntegerSolution solution) {
    if (solution.variables().size() != numberOfVariables()) throw new IllegalArgumentException("Wrong chromosome length");
    List<Assignment> result = new ArrayList<>();
    for (int i = 0; i < data.lessons().size(); i++) result.add(decodeOne(i, solution));
    return result;
  }
  private Assignment decodeOne(int i, IntegerSolution solution) {
    int value = solution.variables().get(i);
    if (value < variableBounds().get(i).getLowerBound() || value > variableBounds().get(i).getUpperBound())
      throw new IllegalArgumentException("Out-of-domain gene " + i);
    Lesson lesson = data.lessons().get(i);
    LessonDomain domain = domains.get(i);
    // Lessons that do not need a room (sem_sala) get no room.
    Room room = !lesson.needsRoom() || domain.rooms().isEmpty() ? null : domain.rooms().get(value);
    // Original day and time from the CSV; lessons without a date stay without a date.
    if (lesson.originalDate() == null) return new Assignment(i, null, -1, -1, room);
    return new Assignment(i, lesson.originalDate(), lesson.start(), lesson.end(), room);
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
            assignments.stream().filter(a -> !preferenceKnown(lessons.get(a.lessonIndex()), a, config)).count(),
            countTypeMismatches(lessons, assignments));
  }

  /** Only the metrics that depend on the rooms; time-dependent metrics stay at 0 (times are fixed). */
  public static ScheduleMetrics roomMetrics(List<Lesson> lessons, List<Assignment> assignments) {
    Map<ResourceDay, List<Assignment>> roomDays = new HashMap<>();
    for (Assignment a : assignments)
      if (a.date() != null && a.room() != null)
        roomDays.computeIfAbsent(new ResourceDay(a.room().id(), a.date()), k -> new ArrayList<>()).add(a);
    roomDays.values().forEach(day -> day.sort(BY_START));
    Overlaps rooms = countRoomConflicts(roomDays);
    long unassigned = assignments.stream().filter(a -> lessons.get(a.lessonIndex()).needsRoom() && a.room() == null).count();
    return new ScheduleMetrics(0, 0, sumExcessSeats(lessons, assignments), rooms.pairs(), 0, 0, rooms.minutes(), 0, 0,
            sumSeatDeficit(lessons, assignments), 0, unassigned, 0, 0, countTypeMismatches(lessons, assignments));
  }

  /** Lessons placed in a room without the requested feature (soft rule). */
  private static long countTypeMismatches(List<Lesson> lessons, List<Assignment> schedule) {
    return schedule.stream().filter(a -> a.room() != null)
            .filter(a -> !a.room().features().contains(lessons.get(a.lessonIndex()).requestedFeature())).count();
  }

  /** Indexes of the lessons in a room clash (same room, same day, overlapping times). */
  public static Set<Integer> roomConflictLessons(List<Assignment> assignments) {
    Map<ResourceDay, List<Assignment>> roomDays = new HashMap<>();
    for (Assignment a : assignments)
      if (a.date() != null && a.room() != null)
        roomDays.computeIfAbsent(new ResourceDay(a.room().id(), a.date()), k -> new ArrayList<>()).add(a);
    Set<Integer> result = new HashSet<>();
    for (List<Assignment> day : roomDays.values()) {
      day.sort(BY_START);
      for (int i = 0; i < day.size(); i++)
        for (int j = i + 1; j < day.size() && day.get(j).start() < day.get(i).end(); j++) {
          result.add(day.get(i).lessonIndex()); result.add(day.get(j).lessonIndex());
        }
    }
    return result;
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
