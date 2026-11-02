package pt.iscte.timetable;

import java.time.LocalDate;
import java.util.List;

public record LessonDomain(List<LocalDate> dates, int lastStartIndex, List<Room> rooms) {
  public LessonDomain { dates = List.copyOf(dates); rooms = List.copyOf(rooms); }
}
