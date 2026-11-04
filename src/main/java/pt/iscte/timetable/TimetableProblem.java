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

/** Problem definition and evaluation, fulfilling the role of the supplied NMMin example.
 *  Cada aula é uma variável; o valor da variável é a sala escolhida.
 *  O dia e a hora de cada aula vêm do CSV e não são alterados.
 *  */
public final class TimetableProblem extends AbstractIntegerProblem {
  public static final int OBJECTIVES = 2;
  public static final int GLOBAL_CONSTRAINTS = 1; // regra hard: choques de sala
  public static final int VARIABLES_PER_OCCURRENCE = 1; // só a sala
  private final DataSet data; //Todas as aulas e salas lidas do CSV
  private final StudyConfig config; //Os parâmetros do ".json", como os pesos do objetivo 1
  private final List<LessonDomain> domains; //Para cada aula, a lista das salas possíveis
  private final double typeScale, seatScale; //

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
      // Variável i = índice da sala na lista de salas possíveis da aula i (0 .. nº de salas possíveis - 1).
      lower.add(0);
      upper.add(Math.max(0, domain.rooms().size() - 1));
    }
    variableBounds(lower, upper); // numberOfVariables() = nº de aulas
    // Escalas fixas para pôr as duas parcelas do objetivo 1 entre 0 e 1.
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
  }
  @Override public IntegerSolution evaluate(IntegerSolution solution) {
    // Durante a pesquisa só se calcula o que depende das salas (mais rápido).
    ScheduleMetrics metrics = roomMetrics(data.lessons(), decode(solution));
    double hardViolations = metrics.hardViolations();
    double softViolations = softPenalty(metrics);
    solution.objectives()[0] = hardViolations;
    solution.objectives()[1] = softViolations;
    // Restrição do jMetal: 0 = cumprida; valor negativo = violada (quanto mais negativo, pior).
    // Com o DominanceWithConstraintsComparator, o NSGA-II prefere sempre horários com menos choques.
    solution.constraints()[0] = -hardViolations;
    return solution;
  }

  /** Objetivo 1 (soft): aulas em sala de tipo diferente do pedido + lugares desperdiçados, ambos entre 0 e 1. */
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
    // Aulas que não precisam de sala (sem_sala ou 0 inscritos) ficam sem sala.
    Room room = !lesson.needsRoom() || domain.rooms().isEmpty() ? null : domain.rooms().get(value);
    // Dia e hora originais do CSV; aulas sem data continuam sem data.
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

  /** Só as métricas que dependem da escolha de salas; as que dependem das horas ficam a 0 (são fixas). */
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

  /** Aulas colocadas numa sala que não tem a característica pedida (regra soft). */
  private static long countTypeMismatches(List<Lesson> lessons, List<Assignment> schedule) {
    return schedule.stream().filter(a -> a.room() != null)
            .filter(a -> !a.room().features().contains(lessons.get(a.lessonIndex()).requestedFeature())).count();
  }

  /** Índices das aulas que estão em choque de sala (mesma sala, mesmo dia, horas sobrepostas). */
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
