package pt.iscte.timetable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unknown compatibility and proven empty domains are distinct diagnostic states. */
public final class Preflight {
  public record Row(String id, int sourceRow, String request, String roomStatus,
                    int featureCandidates, int capacityCandidates, Integer maximumCapacity,
                    List<String> blockers, List<String> warnings) {}
  public record Report(String scenario, boolean synthetic, int lessons, int catalogRooms,
                       boolean fullyEvaluable, boolean jointlyFeasibleProven,
                       Map<String, Long> roomCoverage, List<String> globalBlockers,
                       Set<String> missingProfiles, List<Row> occurrences,
                       Map<String, String> sourceHashes) {}
  public record Result(Report report, List<LessonDomain> domains) {
    public boolean runnable(StudyConfig config) {
      if (report.fullyEvaluable()) return true;
      if (!config.allowIncompleteData()) return false;
      Set<String> permittedGlobal = Set.of("UNAVAILABLE_DATES_NOT_REVIEWED", "CLASS_IDENTIFIERS_NOT_REVIEWED",
              "MULTIPLE_PROFILE_INTERPRETATION_NOT_CONFIRMED", "PREFERRED_PROFILES_MISSING");
      Set<String> permittedRows = Set.of("MISSING_DATE", "MISSING_CLASS", "MISSING_UNIT",
              "UNRESOLVED_REQUEST", "NO_FEATURE_ROOM", "INSUFFICIENT_CAPACITY");
      return report.globalBlockers().stream().allMatch(permittedGlobal::contains)
              // As preferências de horário (perfis por turma) já não são usadas: não bloqueiam a execução.
              && report.occurrences().stream().flatMap(row -> row.blockers().stream()).allMatch(permittedRows::contains);
    }
    public void requireRunnable(StudyConfig config) {
      if (!runnable(config)) throw new IllegalStateException("Input cannot run under the configured policy; read validation.json");
    }
    public void requireReady() {
      if (!report.fullyEvaluable()) throw new IllegalStateException("Input is not fully evaluable; read validation.json");
    }
  }
  private Preflight() {}

  /** Características que identificam uma sala de aula. Espaços sem nenhuma delas (átrios, balneários,
   *  salas de reunião, Grande Auditório...) nunca são escolhidos. */
  public static final Set<String> CLASSROOM_FEATURES = Set.of("sala_aulas_normal", "anfiteatro", "sala_mestrado",
          "sala_mestrado_plus", "byod", "sala_arquitectura", "videoconferencia", "lab_ista",
          "arq_1", "arq_2", "arq_3", "arq_4", "arq_5", "arq_6", "arq_9",
          "lab_arq_computadores_1", "lab_arq_computadores_2", "lab_bases_engenharia", "lab_electronica",
          "lab_informatica", "lab_jornalismo", "lab_redes_1", "lab_redes_2", "lab_telecomunicacoes");
  public static boolean isClassroom(Room room) {
    return room.features().stream().anyMatch(CLASSROOM_FEATURES::contains);
  }
  public static Result inspect(DataSet data, RoomPolicy policy, StudyConfig config) {
    config.validate();
    List<String> global = new ArrayList<>(data.inputErrors());
    if (data.lessons().isEmpty()) global.add("EMPTY_LESSON_SET");
    if (config.horizonStart() == null) global.add("PLANNING_HORIZON_NOT_CONFIGURED");
    if (!config.unavailableDatesReviewed()) global.add("UNAVAILABLE_DATES_NOT_REVIEWED");
    if (!config.classIdentifiersReviewed()) global.add("CLASS_IDENTIFIERS_NOT_REVIEWED");
    if (config.multipleProfilesMode() == null) global.add("MULTIPLE_PROFILE_INTERPRETATION_NOT_CONFIRMED");
    Set<String> missingProfiles = new LinkedHashSet<>();
    for (Lesson lesson : data.lessons()) for (String group : lesson.groups())
      if (!config.profilesByClass().containsKey(group)) missingProfiles.add(group);
    if (!missingProfiles.isEmpty()) global.add("PREFERRED_PROFILES_MISSING");
    Map<String, Long> coverage = new LinkedHashMap<>();
    for (String state : List.of("AVAILABLE", "NO_PHYSICAL_ROOM", "UNRESOLVED_REQUEST",
            "NO_FEATURE_ROOM", "INSUFFICIENT_CAPACITY", "INVALID_STUDENTS")) coverage.put(state, 0L);
    List<Row> rows = new ArrayList<>();
    List<LessonDomain> domains = new ArrayList<>();
    for (Lesson lesson : data.lessons()) {
      List<String> blockers = new ArrayList<>(), warnings = new ArrayList<>();
      if (lesson.originalDate() == null) blockers.add("MISSING_DATE");
      if (lesson.groups().isEmpty()) blockers.add("MISSING_CLASS");
      if (lesson.unit().isBlank()) blockers.add("MISSING_UNIT");
      if (lesson.duration() <= 0 || lesson.duration() % config.gridMinutes() != 0)
        blockers.add("INVALID_OR_OFF_GRID_DURATION");
      if (lesson.duration() > config.closingMinute() - config.openingMinute()) blockers.add("DURATION_EXCEEDS_DAY");
      if (lesson.students() < 0) blockers.add("INVALID_STUDENTS");
      if (lesson.students() == 0) warnings.add("ZERO_STUDENTS_ASSUMED_EFFECTIVE_DEMAND");
      if (lesson.duration() > 180) warnings.add("PROVISIONAL_LONG_INDIVIDUAL_EXCEPTION");
      List<Room> feature = List.of(), eligible = List.of();
      String status;
      if (!lesson.needsRoom()) status = "NO_PHYSICAL_ROOM";
      else if (!policy.resolved(lesson.requestedFeature(), data.featureColumns())) status = "UNRESOLVED_REQUEST";
      else if (lesson.students() < 0) status = "INVALID_STUDENTS";
      else {
        // Salas do tipo pedido: só para informação, porque o tipo de sala é soft.
        feature = policy.byFeature(lesson.requestedFeature(), data.rooms());
        // Salas possíveis (hard): qualquer sala de aula com lugares suficientes.
        // Ordenadas: primeiro as do tipo pedido, depois da mais pequena para a maior. Assim, números de sala
        // próximos correspondem a salas parecidas, e o cruzamento/mutação do jMetal fazem mais sentido.
        eligible = data.rooms().stream().filter(Preflight::isClassroom)
                .filter(r -> r.capacity() >= lesson.students())
                .sorted(java.util.Comparator.comparing((Room r) -> !r.features().contains(lesson.requestedFeature()))
                        .thenComparingInt(Room::capacity).thenComparing(Room::id))
                .toList();
        status = eligible.isEmpty() ? "INSUFFICIENT_CAPACITY" : "AVAILABLE";
        if (feature.stream().noneMatch(r -> r.capacity() >= lesson.students()))
          warnings.add("NO_ROOM_OF_REQUESTED_TYPE_WITH_CAPACITY");
      }
      coverage.merge(status, 1L, Long::sum);
      if (!status.equals("AVAILABLE") && !status.equals("NO_PHYSICAL_ROOM")) blockers.add(status);
      List<LocalDate> dates = new ArrayList<>();
      if (lesson.originalDate() != null && config.horizonStart() != null) {
        LocalDate monday = lesson.originalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int d = 0; d < 7; d++) {
          LocalDate date = monday.plusDays(d);
          if (!date.isBefore(config.horizonStart()) && !date.isAfter(config.horizonEnd())
                  && config.allowedDays().contains(date.getDayOfWeek()) && !config.closedDates().contains(date)) dates.add(date);
        }
        if (dates.isEmpty()) blockers.add("NO_ALLOWED_DATE_IN_ORIGINAL_WEEK");
      }
      int lastStart = (config.closingMinute() - config.openingMinute() - lesson.duration()) / config.gridMinutes();
      domains.add(new LessonDomain(dates, lastStart, eligible));
      Integer maximum = feature.stream().mapToInt(Room::capacity).max().stream().boxed().findFirst().orElse(null);
      rows.add(new Row(lesson.id(), lesson.sourceRow(), lesson.requestedFeature(), status,
              feature.size(), eligible.size(), maximum, List.copyOf(blockers), List.copyOf(warnings)));
    }
    boolean ready = global.isEmpty() && rows.stream().allMatch(r -> r.blockers().isEmpty());
    return new Result(new Report(config.scenario(), config.synthetic(), data.lessons().size(), data.rooms().size(),
            ready, false, coverage, List.copyOf(global), Set.copyOf(missingProfiles), List.copyOf(rows), data.sourceHashes()),
            List.copyOf(domains));
  }
}
