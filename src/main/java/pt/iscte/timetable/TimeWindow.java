package pt.iscte.timetable;

import java.time.LocalTime;

public record TimeWindow(LocalTime start, LocalTime end) {
  public TimeWindow {
    if (start == null || end == null || !start.isBefore(end))
      throw new IllegalArgumentException("Invalid preferred time window");
  }
  public boolean contains(int from, int to) {
    return from >= start.toSecondOfDay() / 60 && to <= end.toSecondOfDay() / 60;
  }
}
