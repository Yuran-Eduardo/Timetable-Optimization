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
import java.util.Comparator;
import java.util.function.ToDoubleFunction;
import org.uma.jmetal.util.archive.impl.NonDominatedSolutionListArchive;
import org.uma.jmetal.util.densityestimator.impl.CrowdingDistanceDensityEstimator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

/** Valida e exporta os horários obtidos pelo jMetal e os respetivos metadados. */
public final class ResultExporter {
  private static final int MAX_HIGHLIGHTS = 6;
  private static final String CANDIDATE = "timetable-export-candidate";

  /** Guarda a origem e as métricas, sem manter uma cópia descodificada de cada horário. */
  private record Candidate(TimetableProblem problem, ScheduleMetrics metrics, String algorithm,
                           int run, int nativeSolution) {}
  /** Impede a criação de instâncias desta classe utilitária. */
  private ResultExporter() {}

  /** Publica toda a frente, distinguindo até seis destaques sem ocultar as restantes alternativas. */
  @SuppressWarnings("unchecked")
  public static Path publishLatest(Path output, Path project) throws IOException {
    Path directory = project.resolve("Resultados-Horarios-CSV");
    Files.createDirectories(directory);
    String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS"))
            + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    Path absoluteOutput = output.toAbsolutePath().normalize();
    Path absoluteProject = project.toAbsolutePath().normalize();
    Map<String, Object> baseMetadata = new java.util.LinkedHashMap<>();
    baseMetadata.put("experimentDirectory", absoluteOutput.startsWith(absoluteProject)
            ? absoluteProject.relativize(absoluteOutput).toString().replace('\\', '/') : absoluteOutput.toString());
    for (String name : List.of("configuration", "solution-summary", "provenance")) {
      Path file = output.resolve(name + ".json");
      if (Files.isRegularFile(file)) baseMetadata.put(name, JsonFiles.MAPPER.readTree(file.toFile()));
    }
    List<Map<String, Object>> options = new ArrayList<>();
    Path manifest = output.resolve("alternatives.json");
    if (Files.isRegularFile(manifest)) {
      var root = JsonFiles.MAPPER.readTree(manifest.toFile());
      var entries = root.path("options");
      if (!entries.isArray() || entries.isEmpty())
        throw new IOException("Lista de alternativas inválida");
      for (var node : entries) options.add(JsonFiles.MAPPER.convertValue(node, Map.class));
      baseMetadata.put("paretoPoints", root.path("paretoPoints").asInt());
      if (root.has("problems")) baseMetadata.put("problems", root.path("problems"));
    } else {
      // Continua a aceitar a publicação de um resultado representativo criado por versões anteriores.
      options.add(new java.util.LinkedHashMap<>(Map.of("option", 1, "recommended", true,
              "highlighted", true, "file", "horario-exemplo.csv")));
    }
    List<Map<String, Object>> published = new ArrayList<>();
    Path first = null;
    for (int index = 0; index < options.size(); index++) {
      var option = options.get(index);
      option.putIfAbsent("highlighted", true); // Compatibilidade com os manifestos anteriores, limitados a seis opções.
      Path source = absoluteOutput.resolve((String) option.get("file")).normalize();
      if (!source.startsWith(absoluteOutput) || !Files.isRegularFile(source))
        throw new IOException("CSV da alternativa ausente ou fora da execução: " + source);
      String problem = (String) option.getOrDefault("problem", "");
      Path problemDirectory = problem.isEmpty() ? directory
              : directory.resolve(problem.replaceAll("[^A-Za-z0-9._-]", "_"));
      Files.createDirectories(problemDirectory);
      int optionNumber = ((Number) option.get("option")).intValue();
      String id = "Horario-" + stamp + "-opcao-%02d".formatted(optionNumber);
      Path visible = problemDirectory.resolve(id + ".csv");
      String relativeCsv = directory.relativize(visible).toString().replace('\\', '/');
      Files.copy(source, visible); // Preserva todos os CSV publicados anteriormente.
      Map<String, Object> metadata = new java.util.LinkedHashMap<>(baseMetadata);
      metadata.put("csv", relativeCsv);
      metadata.put("option", optionNumber);
      metadata.put("problem", problem);
      metadata.put("recommended", option.get("recommended"));
      metadata.put("highlighted", option.get("highlighted"));
      if (Files.isRegularFile(manifest)) metadata.put("solution-summary", option);
      JsonFiles.write(problemDirectory.resolve(id + ".json"), metadata);
      var comparisonRow = new java.util.LinkedHashMap<>(option);
      comparisonRow.put("csv", relativeCsv);
      published.add(comparisonRow);
      if (first == null) first = visible;
      if (Boolean.TRUE.equals(option.get("highlighted")))
        System.out.println("DESTAQUE " + problem + " — OPÇÃO " + optionNumber
                + (Boolean.TRUE.equals(option.get("recommended")) ? " (recomendada)" : "") + ": " + visible);
    }
    Path comparison = directory.resolve("Comparacao-" + stamp + ".csv");
    writeComparison(comparison, published, "csv");
    var highlighted = published.stream().filter(row -> Boolean.TRUE.equals(row.get("highlighted"))).toList();
    Path highlights = directory.resolve("Destaques-" + stamp + ".csv");
    writeComparison(highlights, highlighted, "csv");
    Map<String, Object> publication = new java.util.LinkedHashMap<>(baseMetadata);
    publication.put("exportedAlternatives", published.size());
    publication.put("highlightedAlternatives", highlighted.size());
    publication.put("highlightLimitPerProblem", MAX_HIGHLIGHTS);
    publication.put("options", published);
    JsonFiles.write(directory.resolve("Comparacao-" + stamp + ".json"), publication);
    StringBuilder pointer = new StringBuilder("Último estudo concluído: " + absoluteOutput + "\n")
            .append("TODAS AS ").append(published.size()).append(" OPÇÕES: ").append(comparison.toAbsolutePath()).append("\n")
            .append("DESTAQUES: ").append(highlights.toAbsolutePath()).append("\n");
    for (var row : published) if (Boolean.TRUE.equals(row.get("recommended")))
      pointer.append("RECOMENDADO ").append(row.getOrDefault("problem", "")).append(": ")
              .append(directory.resolve((String) row.get("csv")).toAbsolutePath()).append("\n");
    for (var row : highlighted) pointer.append(row.getOrDefault("problem", "")).append(" — Opção ").append(row.get("option")).append(": ")
            .append(directory.resolve((String) row.get("csv")).toAbsolutePath()).append("\n");
    pointer.append("Os horários anteriores permanecem guardados. Cada CSV deve ser analisado quanto a violações hard.\n");
    Files.writeString(project.resolve("RESULTADOS.txt"), pointer, StandardCharsets.UTF_8);
    System.out.println("COMPARAR NO EXCEL: " + comparison);
    System.out.println("DESTAQUES: " + highlights + " | Todas as " + published.size() + " opções permanecem disponíveis.");
    return first;
  }

  /** Guarda a configuração, a proveniência e o diagnóstico anteriores à otimização. */
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
            "jmetal", "7.9", "objectiveCounts", List.of(2, 4),
            "datasetLabel", input.config().synthetic() ? "SYNTHETIC" : "REAL_SOURCE_DATA"));
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
    text.append("\nModelo vigente: 1 variável (sala) por aula. Cada tamanho é avaliado com dois problemas, de 2 e 4 objetivos, ambos com uma restrição hard. "
            + "2obj: total hard e penalização soft ponderada. 4obj: choques de sala, aulas sem sala, tipo errado e lugares vazios.\n");
    text.append("\nExecução diagnóstica de dados incompletos autorizada na configuração: ")
            .append(input.config().allowIncompleteData()).append(". Pode executar: ")
            .append(input.validation().runnable(input.config())).append(".\n")
            .append("Sem sala elegível: mantém sala em branco e penaliza a falta de atribuição. ")
            .append("Sem data: mantém data/hora em branco e identifica a informação incompleta, sem criar um objetivo adicional. ")
            .append("Os perfis de horário não são objetivos. Os pesos type e seats definem o objetivo soft em 2obj e selecionam o recomendado em ambas as formulações.\n");
    Files.writeString(output.resolve("validation.md"), text, StandardCharsets.UTF_8);
  }

  /** Descodifica FUN/VAR e exporta as frentes e recomendações, isoladas por problema. */
  public static void exportNativeSchedules(Path output,
                                           org.uma.jmetal.lab.experiment.Experiment<IntegerSolution, List<IntegerSolution>> experiment,
                                           RoomPolicy policy) throws IOException {
    if (experiment.getProblemList().size() > 1) {
      exportSeparateProblems(output, experiment, policy);
      return;
    }
    List<Object> instances = new ArrayList<>();
    var front = new NonDominatedSolutionListArchive<IntegerSolution>();
    for (var instance : experiment.getProblemList()) {
      TimetableProblem problem = (TimetableProblem) instance.getProblem();
      instances.add(Map.of("problem", instance.getTag(), "occurrences", problem.data().lessons().size(),
              "variables", problem.numberOfVariables(), "objectiveCount", problem.numberOfObjectives(),
              "selection", "First N occurrences in configured CSV order",
              "sourceRows", problem.data().lessons().stream().map(Lesson::sourceRow).toList()));
      for (String algorithm : experiment.getAlgorithmList().stream()
              .map(a -> a.getAlgorithmTag()).distinct().toList()) {
        for (int run = 0; run < experiment.getIndependentRuns(); run++) {
          Path nativeDirectory = Path.of(experiment.getExperimentBaseDirectory(), "data",
                  algorithm, instance.getTag());
          double[][] objectives = org.uma.jmetal.util.VectorUtils.readVectors(nativeDirectory.resolve("FUN" + run + ".csv").toString(), ",");
          double[][] variables = org.uma.jmetal.util.VectorUtils.readVectors(nativeDirectory.resolve("VAR" + run + ".csv").toString(), ",");
          if (objectives.length != variables.length) throw new IOException("FUN/VAR row mismatch");
          Path directory = output.resolve("horarios").resolve(instance.getTag())
                  .resolve(algorithm).resolve("run-" + run);
          Files.createDirectories(directory);
          try (var metrics = printer(directory.resolve("metrics.csv"), "solution", "hard_violations", "soft_penalty",
                  "room_conflicts", "unassigned_rooms", "type_mismatches", "excess_seats",
                  "class_conflicts_fixed", "sequence_violations_fixed", "hard_zero")) {
            for (int index = 0; index < variables.length; index++) {
              var solution = new org.uma.jmetal.solution.integersolution.impl.DefaultIntegerSolution(
                      problem.variableBounds(), problem.numberOfObjectives(), 1);
              if (variables[index].length != problem.numberOfVariables()) throw new IOException("Wrong VAR dimensions");
              for (int gene = 0; gene < variables[index].length; gene++) {
                double value = variables[index][gene];
                if (value != Math.rint(value)) throw new IOException("Non-integer VAR value");
                solution.variables().set(gene, (int) value);
              }
              var assignments = problem.decode(solution);
              validateLocal(problem, policy, assignments);
              var m = TimetableProblem.calculateMetrics(problem.data().lessons(), assignments, problem.config());
              // Confirma que os objetivos em FUN coincidem com os recalculados a partir de VAR.
              if (!java.util.Arrays.equals(objectives[index], problem.objectiveValues(m)))
                throw new IOException("Exported objectives differ from recomputed objectives");
              metrics.printRecord(index, m.hardViolations(), problem.softPenalty(m), m.roomConflictPairs(),
                      m.unassignedRooms(), m.typeMismatches(), m.excessSeats(),
                      m.classConflictPairs(), m.sequenceViolations(), m.feasible());
              // Preserva os resultados nativos, independentemente do limite de CSV publicados.
              if (!problem.config().allowIncompleteData())
                writeSchedule(directory.resolve("schedule-%03d.csv".formatted(index)), problem, policy, assignments, m);
              System.arraycopy(objectives[index], 0, solution.objectives(), 0, problem.numberOfObjectives());
              solution.constraints()[0] = -m.hardViolations();
              solution.attributes().put(CANDIDATE, new Candidate(problem, m, algorithm, run, index));
              front.add(solution);
            }
          }
        }
      }
    }
    JsonFiles.write(output.resolve("problem-instances.json"), instances);
    ToDoubleFunction<IntegerSolution> softScore = solution -> {
      Candidate candidate = (Candidate) solution.attributes().get(CANDIDATE);
      return candidate.problem().softPenalty(candidate.metrics());
    };
    var highlights = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<IntegerSolution, Boolean>());
    highlights.addAll(selectAlternatives(front.solutions(), MAX_HIGHLIGHTS, softScore));
    var alternatives = new ArrayList<>(front.solutions());
    alternatives.sort(Comparator.comparingDouble((IntegerSolution s) -> -s.constraints()[0])
            .thenComparingDouble(softScore));
    List<Map<String, Object>> exported = new ArrayList<>();
    if (!alternatives.isEmpty()) {
      Files.createDirectories(output.resolve("alternativas"));
      for (int index = 0; index < alternatives.size(); index++) {
        var solution = alternatives.get(index);
        Candidate candidate = (Candidate) solution.attributes().get(CANDIDATE);
        String file = "alternativas/horario-%02d.csv".formatted(index + 1);
        writeSchedule(output.resolve(file), candidate.problem(), policy,
                candidate.problem().decode(solution), candidate.metrics());
        Map<String, Object> option = new java.util.LinkedHashMap<>();
        option.put("option", index + 1);
        option.put("recommended", index == 0);
        option.put("highlighted", highlights.contains(solution));
        option.put("file", file);
        option.put("problem", candidate.problem().name());
        option.put("objectiveCount", candidate.problem().numberOfObjectives());
        option.put("algorithm", candidate.algorithm());
        option.put("run", candidate.run());
        option.put("nativeSolution", candidate.nativeSolution());
        option.put("occurrences", candidate.problem().data().lessons().size());
        option.put("objectives", solution.objectives());
        option.put("hardViolations", candidate.metrics().hardViolations());
        option.put("roomConflictPairs", candidate.metrics().roomConflictPairs());
        option.put("unassignedRooms", candidate.metrics().unassignedRooms());
        option.put("typeMismatches", candidate.metrics().typeMismatches());
        option.put("excessSeats", candidate.metrics().excessSeats());
        option.put("softPenalty", softScore.applyAsDouble(solution));
        exported.add(option);
      }
      JsonFiles.write(output.resolve("alternatives.json"), Map.of("paretoPoints", front.size(),
              "exportedAlternatives", exported.size(), "highlightLimit", MAX_HIGHLIGHTS,
              "highlightedAlternatives", highlights.size(), "options", exported));
      writeComparison(output.resolve("alternatives-summary.csv"), exported, "file");
      writeComparison(output.resolve("alternatives-highlights.csv"),
              exported.stream().filter(row -> Boolean.TRUE.equals(row.get("highlighted"))).toList(), "file");
      Candidate recommended = (Candidate) alternatives.getFirst().attributes().get(CANDIDATE);
      TimetableProblem selectedProblem = recommended.problem();
      List<Assignment> selectedSchedule = selectedProblem.decode(alternatives.getFirst());
      ScheduleMetrics selectedMetrics = recommended.metrics();
      double bestHard = selectedMetrics.hardViolations(), bestSoft = selectedProblem.softPenalty(selectedMetrics);
      // Mantém o ficheiro representativo para compatibilidade com os consumidores existentes.
      Files.copy(output.resolve((String) exported.getFirst().get("file")), output.resolve("horario-exemplo.csv"));
      long withRoom = selectedSchedule.stream().filter(a -> a.room() != null).count();
      Map<String, Object> summary = new java.util.LinkedHashMap<>();
      summary.put("occurrences", selectedSchedule.size());
      summary.put("problem", selectedProblem.name());
      summary.put("objectiveCount", selectedProblem.numberOfObjectives());
      summary.put("hardViolations", bestHard);
      summary.put("softPenalty", bestSoft);
      summary.put("roomConflictPairs", selectedMetrics.roomConflictPairs());
      summary.put("unassignedRooms", selectedMetrics.unassignedRooms());
      summary.put("lessonsWithRoom", withRoom);
      summary.put("typeMismatches", selectedMetrics.typeMismatches());
      summary.put("excessSeats", selectedMetrics.excessSeats());
      summary.put("classConflictPairsFixedByTimes", selectedMetrics.classConflictPairs());
      summary.put("sequenceViolationsFixedByTimes", selectedMetrics.sequenceViolations());
      summary.put("selection", "Menor total de violações hard, seguido da menor pontuação soft ponderada, entre os resultados nativos deste problema");
      summary.put("paretoPoints", front.size());
      summary.put("exportedAlternatives", exported.size());
      summary.put("highlightedAlternatives", highlights.size());
      JsonFiles.write(output.resolve("solution-summary.json"), summary);
      System.out.println();
      System.out.println("===== HORÁRIO RECOMENDADO: " + selectedProblem.name() + " =====");
      System.out.println("Aulas: " + selectedSchedule.size() + " | com sala: " + withRoom);
      System.out.println("[HARD] Pares de aulas na mesma sala à mesma hora: " + selectedMetrics.roomConflictPairs());
      System.out.println("[HARD] Aulas que precisam de sala e ficaram sem sala: " + selectedMetrics.unassignedRooms());
      System.out.println("[SOFT] Aulas em sala de tipo diferente do pedido: " + selectedMetrics.typeMismatches()
              + " (" + Math.round(100.0 * selectedMetrics.typeMismatches() / Math.max(1, withRoom)) + "%)");
      System.out.println("[SOFT] Lugares vazios (soma): " + selectedMetrics.excessSeats()
              + " | média por aula: " + Math.round((double) selectedMetrics.excessSeats() / Math.max(1, withRoom)));
      System.out.println("Objetivos: " + java.util.Arrays.toString(selectedProblem.objectiveValues(selectedMetrics)));
      System.out.println("Alternativas distintas na frente agregada: " + front.size()
              + " | CSV disponíveis: " + alternatives.size() + " | Destaques: " + highlights.size() + ".");
    }
  }

  /** Exporta cada frente isoladamente e reúne apenas a tabela de métricas comuns, sem misturar dominâncias. */
  @SuppressWarnings("unchecked")
  private static void exportSeparateProblems(Path output,
      org.uma.jmetal.lab.experiment.Experiment<IntegerSolution, List<IntegerSolution>> experiment,
      RoomPolicy policy) throws IOException {
    List<Map<String, Object>> options = new ArrayList<>(), summaries = new ArrayList<>();
    List<Object> instances = new ArrayList<>();
    for (var instance : experiment.getProblemList()) {
      String tag = instance.getTag();
      Path childOutput = output.resolve("problemas").resolve(tag);
      var singleProblem = new org.uma.jmetal.lab.experiment.ExperimentBuilder<IntegerSolution, List<IntegerSolution>>(
              experiment.getExperimentName()).setProblemList(List.of(instance))
              .setAlgorithmList(experiment.getAlgorithmList())
              .setExperimentBaseDirectory(Path.of(experiment.getExperimentBaseDirectory()).getParent().toString())
              .setIndependentRuns(experiment.getIndependentRuns()).build();
      // A experiência serve só para localizar FUN/VAR já calculados; não executa novamente o algoritmo.
      exportNativeSchedules(childOutput, singleProblem, policy);
      var manifest = JsonFiles.MAPPER.readTree(childOutput.resolve("alternatives.json").toFile());
      for (var node : manifest.path("options")) {
        Map<String, Object> option = JsonFiles.MAPPER.convertValue(node, Map.class);
        option.put("file", "problemas/" + tag + "/" + option.get("file"));
        options.add(option);
      }
      summaries.add(JsonFiles.MAPPER.convertValue(
              JsonFiles.MAPPER.readTree(childOutput.resolve("solution-summary.json").toFile()), Map.class));
      JsonFiles.MAPPER.readTree(childOutput.resolve("problem-instances.json").toFile()).forEach(instances::add);
    }
    var highlights = options.stream().filter(row -> Boolean.TRUE.equals(row.get("highlighted"))).toList();
    JsonFiles.write(output.resolve("problem-instances.json"), instances);
    JsonFiles.write(output.resolve("solution-summary.json"), Map.of("problems", summaries));
    JsonFiles.write(output.resolve("alternatives.json"), Map.of("problems", summaries,
            "paretoPoints", options.size(), "exportedAlternatives", options.size(),
            "highlightLimitPerProblem", MAX_HIGHLIGHTS, "highlightedAlternatives", highlights.size(), "options", options));
    writeComparison(output.resolve("alternatives-summary.csv"), options, "file");
    writeComparison(output.resolve("alternatives-highlights.csv"), highlights, "file");
  }

  /** Escolhe os destaques: recomendado, extremos dos objetivos e diversidade pela aglomeração do jMetal. */
  static List<IntegerSolution> selectAlternatives(List<IntegerSolution> candidates, int limit,
                                                 ToDoubleFunction<IntegerSolution> softScore) {
    if (limit < 1) throw new IllegalArgumentException("O limite deve ser positivo");
    var archive = new NonDominatedSolutionListArchive<IntegerSolution>();
    candidates.forEach(archive::add);
    Comparator<IntegerSolution> preference = Comparator
            .comparingDouble((IntegerSolution s) -> -s.constraints()[0])
            .thenComparingDouble(softScore);
    var result = new ArrayList<>(archive.solutions());
    result.sort(preference);
    if (result.isEmpty()) return result;
    var recommended = result.getFirst();
    var protectedOptions = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<IntegerSolution, Boolean>());
    protectedOptions.add(recommended);
    for (int objective = 0; objective < recommended.objectives().length && protectedOptions.size() < limit; objective++) {
      final int index = objective;
      protectedOptions.add(result.stream().min(Comparator.comparingDouble((IntegerSolution s) -> s.objectives()[index])
              .thenComparing(preference)).orElseThrow());
    }
    var density = new CrowdingDistanceDensityEstimator<IntegerSolution>();
    while (result.size() > limit) {
      density.compute(result);
      var worst = result.stream().filter(s -> !protectedOptions.contains(s))
              .min(Comparator.comparingDouble(density::value)).orElseThrow();
      result.removeIf(s -> s == worst);
    }
    result.sort(preference);
    return result;
  }

  /** Escreve um índice para comparar alternativas, separado dos CSV de aulas. */
  private static void writeComparison(Path file, List<Map<String, Object>> options, String fileKey) throws IOException {
    try (var csv = printer(file, "Problema", "Numero_objetivos", "Opcao", "Recomendado", "Destaque", "CSV", "Violacoes_hard", "Choques_sala",
            "Aulas_sem_sala", "Tipo_sala_errado", "Lugares_vazios", "Pontuacao_soft")) {
      for (var option : options) csv.printRecord(option.get("problem"), option.get("objectiveCount"),
              option.get("option"), option.get("recommended"), option.get("highlighted"),
              option.get(fileKey), option.get("hardViolations"), option.get("roomConflictPairs"),
              option.get("unassignedRooms"), option.get("typeMismatches"), option.get("excessSeats"), option.get("softPenalty"));
    }
  }

  /** Verifica as ocorrências, as datas e as regras locais a partir das atribuições descodificadas. */
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
  /** Grava cada aula e os seus indicadores de atribuição num CSV legível no Excel. */
  private static void writeSchedule(Path file, TimetableProblem problem, RoomPolicy policy,
                                    List<Assignment> assignments, ScheduleMetrics metrics) throws IOException {
    Set<Integer> conflicts = TimetableProblem.roomConflictLessons(assignments);
    try (var csv = printer(file, "source_id", "source_csv_row", "Curso", "Unidade de execução", "Turno", "Turma",
            "Inscritos", "Data_original", "Sala_original", "Data", "Inicio", "Fim", "Sala",
            "Capacidade_Normal", "synthetic", "hard_violations", "soft_penalty",
            "Pedido_sala_original", "Duracao_original_min", "Estado",
            "Tipo_sala_certo", "Lugares_vazios", "Choque_sala", "Problema", "Numero_objetivos")) {
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
                conflicts.contains(a.lessonIndex()) ? "SIM" : "NAO", problem.name(), problem.numberOfObjectives());
      }
    }
  }

  /** Cria um escritor CSV em UTF-8 com cabeçalho e separador ponto e vírgula. */
  public static CSVPrinter printer(Path path, String... headers) throws IOException {
    return new CSVPrinter(Files.newBufferedWriter(path, StandardCharsets.UTF_8),
            CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(headers).build());
  }
  /** Formata minutos desde a meia-noite como HH:mm:ss. */
  public static String clock(int minute) { return "%02d:%02d:00".formatted(minute / 60, minute % 60); }
}
