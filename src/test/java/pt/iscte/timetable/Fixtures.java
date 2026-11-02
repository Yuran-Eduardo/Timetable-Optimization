package pt.iscte.timetable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Fixtures {
  static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
  static final Room ROOM = new Room("R", "SYNTHETIC", 50, Set.of("Sala de Aulas normal"));
  static StudyConfig config() throws IOException {
    return JsonFiles.MAPPER.readValue(Path.of("config/demo.json").toFile(), StudyConfig.class);
  }
  static StudyConfig change(StudyConfig source, String key, Object value) {
    ObjectNode node = JsonFiles.MAPPER.valueToTree(source);
    node.set(key, JsonFiles.MAPPER.valueToTree(value));
    return JsonFiles.MAPPER.convertValue(node, StudyConfig.class);
  }
  static Lesson lesson(String id, String unit, int duration, int students, String request, String... groups) {
    return new Lesson(id, 1, "SYNTHETIC", unit, id, Set.of(groups), students, MONDAY, duration, request, "ORIGINAL-UNBOUND");
  }
  static Lesson lesson(String id, String unit, int duration, String... groups) {
    return lesson(id, unit, duration, 30, "Sala de Aulas normal", groups);
  }
  static DataSet data(List<Lesson> lessons, List<Room> rooms) {
    return new DataSet(lessons, rooms, Set.of("Sala de Aulas normal", "Anfiteatro aulas", "Laboratório de Informática", "MISSING-FEATURE"), List.of(), Map.of());
  }
  static RoomPolicy policy() {
    return new RoomPolicy(Map.of("Sala/anfiteatro aulas", Set.of("Sala de Aulas normal", "Anfiteatro aulas")), Set.of("Lab ISTA", ""));
  }
  static Assignment at(int index, int start, int duration, Room room) {
    return new Assignment(index, MONDAY, start, start + duration, room);
  }
  static DataSet demo() throws IOException {
    return CsvDataLoader.load(Path.of("data/sinteticos/horario.csv"), Path.of("data/sinteticos/salas.csv"));
  }
}
