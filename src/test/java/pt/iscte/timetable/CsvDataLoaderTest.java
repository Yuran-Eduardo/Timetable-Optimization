package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifica o formato tratado e a preservação de linhas com erros de entrada. */
class CsvDataLoaderTest {
  @TempDir Path temporary;

  /** Aceita BOM, sep=; e campos entre aspas, sem apagar aulas inválidas. */
  @Test void leCsvTratadoEPreservaOcorrenciasInvalidas() throws Exception {
    Path catalog = temporary.resolve("salas.csv");
    Files.writeString(catalog, "\uFEFFsep=;\nedificio;sala;cap_normal;cap_exame;n_caracteristicas;sala_aulas_normal\nS;R;50;25;1;X\n");
    Path source = temporary.resolve("aulas.csv");
    Files.writeString(source, "\uFEFFid;curso;uc;turno;turma;inscritos;inicio;fim;data;caracteristica_pedida;sala\n"
        + "A;S;\"Dados; decisão\";TP;\"G1, G2\";0;08:00:00;09:30:00;30/02/2026;sala_aulas_normal;\n"
        + ";S;Outra;TP2;G1;;09:30:00;11:00:00;02/11/2026;sala_aulas_normal;\n");
    var data = CsvDataLoader.load(source, catalog);
    assertEquals(2, data.lessons().size());
    var first = data.lessons().getFirst();
    assertEquals("A", first.id());
    assertEquals("Dados; decisão", first.unit());
    assertEquals(2, first.groups().size());
    assertEquals(0, first.students());
    assertEquals(480, first.start());
    assertEquals(90, first.duration());
    assertNull(first.originalDate());
    assertEquals("CSV-000003", data.lessons().get(1).id());
    assertEquals(-1, data.lessons().get(1).students());
    assertEquals(2, data.inputErrors().size());
    assertEquals(50, data.rooms().getFirst().capacity());
    assertTrue(data.rooms().getFirst().features().contains(Fixtures.NORMAL));
    assertEquals(2, data.sourceHashes().size());
    assertTrue(data.sourceHashes().values().stream().allMatch(hash -> hash.matches("[0-9a-f]{64}")));
  }
}
