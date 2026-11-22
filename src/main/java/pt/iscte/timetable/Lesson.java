package pt.iscte.timetable;

import java.time.LocalDate;
import java.util.Set;

/** Representa uma aula numa data concreta, mesmo quando é partilhada por várias turmas. */
public record Lesson(String id, int sourceRow, String courses, String unit, String shift,
                     Set<String> groups, int students, LocalDate originalDate,
                     int duration, int start, String requestedFeature, String originalRoom) {
  /** Guarda as turmas da aula como um conjunto imutável. */
  public Lesson { groups = Set.copyOf(groups); }
  // Nos dados tratados, "Não necessita de sala" passou a ser o código sem_sala.
  public static final String NO_ROOM = "sem_sala";
  // Regra do grupo: só as aulas sem_sala não recebem sala. Quem pede sala recebe sempre, mesmo com 0 inscritos.
  public boolean needsRoom() { return !requestedFeature.equals(NO_ROOM); }
  // Hora de fim, em minutos desde a meia-noite (o início vem do CSV e não é alterado pelo algoritmo).
  public int end() { return start + duration; }
}
