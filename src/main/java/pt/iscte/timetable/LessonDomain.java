package pt.iscte.timetable;

import java.time.LocalDate;
import java.util.List;

/** Guarda as salas candidatas e os campos de calendário herdados do modelo inicial. */
public record LessonDomain(List<LocalDate> dates, int lastStartIndex, List<Room> rooms) {
  /** Preserva as datas e as salas candidatas como listas imutáveis. */
  public LessonDomain { dates = List.copyOf(dates); rooms = List.copyOf(rooms); }
}
