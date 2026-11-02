package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvDataLoaderTest {
  @TempDir Path temporary;
  @Test void impossibleDatesAndMissingDemandAreReportedWithoutDroppingOccurrences() throws Exception {
    Path catalog = temporary.resolve("rooms.csv");
    Files.writeString(catalog, "\uFEFFsep=;\nEdifício;Nome sala;Capacidade Normal;Capacidade Exame;Nº características;Sala de Aulas normal\nS;R;50;25;1;X\n");
    Path source = temporary.resolve("lessons.csv");
    Files.writeString(source, "\uFEFFCurso;Unidade de execução;Turno;Turma;Inscritos no turno;Dia da Semana;Início;Fim;Dia;Características da sala pedida para a aula;Sala da aula;Lotação;Características reais da sala\n"
        + "S;\"Dados; decisão\";TP;\"G1, G2\";0;Seg;08:00:00;09:30:00;30/02/2026;Sala/anfiteatro aulas;R;50;Sala de Aulas normal\n"
        + "S;Outra;TP2;G1;;Seg;08:00:00;09:30:00;02/11/2026;Sala de Aulas normal;R;50;Sala de Aulas normal\n");
    var data = CsvDataLoader.load(source, catalog);
    assertEquals(2, data.lessons().size());
    assertEquals("Dados; decisão", data.lessons().getFirst().unit());
    assertEquals(2, data.lessons().getFirst().groups().size());
    assertEquals(0, data.lessons().getFirst().students());
    assertNull(data.lessons().getFirst().originalDate());
    assertEquals(-1, data.lessons().get(1).students());
    assertEquals(2, data.inputErrors().size());
    assertEquals("Sala/anfiteatro aulas", data.lessons().getFirst().requestedFeature());
    assertEquals("CSV-000002", data.lessons().getFirst().id());
  }
}
