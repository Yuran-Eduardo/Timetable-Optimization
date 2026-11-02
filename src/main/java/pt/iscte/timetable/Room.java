package pt.iscte.timetable;

import java.util.Set;

/** Room identifiers are opaque categories, including source scientific-looking strings. */
public record Room(String id, String building, int capacity, Set<String> features) {
  public Room {
    if (capacity < 0) throw new IllegalArgumentException("Negative room capacity: " + id);
    features = Set.copyOf(features);
  }
}
