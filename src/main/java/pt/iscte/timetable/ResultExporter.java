package pt.iscte.timetable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

public final class ResultExporter {
  private ResultExporter() {}

  /** Store an immutable CSV per completed study; previous runs are never replaced. */
  public static Path publishLatest(Path output, Path project) throws IOException {
    Path schedule = output.resolve("horario-exemplo.csv");
    if (!Files.isRegularFile(schedule)) throw new IOException("No schedule produced: " + output);
    Path directory = project.resolve("Resultados-Horarios-CSV");
    Files.createDirectories(directory);
    String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS"));
    String id = "Horario-" + stamp + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    Path visible = directory.resolve(id + ".csv");
    Files.copy(schedule, visible); // No REPLACE_EXISTING: no old CSV can be overwritten.
    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    metadata.put("csv", visible.getFileName().toString());
    Path absoluteOutput = output.toAbsolutePath().normalize();
    Path absoluteProject = project.toAbsolutePath().normalize();
    metadata.put("experimentDirectory", absoluteOutput.startsWith(absoluteProject)
            ? absoluteProject.relativize(absoluteOutput).toString().replace('\\', '/') : absoluteOutput.toString());
    for (String name : List.of("configuration", "solution-summary", "provenance")) {
      Path file = output.resolve(name + ".json");
      if (Files.isRegularFile(file)) metadata.put(name, JsonFiles.MAPPER.readTree(file.toFile()));
    }
    JsonFiles.write(directory.resolve(id + ".json"), metadata);
    Files.writeString(project.resolve("RESULTADOS.txt"),
            "Último estudo concluído: " + output.toAbsolutePath() + "\n"
                    + "ABRIR: " + visible.toAbsolutePath() + "\n"
                    + "Os CSVs de execuções anteriores permanecem em Resultados-Horarios-CSV.\n"
                    + "O CSV pode conter conflitos e casos por atribuir; consultar Estado e solution-summary.json.\n"
                    + (Files.exists(output.resolve("solution-summary.json"))
                    ? Files.readString(output.resolve("solution-summary.json"), StandardCharsets.UTF_8) : "")
                    + "\nOs detalhes e as alternativas estão na pasta da execução.\n", StandardCharsets.UTF_8);
    System.out.println("ABRIR NO EXCEL: " + visible);
    return visible;
  }

  public static void exportValidation(StudyInput input) throws IOException {
    Path output = input.output();
    if (Files.exists(output)) try (var entries = Files.list(output)) {
      if (entries.findAny().isPresent()) throw new IllegalArgumentException("Output directory must be empty: " + output);
    }
    Files.createDirectories(output);
    JsonFiles.write(output.resolve("configuration.json"), input.config());
    JsonFiles.write(output.resolve("provenance.json"), Map.of(
            "configSha256", CsvDataLoader.sha256(input.configFile()),
            "policySha256", CsvDataLoader.sha256(input.policyFile()),
            "sources", input.data().sourceHashes(), "jdk", System.getProperty("java.version"),
            "jmetal", "7.9", "datasetLabel", input.config().synthetic() ? "SYNTHETIC" : "REAL_SOURCE_DATA"));
    Files.copy(input.policyFile(), output.resolve("compatibility-policy.json"));
    Path schedule = input.configFile().getParent().resolve(input.config().scheduleFile()).normalize();
    String filename = schedule.getFileName().toString();
    Path manifest = schedule.resolveSibling(filename.replaceFirst("\\.csv$", ".manifest.json"));
    if (Files.isRegularFile(manifest))
      Files.copy(manifest, output.resolve("selection-manifest.json"));
    var report = input.validation().report();
    JsonFiles.write(output.resolve("validation.json"), report);
    StringBuilder text = new StringBuilder("# Validação anterior à otimização\n\nCenário: " + report.scenario()
            + "\n\nDados sintéticos: " + report.synthetic() + "\n\nIntegralmente avaliável: " + report.fullyEvaluable()
            + "\n\n| Domínio de sala | Ocorrências |\n|---|---:|\n");
    report.roomCoverage().forEach((key, count) -> text.append("| ").append(key).append(" | ").append(count).append(" |\n"));
    text.append("\nBloqueios de configuração/entrada: ").append(report.globalBlockers())
            .append("\n\nTurmas sem perfil: ").append(report.missingProfiles().size())
            .append("\n\nDomínios individuais não vazios não demonstram viabilidade conjunta.\n");
    text.append("\nModelo vigente: 1 variável (sala) por aula; 2 objetivos e 1 restrição. Objetivo 0 e restrição: choques de sala + aulas sem sala. Objetivo 1: tipo de sala errado + lugares vazios.\n");
    text.append("\nExecução diagnóstica de dados incompletos autorizada na configuração: ")
            .append(input.config().allowIncompleteData()).append(". Pode executar: ")
            .append(input.validation().runnable(input.config())).append(".\n")
            .append("Sem sala elegível: mantém sala em branco e penaliza a falta de atribuição. ")
            .append("Sem data: mantém data/hora em branco e penaliza informação incompleta. ")
            .append("Preferências não fornecidas não são inferidas nem avaliadas; ver os pesos em configuration.json.\n");
    Files.writeString(output.resolve("validation.md"), text, StandardCharsets.UTF_8);
  }

  /** Decode the files produced by ExecuteAlgorithms; no optimizer or indicators are implemented here. */
  public static void exportNativeSchedules(Path output,
                                           org.uma.jmetal.lab.experiment.Experiment<IntegerSolution, List<IntegerSolution>> experiment,
                                           RoomPolicy policy) throws IOException {
    List<Object> instances = new ArrayList<>();
    int largest = experiment.getProblemList().stream()
            .mapToInt(p -> ((TimetableProblem) p.getProblem()).data().lessons().size()).max().orElseThrow();
    TimetableProblem selectedProblem = null;
    List<Assignment> selectedSchedule = null;
    ScheduleMetrics selectedMetrics = null;
    double bestHard = Double.POSITIVE_INFINITY, bestSoft = Double.POSITIVE_INFINITY;
    for (var instance : experiment.getProblemList()) {
      TimetableProblem problem = (TimetableProblem) instance.getProblem();
      instances.add(Map.of("problem", instance.getTag(), "occurrences", problem.data().lessons().size(),
              "variables", problem.numberOfVariables(), "selection", "First N occurrences in configured CSV order",
              "sourceRows", problem.data().lessons().stream().map(Lesson::sourceRow).toList()));
      for (var algorithm : experiment.getAlgorithmList()) {
        for (int run = 0; run < experiment.getIndependentRuns(); run++) {
          Path nativeDirectory = Path.of(experiment.getExperimentBaseDirectory(), "data",
                  algorithm.getAlgorithmTag(), instance.getTag());
          double[][] objectives = org.uma.jmetal.util.VectorUtils.readVectors(nativeDirectory.resolve("FUN" + run + ".csv").toString(), ",");
          double[][] variables = org.uma.jmetal.util.VectorUtils.readVectors(nativeDirectory.resolve("VAR" + run + ".csv").toString(), ",");
          if (objectives.length != variables.length) throw new IOException("FUN/VAR row mismatch");
          Path directory = output.resolve("horarios").resolve(instance.getTag())
                  .resolve(algorithm.getAlgorithmTag()).resolve("run-" + run);
          Files.createDirectories(directory);
          try (var metrics = printer(directory.resolve("metrics.csv"), "solution", "hard_violations", "soft_penalty",
                  "room_conflicts", "unassigned_rooms", "type_mismatches", "excess_seats",
                  "class_conflicts_fixed", "sequence_violations_fixed", "hard_zero")) {
            for (int index = 0; index < variables.length; index++) {
              var solution = new org.uma.jmetal.solution.integersolution.impl.DefaultIntegerSolution(
                      problem.variableBounds(), 2, 0);
              if (variables[index].length != problem.numberOfVariables()) throw new IOException("Wrong VAR dimensions");
              for (int gene = 0; gene < variables[index].length; gene++) {
                double value = variables[index][gene];
                if (value != Math.rint(value)) throw new IOException("Non-integer VAR value");
                solution.variables().set(gene, (int) value);
              }
              var assignments = problem.decode(solution);
              validateLocal(problem, policy, assignments);
              var m = TimetableProblem.calculateMetrics(problem.data().lessons(), assignments, problem.config());
              if (objectives[index].length != 2 || objectives[index][0] != m.hardViolations()
                      || Math.abs(objectives[index][1] - problem.softPenalty(m)) > 1e-10)
                throw new IOException("Exported objectives differ from recomputed objectives");
              metrics.printRecord(index, m.hardViolations(), problem.softPenalty(m), m.roomConflictPairs(),
                      m.unassignedRooms(), m.typeMismatches(), m.excessSeats(),
                      m.classConflictPairs(), m.sequenceViolations(), m.feasible());
              // Complete data studies retain all native FUN/VAR points; export one representative CSV.
              if (!problem.config().allowIncompleteData())
                writeSchedule(directory.resolve("schedule-%03d.csv".formatted(index)), problem, policy, assignments, m);
              double soft = problem.softPenalty(m);
              if (problem.data().lessons().size() == largest
                      && (m.hardViolations() < bestHard || m.hardViolations() == bestHard && soft < bestSoft)) {
                bestHard = m.hardViolations(); bestSoft = soft;
                selectedProblem = problem; selectedSchedule = assignments; selectedMetrics = m;
              }
            }
          }
        }
      }
    }
    JsonFiles.write(output.resolve("problem-instances.json"), instances);
    if (selectedSchedule != null) {
      writeSchedule(output.resolve("horario-exemplo.csv"), selectedProblem, policy, selectedSchedule, selectedMetrics);
      long withRoom = selectedSchedule.stream().filter(a -> a.room() != null).count();
      Map<String, Object> summary = new java.util.LinkedHashMap<>();
      summary.put("occurrences", selectedSchedule.size());
      summary.put("hardViolations", bestHard);
      summary.put("softPenalty", bestSoft);
      summary.put("roomConflictPairs", selectedMetrics.roomConflictPairs());
      summary.put("unassignedRooms", selectedMetrics.unassignedRooms());
      summary.put("lessonsWithRoom", withRoom);
      summary.put("typeMismatches", selectedMetrics.typeMismatches());
      summary.put("excessSeats", selectedMetrics.excessSeats());
      summary.put("classConflictPairsFixedByTimes", selectedMetrics.classConflictPairs());
      summary.put("sequenceViolationsFixedByTimes", selectedMetrics.sequenceViolations());
      summary.put("selection", "Minimum objective 0, then objective 1, among native results of the largest instance");
      JsonFiles.write(output.resolve("solution-summary.json"), summary);
      System.out.println();
      System.out.println("===== RESUMO DO MELHOR HORÁRIO =====");
      System.out.println("Aulas: " + selectedSchedule.size() + " | com sala: " + withRoom);
      System.out.println("[HARD] Pares de aulas na mesma sala à mesma hora: " + selectedMetrics.roomConflictPairs());
      System.out.println("[HARD] Aulas que precisam de sala e ficaram sem sala: " + selectedMetrics.unassignedRooms());
      System.out.println("[SOFT] Aulas em sala de tipo diferente do pedido: " + selectedMetrics.typeMismatches()
              + " (" + Math.round(100.0 * selectedMetrics.typeMismatches() / Math.max(1, withRoom)) + "%)");
      System.out.println("[SOFT] Lugares vazios (soma): " + selectedMetrics.excessSeats()
              + " | média por aula: " + Math.round((double) selectedMetrics.excessSeats() / Math.max(1, withRoom)));
      System.out.println("[INFO] Choques de turma (fixos, dependem das horas): " + selectedMetrics.classConflictPairs());
      System.out.println("[INFO] Blocos > 3h da mesma UC (fixos, dependem das horas): " + selectedMetrics.sequenceViolations());
      System.out.println("Objetivo 0 = " + (long) bestHard + " | Objetivo 1 = " + bestSoft);
    }
  }

  /** Checks coverage and local rules from the decoded assignments, not cached fitness. */
  public static void validateLocal(TimetableProblem problem, RoomPolicy policy, List<Assignment> assignments) {
    var config = problem.config();
    if (assignments.size() != problem.data().lessons().size()) throw new IllegalStateException("Incomplete schedule");
    Set<Integer> seen = new HashSet<>();
    for (Assignment a : assignments) {
      if (a.lessonIndex() < 0 || a.lessonIndex() >= problem.data().lessons().size() || !seen.add(a.lessonIndex()))
        throw new IllegalStateException("Missing or duplicated occurrence");
      Lesson lesson = problem.data().lessons().get(a.lessonIndex());
      // O dia e a hora têm de ser exatamente os do CSV (o algoritmo só escolhe salas).
      if (lesson.originalDate() == null) {
        if (!config.allowIncompleteData() || a.date() != null || a.start() != -1 || a.end() != -1)
          throw new IllegalStateException("Missing date must remain explicitly unassigned");
      } else if (!lesson.originalDate().equals(a.date()) || a.start() != lesson.start() || a.end() != lesson.end())
        throw new IllegalStateException("Original date/time changed: " + lesson.id());
      if (lesson.needsRoom()) {
        if (a.room() == null && config.allowIncompleteData() && !problem.hasRoomCandidates(a.lessonIndex())) continue;
        Room canonical = problem.data().rooms().stream().filter(r -> a.room() != null && r.id().equals(a.room().id())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown physical room"));
        // Hard: sala de aula com lugares suficientes. O tipo de sala é soft, por isso não é verificado aqui.
        if (!canonical.equals(a.room()) || canonical.capacity() < lesson.students() || !Preflight.isClassroom(canonical))
          throw new IllegalStateException("Invalid room assignment");
      } else if (a.room() != null) throw new IllegalStateException("No-room activity consumed a physical room");
    }
  }
  private static void writeSchedule(Path file, TimetableProblem problem, RoomPolicy policy,
                                    List<Assignment> assignments, ScheduleMetrics metrics) throws IOException {
    Set<Integer> conflicts = TimetableProblem.roomConflictLessons(assignments);
    try (var csv = printer(file, "source_id", "source_csv_row", "Curso", "Unidade de execução", "Turno", "Turma",
            "Inscritos", "Data_original", "Sala_original", "Data", "Inicio", "Fim", "Sala",
            "Capacidade_Normal", "synthetic", "hard_violations", "soft_penalty",
            "Pedido_sala_original", "Duracao_original_min", "Estado",
            "Tipo_sala_certo", "Lugares_vazios", "Choque_sala")) {
      for (Assignment a : assignments.stream().sorted(java.util.Comparator.comparing(Assignment::date,
                      java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
              .thenComparingInt(Assignment::start).thenComparingInt(Assignment::lessonIndex)).toList()) {
        Lesson lesson = problem.data().lessons().get(a.lessonIndex());
        List<String> issues = new ArrayList<>();
        if (a.date() == null) issues.add("MISSING_DATE");
        if (lesson.groups().isEmpty()) issues.add("MISSING_CLASS");
        if (lesson.needsRoom() && a.room() == null) issues.add("INSUFFICIENT_CAPACITY");
        if (!lesson.needsRoom()) issues.add(lesson.students() == 0 && !lesson.requestedFeature().equals(Lesson.NO_ROOM)
                ? "ZERO_INSCRITOS_SEM_SALA" : "SEM_SALA");
        csv.printRecord(lesson.id(), lesson.sourceRow(), lesson.courses(), lesson.unit(), lesson.shift(),
                String.join(",", lesson.groups()), lesson.students(), lesson.originalDate(), lesson.originalRoom(),
                a.date(), a.date() == null ? "" : clock(a.start()), a.date() == null ? "" : clock(a.end()),
                a.room() == null ? "" : a.room().id(), a.room() == null ? "" : a.room().capacity(),
                problem.config().synthetic(), metrics.hardViolations(), problem.softPenalty(metrics),
                lesson.requestedFeature(), lesson.duration(), issues.isEmpty() ? "SALA_ATRIBUIDA" : String.join("|", issues),
                a.room() == null ? "" : a.room().features().contains(lesson.requestedFeature()) ? "SIM" : "NAO",
                a.room() == null ? "" : Math.max(0, a.room().capacity() - lesson.students()),
                conflicts.contains(a.lessonIndex()) ? "SIM" : "NAO");
      }
    }
  }

  public static CSVPrinter printer(Path path, String... headers) throws IOException {
    return new CSVPrinter(Files.newBufferedWriter(path, StandardCharsets.UTF_8),
            CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(headers).build());
  }
  public static String clock(int minute) { return "%02d:%02d:00".formatted(minute / 60, minute % 60); }
}
