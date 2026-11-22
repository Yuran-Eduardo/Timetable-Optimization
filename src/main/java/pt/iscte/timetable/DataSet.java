package pt.iscte.timetable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Agrupa as aulas, as salas e os dados de rastreabilidade da entrada. */
public record DataSet(List<Lesson> lessons, List<Room> rooms, Set<String> featureColumns,
                      List<String> inputErrors, Map<String, String> sourceHashes) {
  /** Preserva as coleções de entrada como cópias imutáveis. */
  public DataSet {
    lessons = List.copyOf(lessons); rooms = List.copyOf(rooms);
    featureColumns = Set.copyOf(featureColumns); inputErrors = List.copyOf(inputErrors);
    sourceHashes = Map.copyOf(sourceHashes);
  }
}
