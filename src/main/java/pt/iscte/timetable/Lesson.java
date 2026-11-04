package pt.iscte.timetable;

import java.time.LocalDate;
import java.util.Set;

/** One source record remains one occurrence, regardless of the number of classes. */
public record Lesson(String id, int sourceRow, String courses, String unit, String shift,
                     Set<String> groups, int students, LocalDate originalDate,
                     int duration, int start, String requestedFeature, String originalRoom) {
  public Lesson { groups = Set.copyOf(groups); }
  // Nos dados tratados, "Não necessita de sala" passou a ser o código sem_sala.
  public static final String NO_ROOM = "sem_sala";
  // Regra do grupo: aulas sem_sala e aulas com 0 inscritos não recebem sala.
  public boolean needsRoom() { return !requestedFeature.equals(NO_ROOM) && students > 0; }
  // Hora de fim, em minutos desde a meia-noite (o início vem do CSV e não é alterado pelo algoritmo).
  public int end() { return start + duration; }
}
