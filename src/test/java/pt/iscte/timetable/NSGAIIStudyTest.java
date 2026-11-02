package pt.iscte.timetable;

import com.iscte.timetable.NSGAIIStudy;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NSGAIIStudyTest {
  @TempDir Path temporary;

  @Test void strictScenarioDiagnosesAllOccurrencesWithoutRunningAlgorithm() throws Exception {
    Path output = temporary.resolve("real output with spaces");
    Path arguments = temporary.resolve("arguments.json");
    JsonFiles.write(arguments, List.of("--config=config/real.json", "--output=" + output));
    NSGAIIStudy.main(new String[]{"--arguments-file=" + arguments});
    var report = JsonFiles.MAPPER.readTree(output.resolve("validation.json").toFile());
    assertEquals(26019, report.path("lessons").asInt());
    assertEquals(131, report.path("catalogRooms").asInt());
    assertEquals(24092, report.path("roomCoverage").path("AVAILABLE").asInt());
    assertEquals(301, report.path("roomCoverage").path("UNRESOLVED_REQUEST").asInt());
    assertEquals(371, report.path("roomCoverage").path("INSUFFICIENT_CAPACITY").asInt());
    assertFalse(report.path("fullyEvaluable").asBoolean());
    assertFalse(Files.exists(output.resolve("NSGAIIStudy/data")));
  }

  @Test void completeNativePipelineProducesFunVarIndicatorsLatexAndRScripts() throws Exception {
    var config = Fixtures.change(Fixtures.change(Fixtures.change(Fixtures.config(),
        "populationSize", 20), "maxEvaluations", 200), "independentRuns", 2);
    var data = Fixtures.demo(); var policy = Fixtures.policy();
    var experiment = NSGAIIStudy.execute(data, policy, config, Preflight.inspect(data, policy, config), temporary);
    Path nativeOutput = temporary.resolve("NSGAIIStudy");
    assertEquals(3, experiment.getProblemList().size());
    for (String tag : List.of("NSGAIIa", "NSGAIIb", "NSGAIIc", "NSGAIId")) {
      for (int size : List.of(5, 10, 20)) {
        Path directory = nativeOutput.resolve("data/" + tag + "/Timetable-" + size);
        for (int run = 0; run < 2; run++) {
          List<String> fun = Files.readAllLines(directory.resolve("FUN" + run + ".csv"));
          List<String> variables = Files.readAllLines(directory.resolve("VAR" + run + ".csv"));
          assertFalse(fun.isEmpty()); assertEquals(fun.size(), variables.size());
          assertEquals(2, fun.getFirst().split(",").length);
          assertEquals(3 * size, variables.getFirst().split(",").length);
        }
        for (var indicator : experiment.getIndicatorList()) {
          List<String> values = Files.readAllLines(directory.resolve(indicator.name()));
          assertEquals(2, values.size());
          assertTrue(values.stream().mapToDouble(Double::parseDouble).allMatch(Double::isFinite));
        }
      }
    }
    try (var files = Files.list(nativeOutput.resolve("latex"))) {
      assertTrue(files.anyMatch(p -> p.toString().endsWith(".tex")));
    }
    try (var files = Files.list(nativeOutput.resolve("R"))) {
      assertTrue(files.anyMatch(p -> p.toString().endsWith(".R")));
    }
    assertTrue(Files.exists(temporary.resolve("problem-instances.json")));
    assertTrue(Files.exists(temporary.resolve("horarios/Timetable-20/NSGAIIa/run-0/metrics.csv")));
  }

  @Test void nativeNormalizationSupportsConstantReferenceObjective() {
    double[][] normalized = org.uma.jmetal.util.NormalizeUtils.normalize(new double[][]{{0, 0.25}});
    assertTrue(Double.isFinite(normalized[0][0]));
    assertTrue(Double.isFinite(normalized[0][1]));
  }
}
