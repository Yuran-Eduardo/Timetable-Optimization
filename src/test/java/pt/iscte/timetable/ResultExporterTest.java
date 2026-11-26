package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.solution.integersolution.impl.DefaultIntegerSolution;
import org.uma.jmetal.lab.experiment.ExperimentBuilder;
import org.uma.jmetal.lab.experiment.util.ExperimentProblem;
import com.iscte.timetable.NSGAIIStudy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifica a validação das atribuições e a preservação dos CSV publicados. */
class ResultExporterTest {
  @TempDir Path project;

  /** Mantém seis compromissos distintos, incluindo o recomendado e os extremos. */
  @Test void selecionaAteSeisAlternativasNaoDominadasSemRepetirObjetivos() {
    var problem = Fixtures.problem(Fixtures.smallData());
    List<IntegerSolution> candidates = new ArrayList<>();
    for (int i = 0; i <= 8; i++) {
      var solution = new DefaultIntegerSolution(problem.variableBounds(), 4, 1);
      solution.objectives()[2] = i;
      solution.objectives()[3] = 80 - 10 * i;
      candidates.add(solution);
    }
    candidates.add((IntegerSolution) candidates.getFirst().copy());
    var dominated = (IntegerSolution) candidates.getFirst().copy();
    dominated.objectives()[3] = 90;
    candidates.add(dominated);
    var infeasible = (IntegerSolution) candidates.getFirst().copy();
    infeasible.objectives()[3] = 0;
    infeasible.constraints()[0] = -1;
    candidates.add(infeasible);
    var selected = ResultExporter.selectAlternatives(candidates, 6,
            s -> s.objectives()[2] + s.objectives()[3] / 100.0);
    assertEquals(6, selected.size());
    assertSame(candidates.getFirst(), selected.getFirst());
    assertTrue(selected.stream().anyMatch(s -> s.objectives()[2] == 8));
    assertEquals(6, selected.stream().map(s -> java.util.Arrays.toString(s.objectives())).distinct().count());
    assertTrue(selected.stream().allMatch(s -> s.constraints()[0] == 0));
    assertTrue(selected.stream().noneMatch(s -> s == dominated));
    var shorter = ResultExporter.selectAlternatives(candidates.subList(0, 2), 6, s -> s.objectives()[2]);
    assertEquals(2, shorter.size());
    assertEquals(1, ResultExporter.selectAlternatives(List.of(candidates.getFirst(), (IntegerSolution) candidates.getFirst().copy()),
            6, s -> s.objectives()[2]).size());
  }

  /** Reúne execuções, elimina dominadas/repetições e publica os CSV com proveniência e métricas. */
  @Test void exportaTodaAFrenteEDestacaSeisSemPerderAsRestantesOpcoes() throws Exception {
    List<Lesson> lessons = new ArrayList<>();
    for (int i = 0; i < 8; i++) lessons.add(Fixtures.lesson("A" + i, 480 + 30 * i, 30, 30, Fixtures.NORMAL));
    var data = Fixtures.data(lessons, List.of(new Room("N", "TESTE", 100, Set.of(Fixtures.NORMAL)),
            new Room("N2", "TESTE", 120, Set.of(Fixtures.NORMAL)),
            new Room("L", "TESTE", 30, Set.of(Fixtures.LAB))));
    var config = Fixtures.change(Fixtures.config(), "problemSizes", List.of(8));
    var problem = new TimetableProblem(data, config, Preflight.inspect(data, Fixtures.policy(), config));
    var problems = List.of(new ExperimentProblem<IntegerSolution>(problem));
    Path output = project.resolve("estudo");
    var experiment = new ExperimentBuilder<IntegerSolution, List<IntegerSolution>>("NSGAIIStudy")
            .setProblemList(problems).setAlgorithmList(NSGAIIStudy.configureAlgorithmList(problems, config))
            .setExperimentBaseDirectory(output.toString()).setIndependentRuns(2).setNumberOfCores(1).build();
    Path nativeDirectory = Path.of(experiment.getExperimentBaseDirectory(), "data/NSGAIIa/" + problem.name());
    Files.createDirectories(nativeDirectory);
    List<List<String>> fun = List.of(new ArrayList<>(), new ArrayList<>());
    List<List<String>> vars = List.of(new ArrayList<>(), new ArrayList<>());
    for (int k = 0; k <= 10; k++) {
      var solution = new DefaultIntegerSolution(problem.variableBounds(), 4, 1);
      for (int gene = 0; gene < 8; gene++) solution.variables().set(gene, k <= 8 && gene < k ? 2 : 0);
      if (k == 9) solution.variables().set(7, 2); // Mesmo objetivo de k=1, com outra atribuição.
      if (k == 10) solution.variables().set(0, 1); // Dominada por k=0.
      problem.evaluate(solution);
      int run = k % 2;
      fun.get(run).add(java.util.Arrays.stream(solution.objectives()).mapToObj(Double::toString)
              .collect(java.util.stream.Collectors.joining(",")));
      vars.get(run).add(solution.variables().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")));
    }
    for (int run = 0; run < 2; run++) {
      Files.write(nativeDirectory.resolve("FUN" + run + ".csv"), fun.get(run));
      Files.write(nativeDirectory.resolve("VAR" + run + ".csv"), vars.get(run));
    }
    ResultExporter.exportNativeSchedules(output, experiment, Fixtures.policy());
    var manifest = JsonFiles.MAPPER.readTree(output.resolve("alternatives.json").toFile());
    assertEquals(9, manifest.path("paretoPoints").asInt());
    assertEquals(9, manifest.path("exportedAlternatives").asInt());
    assertEquals(9, manifest.path("options").size());
    assertEquals(6, manifest.path("highlightedAlternatives").asInt());
    assertEquals(6, manifest.path("highlightLimit").asInt());
    int highlights = 0;
    var distinctObjectives = new java.util.HashSet<String>();
    for (var option : manifest.path("options")) {
      assertTrue(distinctObjectives.add(option.path("objectives").toString()));
      assertEquals(0, option.path("hardViolations").asLong());
      assertTrue(option.has("run") && option.has("nativeSolution"));
      if (option.path("highlighted").asBoolean()) highlights++;
      try (var reader = Files.newBufferedReader(output.resolve(option.path("file").asText()));
           var csv = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
        var records = csv.getRecords();
        assertEquals(8, records.size());
        assertEquals(8, records.stream().map(r -> r.get("source_id")).distinct().count());
        assertTrue(records.stream().allMatch(r -> r.get("Choque_sala").equals("NAO")));
      }
    }
    assertEquals(6, highlights);
    assertTrue(manifest.path("options").get(0).path("highlighted").asBoolean());
    assertTrue(manifest.path("options").get(8).path("highlighted").asBoolean());
    var published = ResultExporter.publishLatest(output, project);
    var firstPublication = Files.readString(published);
    ResultExporter.publishLatest(output, project);
    assertEquals(firstPublication, Files.readString(published));
    var publishedDirectory = project.resolve("Resultados-Horarios-CSV");
    try (var files = Files.walk(publishedDirectory)) {
      assertEquals(18, files.filter(p -> p.getFileName().toString().startsWith("Horario-") && p.toString().endsWith(".csv")).count());
    }
    try (var files = Files.list(publishedDirectory)) {
      var comparison = files.filter(p -> p.getFileName().toString().startsWith("Comparacao-") && p.toString().endsWith(".csv")).findFirst().orElseThrow();
      try (var reader = Files.newBufferedReader(comparison);
           var csv = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
        var records = csv.getRecords();
        assertEquals(9, records.size());
        assertEquals("true", records.getFirst().get("Recomendado"));
        assertEquals(6, records.stream().filter(r -> r.get("Destaque").equals("true")).count());
        assertEquals(3, records.stream().filter(r -> r.get("Destaque").equals("false")).count());
        for (var row : records) assertTrue(Files.isRegularFile(publishedDirectory.resolve(row.get("CSV"))));
      }
    }
    try (var files = Files.list(publishedDirectory)) {
      var highlightsFile = files.filter(p -> p.getFileName().toString().startsWith("Destaques-") && p.toString().endsWith(".csv")).findFirst().orElseThrow();
      try (var reader = Files.newBufferedReader(highlightsFile);
           var csv = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
        var records = csv.getRecords();
        assertEquals(6, records.size());
        assertTrue(records.stream().allMatch(r -> r.get("Destaque").equals("true")));
      }
    }
  }

  /** Duas publicações geram nomes distintos e preservam os conteúdos e metadados anteriores. */
  @Test void publicaDoisCsvSemSubstituirOAnterior() throws Exception {
    Path output = project.resolve("Resultados/example-run");
    Files.createDirectories(output);
    String content = "source_id;Sala\nCSV-000002;R1\n";
    Files.writeString(output.resolve("horario-exemplo.csv"), content);
    JsonFiles.write(output.resolve("solution-summary.json"), java.util.Map.of("occurrences", 1, "hardViolations", 2));
    Path first = ResultExporter.publishLatest(output, project);
    Path second = ResultExporter.publishLatest(output, project);
    assertNotEquals(first, second);
    assertEquals("Resultados-Horarios-CSV", first.getParent().getFileName().toString());
    assertTrue(first.getFileName().toString().startsWith("Horario-"));
    assertEquals(content, Files.readString(first));
    assertEquals(content, Files.readString(second));
    Path metadata = first.resolveSibling(first.getFileName().toString().replace(".csv", ".json"));
    var json = JsonFiles.MAPPER.readTree(metadata.toFile());
    assertEquals(1, json.path("solution-summary").path("occurrences").asInt());
    assertEquals("Resultados/example-run", json.path("experimentDirectory").asText());
    assertTrue(Files.readString(project.resolve("RESULTADOS.txt")).contains(second.getFileName().toString()));
  }

  /** Rejeita exportações com aulas duplicadas, horas alteradas ou capacidades inventadas. */
  @Test void rejeitaOcorrenciasDuplicadasEAlteracoesDosDadosOriginais() {
    var data = Fixtures.data(List.of(Fixtures.lesson("A", 480, 90, 30, Fixtures.NORMAL),
        Fixtures.lesson("B", 570, 90, 30, Fixtures.NORMAL)), List.of(Fixtures.ROOM));
    var problem = Fixtures.problem(data);
    var first = Fixtures.at(0, 480, 90, Fixtures.ROOM);
    var second = Fixtures.at(1, 570, 90, Fixtures.ROOM);
    assertThrows(IllegalStateException.class,
        () -> ResultExporter.validateLocal(problem, Fixtures.policy(), List.of(first)));
    assertThrows(IllegalStateException.class,
        () -> ResultExporter.validateLocal(problem, Fixtures.policy(), List.of(first, first)));
    assertThrows(IllegalStateException.class,
        () -> ResultExporter.validateLocal(problem, Fixtures.policy(),
            List.of(Fixtures.at(0, 510, 90, Fixtures.ROOM), second)));
    var forged = new Room(Fixtures.ROOM.id(), Fixtures.ROOM.building(), 500, Fixtures.ROOM.features());
    assertThrows(IllegalStateException.class,
        () -> ResultExporter.validateLocal(problem, Fixtures.policy(),
            List.of(Fixtures.at(0, 480, 90, forged), second)));
  }
}
