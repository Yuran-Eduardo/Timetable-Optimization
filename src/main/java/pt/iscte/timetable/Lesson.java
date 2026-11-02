package pt.iscte.timetable;

import java.time.LocalDate;
import java.util.Set;

/** One source record remains one occurrence, regardless of the number of classes. */
public record Lesson(String id, int sourceRow, String courses, String unit, String shift,
                     Set<String> groups, int students, LocalDate originalDate,
                     int duration, String requestedFeature, String originalRoom) {
  public Lesson { groups = Set.copyOf(groups); }
  public boolean needsRoom() { return !requestedFeature.equals("Não necessita de sala"); }
}
