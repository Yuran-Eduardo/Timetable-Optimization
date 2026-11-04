package pt.iscte.timetable;

/** Values calculated by TimetableProblem; units remain separate. */
public record ScheduleMetrics(long outsidePreferred, long gapMinutes, long excessSeats,
                              long roomConflictPairs, long classConflictPairs, long sequenceViolations,
                              long roomOverlapMinutes, long classOverlapMinutes, long sequenceExcessMinutes,
                              long seatDeficit, long individualLongExceptions, long unassignedRooms,
                              long incompleteOccurrences, long unassessedPreferences, long typeMismatches) {
  /** Objetivo 0 (hard): choques de sala + falta de lugares + aulas que precisam de sala e ficaram sem sala.
   *  Choques de turma e blocos de 3h dependem só das horas (fixas), por isso ficam apenas como informação. */
  public long hardViolations() {
    return roomConflictPairs + seatDeficit + unassignedRooms;
  }
  public boolean feasible() {
    return hardViolations() == 0;
  }
}
