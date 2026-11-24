package pt.iscte.timetable;

import java.util.Set;

/** Representa uma sala com identificador textual, capacidade e características. */
public record Room(String id, String building, int capacity, Set<String> features) {
  /** Rejeita capacidade negativa e guarda as características como conjunto imutável. */
  public Room {
    if (capacity < 0) throw new IllegalArgumentException("Negative room capacity: " + id);
    features = Set.copyOf(features);
  }
}
