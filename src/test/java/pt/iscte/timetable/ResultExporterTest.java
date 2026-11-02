package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultExporterTest {
  @TempDir Path project;

  @Test void publishingTwiceKeepsBothCsvsAndTheirMetadata() throws Exception {
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
}
