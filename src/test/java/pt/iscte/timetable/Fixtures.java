package pt.iscte.timetable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dados pequenos e independentes dos cenários antigos, usados apenas nos testes. */
final class Fixtures {
  static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
  static final String NORMAL = "sala_aulas_normal";
  static final String LAB = "lab_informatica";
  static final Room ROOM = new Room("R", "TESTE", 50, Set.of(NORMAL));

  private Fixtures() {}

  /** Cria uma experiência curta sem depender de ficheiros de demonstração removidos. */
  static StudyConfig config() {
    return new StudyConfig("teste-automatizado", true, "", "", "", MONDAY, MONDAY.plusDays(5),
        480, 1380, 30, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), Set.of(), true, true,
        "SEPARATE_PERIODS", Map.of("Dia", new TimeWindow(LocalTime.of(8, 0), LocalTime.of(23, 0))),
        Map.of("G1", List.of("Dia")), 10, 40, 2, 1, List.of(4),
        Map.of("type", 1.0, "seats", 1.0), true);
  }

  /** Altera um parâmetro de teste sem mudar a configuração real do projeto. */
  static StudyConfig change(StudyConfig source, String key, Object value) {
    ObjectNode node = JsonFiles.MAPPER.valueToTree(source);
    node.set(key, JsonFiles.MAPPER.valueToTree(value));
    return JsonFiles.MAPPER.convertValue(node, StudyConfig.class);
  }

  /** Cria uma aula com data e início explícitos, em minutos desde a meia-noite. */
  static Lesson lesson(String id, int start, int duration, int students, String request) {
    return new Lesson(id, 1, "TESTE", "UC-" + id, "T-" + id, Set.of("G1"),
        students, MONDAY, duration, start, request, "");
  }

  /** Reúne aulas e salas com as características conhecidas dos testes. */
  static DataSet data(List<Lesson> lessons, List<Room> rooms) {
    return new DataSet(lessons, rooms, Set.of(NORMAL, LAB, "anfiteatro", "lab_pendente"), List.of(), Map.of());
  }

  /** Mantém um pedido pendente para verificar os diagnósticos de entrada. */
  static RoomPolicy policy() {
    return new RoomPolicy(Map.of(), Set.of("lab_pendente"));
  }

  /** Cria uma atribuição no mesmo dia das aulas de teste. */
  static Assignment at(int index, int start, int duration, Room room) {
    return new Assignment(index, MONDAY, start, start + duration, room);
  }

  /** Cria quatro ocorrências, incluindo uma sem sala e outra com zero inscritos. */
  static DataSet smallData() {
    return data(List.of(lesson("A", 480, 90, 30, NORMAL), lesson("B", 480, 90, 40, LAB),
        lesson("C", 570, 90, 0, NORMAL), lesson("D", 480, 90, 30, Lesson.NO_ROOM)),
        List.of(ROOM, new Room("L", "TESTE", 40, Set.of(LAB)),
            new Room("A", "TESTE", 100, Set.of("anfiteatro"))));
  }

  /** Constrói o problema com a mesma validação usada pelo programa. */
  static TimetableProblem problem(DataSet data) {
    var config = config();
    return new TimetableProblem(data, config, Preflight.inspect(data, policy(), config));
  }
}
