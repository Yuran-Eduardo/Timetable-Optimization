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

/** Define e avalia a atribuição de salas, adaptando o exemplo NMMin. Cada aula tem uma variável inteira; o dia e a hora permanecem os do CSV. */
public final class TimetableProblem extends AbstractIntegerProblem {
  /** Um objetivo por regra: choques, aulas sem sala, tipo diferente do pedido e lugares vazios. */
  public static final int OBJECTIVES = 4;
  public static final int GLOBAL_CONSTRAINTS = 1; // Restrição agregada das violações hard.
  public static final int VARIABLES_PER_OCCURRENCE = 1; // Apenas a sala é uma variável de decisão.
  private final DataSet data; //Todas as aulas e salas lidas do CSV
  private final StudyConfig config; //Os parâmetros do ".json", como os pesos do objetivo
  private final List<LessonDomain> domains; // Para cada aula, a lista das salas possíveis.
  private final double typeScale, seatScale;
  /** Limita as tentativas aleatórias antes de procurar a primeira sala livre. */
  private static final int MAX_RANDOM_TRIES = 10;
  /** Corrige primeiro as aulas com menos salas candidatas. */
  private final List<Integer> repairOrder;

  /** Cria um problema com as primeiras N aulas do ficheiro e uma variável por aula. */
  public TimetableProblem(int numberOfOccurrences, DataSet source, StudyConfig config, RoomPolicy policy) {
    this(select(numberOfOccurrences, source), config,
            Preflight.inspect(select(numberOfOccurrences, source), policy, config));
  }

  /** Seleciona as primeiras N ocorrências, preservando os dados de origem. */
  private static DataSet select(int size, DataSet source) {
    if (size < 1 || size > source.lessons().size())
      throw new IllegalArgumentException("Requested " + size + " occurrences, available " + source.lessons().size());
    return new DataSet(List.copyOf(source.lessons().subList(0, size)), source.rooms(),
            source.featureColumns(), source.inputErrors(), source.sourceHashes());
  }

  /** Valida a entrada e configura os limites dos genes, os objetivos, as escalas e a ordem de correção. */
  public TimetableProblem(DataSet data, StudyConfig config, Preflight.Result validation) {
    validation.requireRunnable(config);
    this.data = data; this.config = config; domains = validation.domains();
    if (domains.size() != data.lessons().size()) throw new IllegalArgumentException("Unmatched lesson domains");
    name("Timetable-" + data.lessons().size());
    // Configura os objetivos e a restrição pela API do jMetal.
    numberOfObjectives(OBJECTIVES);
    numberOfConstraints(GLOBAL_CONSTRAINTS);
    int decisionVariables = Math.multiplyExact(VARIABLES_PER_OCCURRENCE, data.lessons().size());
    List<Integer> lower = new ArrayList<>(decisionVariables), upper = new ArrayList<>(decisionVariables);
    for (int i = 0; i < domains.size(); i++) {
      LessonDomain domain = domains.get(i);
      if (!config.allowIncompleteData() && data.lessons().get(i).needsRoom() && domain.rooms().isEmpty())
        throw new IllegalArgumentException("Empty local domain: " + data.lessons().get(i).id());
      // O gene i guarda a posição da sala na lista de candidatas da aula i.
      lower.add(0);
      upper.add(Math.max(0, domain.rooms().size() - 1));
    }
    variableBounds(lower, upper); // O número de variáveis corresponde ao número de aulas.
    // Calcula escalas para a pontuação soft usada na seleção do CSV final.
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

  /** Conta as soluções iniciais por thread para inserir periodicamente uma solução gulosa. */
  private final ThreadLocal<Integer> created = ThreadLocal.withInitial(() -> 0);

  /** Cria uma solução inicial: a primeira de cada bloco de população é gulosa; as restantes são aleatórias e passam pela correção de choques. */
  @Override public IntegerSolution createSolution() {
    IntegerSolution solution = super.createSolution(); // Atribuição aleatória criada pelo jMetal.
    int count = created.get();
    created.set(count + 1);
    if (count % config.populationSize() == 0) {
      // Inicia cada aula na primeira candidata e corrige sem tentativas aleatórias.
      for (int i = 0; i < solution.variables().size(); i++) solution.variables().set(i, 0);
      repair(solution, false);
    } else {
      repair(solution, true);
    }
    return solution;
  }

  /** Tenta substituir salas ocupadas por candidatas livres, mantendo os horários das aulas. */
  public void repair(IntegerSolution solution) {
    repair(solution, true);
  }

  /** Escolhe a primeira sala livre; pode tentar salas aleatórias antes da procura sequencial. */
  private void repair(IntegerSolution solution, boolean randomTries) {
    // Agrupa por sala e data as aulas já colocadas.
    Map<ResourceDay, List<Integer>> used = new HashMap<>();
    for (int i : repairOrder) {
      Lesson lesson = data.lessons().get(i);
      List<Room> rooms = domains.get(i).rooms();
      if (!lesson.needsRoom() || rooms.isEmpty() || lesson.originalDate() == null) continue;
      int choice = solution.variables().get(i);
      if (!isFree(rooms.get(choice), lesson, used)) {
        choice = -1;
        // 1) Tenta algumas salas aleatórias.
        for (int t = 0; randomTries && t < MAX_RANDOM_TRIES && choice < 0; t++) {
          int r = JMetalRandom.getInstance().nextInt(0, rooms.size() - 1);
          if (isFree(rooms.get(r), lesson, used)) choice = r;
        }
        // 2) Procura a primeira sala livre na lista ordenada.
        for (int r = 0; r < rooms.size() && choice < 0; r++)
          if (isFree(rooms.get(r), lesson, used)) choice = r;
        // 3) Se não existir sala livre, mantém a anterior e deixa o choque para a avaliação.
        if (choice < 0) choice = solution.variables().get(i);
        solution.variables().set(i, choice);
      }
      used.computeIfAbsent(new ResourceDay(rooms.get(choice).id(), lesson.originalDate()), k -> new ArrayList<>()).add(i);
    }
  }

  /** Verifica se a sala está livre no intervalo da aula e na mesma data. */
  private boolean isFree(Room room, Lesson lesson, Map<ResourceDay, List<Integer>> used) {
    for (int other : used.getOrDefault(new ResourceDay(room.id(), lesson.originalDate()), List.of())) {
      Lesson o = data.lessons().get(other);
      if (o.start() < lesson.end() && lesson.start() < o.end()) return false;
    }
    return true;
  }
  /** Atribui os quatro objetivos e o valor agregado da restrição hard à solução. */
  @Override public IntegerSolution evaluate(IntegerSolution solution) {
    // Durante a procura, calcula apenas as métricas que dependem das salas.
    ScheduleMetrics metrics = roomMetrics(data.lessons(), decode(solution));
    double hardViolations = metrics.hardViolations();
    double[] values = objectiveValues(metrics);
    for (int k = 0; k < OBJECTIVES; k++) solution.objectives()[k] = values[k];
    // No jMetal, zero satisfaz a restrição e um valor negativo indica violação.
    // O comparador dá prioridade a menores violações hard, antes dos objetivos.
    solution.constraints()[0] = -hardViolations;
    return solution;
  }

  /** Devolve os quatro objetivos a minimizar: dois hard e dois soft. */
  public double[] objectiveValues(ScheduleMetrics metrics) {
    return new double[] {
            metrics.roomConflictPairs(), // 0 (hard): pares de aulas sobrepostas na mesma sala.
            metrics.unassignedRooms(),   // 1 (hard): aulas que precisam de sala e ficaram sem ela.
            metrics.typeMismatches(),    // 2 (soft): aulas numa sala de tipo diferente do pedido.
            metrics.excessSeats()        // 3 (soft): soma dos lugares vazios.
    };
  }

  /** Combina tipo e lugares com escalas fixas e pesos, apenas para selecionar e apresentar o CSV final. */
  public double softPenalty(ScheduleMetrics metrics) {
    return config.softWeights().get("type") * metrics.typeMismatches() / typeScale
            + config.softWeights().get("seats") * metrics.excessSeats() / seatScale;
  }

  /** Converte os genes inteiros numa lista de atribuições de salas. */
  public List<Assignment> decode(IntegerSolution solution) {
    if (solution.variables().size() != numberOfVariables()) throw new IllegalArgumentException("Wrong chromosome length");
    List<Assignment> result = new ArrayList<>();
    for (int i = 0; i < data.lessons().size(); i++) result.add(decodeOne(i, solution));
    return result;
  }
  /** Descodifica uma aula, verificando os limites do gene e preservando a data e as horas. */
  private Assignment decodeOne(int i, IntegerSolution solution) {
    int value = solution.variables().get(i);
    if (value < variableBounds().get(i).getLowerBound() || value > variableBounds().get(i).getUpperBound())
      throw new IllegalArgumentException("Out-of-domain gene " + i);
    Lesson lesson = data.lessons().get(i);
    LessonDomain domain = domains.get(i);
    // As atividades sem_sala não consomem uma sala física.
    Room room = !lesson.needsRoom() || domain.rooms().isEmpty() ? null : domain.rooms().get(value);
    // Preserva a data e as horas originais; uma data ausente permanece ausente.
    if (lesson.originalDate() == null) return new Assignment(i, null, -1, -1, room);
    return new Assignment(i, lesson.originalDate(), lesson.start(), lesson.end(), room);
  }
  /** Devolve os dados usados pelo problema. */
  public DataSet data() { return data; }
  /** Devolve a configuração do cenário. */
  public StudyConfig config() { return config; }
  /** Indica se a aula tem pelo menos uma sala candidata. */
  public boolean hasRoomCandidates(int index) { return !domains.get(index).rooms().isEmpty(); }

  private record ResourceDay(String resource, LocalDate date) {}
  private record SequenceKey(String group, String unit, LocalDate date) {}
  private record Overlaps(long pairs, long minutes) {}
  private record Sequences(long violations, long excessMinutes) {}
  private static final Comparator<Assignment> BY_START = Comparator.comparingInt(Assignment::start)
          .thenComparingInt(Assignment::end).thenComparingInt(Assignment::lessonIndex);

  /** Calcula as métricas completas, incluindo diagnósticos temporais que não são objetivos. */
  public static ScheduleMetrics calculateMetrics(List<Lesson> lessons,
                                                 List<Assignment> assignments, StudyConfig config) {
    Map<ResourceDay, List<Assignment>> roomDays = new HashMap<>(), classDays = new HashMap<>();
    Map<SequenceKey, List<Assignment>> sequences = new HashMap<>();
    for (Assignment a : assignments) {
      Lesson lesson = lessons.get(a.lessonIndex());
      if (a.date() == null) continue; // Não inventa uma data para aulas com data desconhecida.
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

  /** Calcula as métricas das salas usadas nos objetivos; os diagnósticos temporais ficam a zero. */
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

  /** Conta atribuições a salas sem a característica pedida, como penalização soft. */
  private static long countTypeMismatches(List<Lesson> lessons, List<Assignment> schedule) {
    return schedule.stream().filter(a -> a.room() != null)
            .filter(a -> !a.room().features().contains(lessons.get(a.lessonIndex()).requestedFeature())).count();
  }

  /** Identifica as aulas envolvidas em sobreposições na mesma sala e data. */
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

  /** Conta aulas fora das preferências conhecidas, apenas para diagnóstico. */
  private static long countOutsidePreferred(List<Lesson> lessons, List<Assignment> schedule, StudyConfig config) {
    // Conta cada aula uma vez, mesmo quando várias turmas não cumprem a preferência.
    return schedule.stream().filter(a -> preferenceKnown(lessons.get(a.lessonIndex()), a, config))
            .filter(a -> !config.preferred(lessons.get(a.lessonIndex()), a.start(), a.end())).count();
  }

  /** Verifica se existem data, turma e perfis para avaliar a preferência. */
  private static boolean preferenceKnown(Lesson lesson, Assignment assignment, StudyConfig config) {
    return assignment.date() != null && !lesson.groups().isEmpty()
            && lesson.groups().stream().allMatch(config.profilesByClass()::containsKey);
  }

  /** Soma os lugares que sobram nas salas atribuídas. */
  private static long sumExcessSeats(List<Lesson> lessons, List<Assignment> schedule) {
    return schedule.stream().filter(a -> a.room() != null)
            .mapToLong(a -> Math.max(0, a.room().capacity() - lessons.get(a.lessonIndex()).students())).sum();
  }

  /** Soma os lugares em falta nas salas atribuídas, como verificação de capacidade. */
  private static long sumSeatDeficit(List<Lesson> lessons, List<Assignment> schedule) {
    // Diagnóstico: os domínios válidos já excluem salas com capacidade insuficiente.
    return schedule.stream().filter(a -> a.room() != null)
            .mapToLong(a -> Math.max(0, lessons.get(a.lessonIndex()).students() - a.room().capacity())).sum();
  }

  /** Soma os intervalos vazios entre aulas de cada turma e dia. */
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
    return gaps; // Exclui o tempo anterior e posterior às aulas; não desconta almoço.
  }

  /** Conta pares de aulas sobrepostas por sala e os minutos de sobreposição. */
  private static Overlaps countRoomConflicts(Map<ResourceDay, List<Assignment>> roomDays) {
    long pairs = 0, minutes = 0;
    for (List<Assignment> day : roomDays.values())
      for (int i = 0; i < day.size(); i++)
        for (int j = i + 1; j < day.size() && day.get(j).start() < day.get(i).end(); j++) {
          pairs++; minutes += overlap(day.get(i), day.get(j));
        }
    return new Overlaps(pairs, minutes);
  }

  /** Conta pares distintos de aulas sobrepostas das mesmas turmas. */
  private static Overlaps countClassConflicts(Map<ResourceDay, List<Assignment>> classDays) {
    Set<Long> pairs = new HashSet<>();
    long minutes = 0;
    for (List<Assignment> day : classDays.values())
      for (int i = 0; i < day.size(); i++)
        for (int j = i + 1; j < day.size() && day.get(j).start() < day.get(i).end(); j++) {
          Assignment a = day.get(i), b = day.get(j);
          long pair = ((long) Math.min(a.lessonIndex(), b.lessonIndex()) << 32)
                  | (Math.max(a.lessonIndex(), b.lessonIndex()) & 0xffffffffL);
          // Não duplica um par de aulas por partilhar mais de uma turma.
          if (pairs.add(pair)) minutes += overlap(a, b);
        }
    return new Overlaps(pairs.size(), minutes);
  }

  /** Deteta blocos de aulas da mesma UC e turma com mais de três horas de ensino. */
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
      // Uma aula longa isolada é uma exceção provisória, não uma sequência de aulas.
      if (count >= 2 && teaching > 180) { violations++; excess += teaching - 180; }
    }
    return new Sequences(violations, excess);
  }

  /** Calcula os minutos de interseção entre dois intervalos de aula. */
  private static int overlap(Assignment a, Assignment b) {
    return Math.max(0, Math.min(a.end(), b.end()) - Math.max(a.start(), b.start()));
  }
}
