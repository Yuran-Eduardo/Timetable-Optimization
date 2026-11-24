package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifica a validação das atribuições e a preservação dos CSV publicados. */
class ResultExporterTest {
  @TempDir Path project;

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
