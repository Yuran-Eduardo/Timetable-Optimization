package pt.iscte.timetable;

import com.iscte.timetable.NSGAIIStudy;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercita a entrada do programa e a pipeline nativa numa experiência curta. */
class NSGAIIStudyTest {
  @TempDir Path temporary;

  /** A opção de validação lê os dados reais sem arrancar o NSGA-II. */
  @Test void validaDadosCompletosSemExecutarAlgoritmo() throws Exception {
    var output = temporary.resolve("validacao com espacos");
    NSGAIIStudy.main(new String[]{"--validate-only", "--output=" + output});
    var report = JsonFiles.MAPPER.readTree(output.resolve("validation.json").toFile());
    assertEquals(26019, report.path("lessons").asInt());
    assertEquals(132, report.path("catalogRooms").asInt());
    assertFalse(Files.exists(output.resolve("NSGAIIStudy/data")));
    assertFalse(Files.exists(output.resolve("horario-exemplo.csv")));
  }

  /** Confirma FUN/VAR, indicadores, scripts, CSV e cobertura das aulas de ponta a ponta. */
  @Test void executaPipelineNativaEExportaTodasAsAulas() throws Exception {
    var data = Fixtures.smallData();
    var config = Fixtures.config();
    var experiment = NSGAIIStudy.execute(data, Fixtures.policy(), config,
        Preflight.inspect(data, Fixtures.policy(), config), temporary);
    assertEquals(1, experiment.getProblemList().size());
    assertTrue(experiment.getAlgorithmList().stream().allMatch(a -> a.getAlgorithmTag().equals("NSGAIIa")));
    var nativeOutput = temporary.resolve("NSGAIIStudy");
    var directory = nativeOutput.resolve("data/NSGAIIa/Timetable-4");
    for (int run = 0; run < 2; run++) {
      var fun = Files.readAllLines(directory.resolve("FUN" + run + ".csv"));
      var variables = Files.readAllLines(directory.resolve("VAR" + run + ".csv"));
      assertFalse(fun.isEmpty());
      assertEquals(fun.size(), variables.size());
      for (String line : fun) assertEquals(2, line.split(",").length);
      for (String line : variables) assertEquals(4, line.split(",").length);
    }
    for (var indicator : experiment.getIndicatorList()) {
      var values = Files.readAllLines(directory.resolve(indicator.name()));
      assertEquals(2, values.size());
      assertTrue(values.stream().mapToDouble(Double::parseDouble).allMatch(Double::isFinite));
    }
    try (var files = Files.list(nativeOutput.resolve("latex"))) {
      assertTrue(files.anyMatch(p -> p.toString().endsWith(".tex")));
    }
    try (var files = Files.list(nativeOutput.resolve("R"))) {
      assertTrue(files.anyMatch(p -> p.toString().endsWith(".R")));
    }
    Set<String> ids = new HashSet<>();
    try (var reader = Files.newBufferedReader(temporary.resolve("horario-exemplo.csv"));
         var csv = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader()
             .setSkipHeaderRecord(true).build().parse(reader)) {
      var rows = csv.getRecords();
      assertEquals(4, rows.size());
      for (var row : rows) {
        assertTrue(ids.add(row.get("source_id")));
        assertEquals("NAO", row.get("Choque_sala"));
        var lesson = data.lessons().stream().filter(l -> l.id().equals(row.get("source_id"))).findFirst().orElseThrow();
        assertEquals(lesson.originalDate().toString(), row.get("Data"));
        assertEquals(ResultExporter.clock(lesson.start()), row.get("Inicio"));
        if (lesson.needsRoom()) assertFalse(row.get("Sala").isBlank());
        else { assertEquals("SEM_SALA", row.get("Estado")); assertTrue(row.get("Sala").isBlank()); }
      }
    }
    assertEquals(Set.of("A", "B", "C", "D"), ids);
    var summary = JsonFiles.MAPPER.readTree(temporary.resolve("solution-summary.json").toFile());
    assertEquals(4, summary.path("occurrences").asInt());
    assertEquals(0, summary.path("hardViolations").asDouble());
    assertTrue(Files.exists(temporary.resolve("horarios/Timetable-4/NSGAIIa/run-0/metrics.csv")));
  }
}
