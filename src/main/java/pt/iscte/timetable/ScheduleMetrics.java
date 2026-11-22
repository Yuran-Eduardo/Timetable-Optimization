package pt.iscte.timetable;

/** Guarda as métricas do horário, separando contagens e durações. */
public record ScheduleMetrics(long outsidePreferred, long gapMinutes, long excessSeats,
                              long roomConflictPairs, long classConflictPairs, long sequenceViolations,
                              long roomOverlapMinutes, long classOverlapMinutes, long sequenceExcessMinutes,
                              long seatDeficit, long individualLongExceptions, long unassignedRooms,
                              long incompleteOccurrences, long unassessedPreferences, long typeMismatches) {
  /** Soma as violações hard: choques de sala, falta de lugares e aulas sem sala. Os choques de turma e as sequências são apenas diagnósticos. */
  public long hardViolations() {
    return roomConflictPairs + seatDeficit + unassignedRooms;
  }
  /** Indica se as métricas não contêm violações hard de atribuição de salas. */
  public boolean feasible() {
    return hardViolations() == 0;
  }
}
