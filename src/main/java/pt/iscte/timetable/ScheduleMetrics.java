package pt.iscte.timetable;

/** Values calculated by TimetableProblem; units remain separate. */
public record ScheduleMetrics(long outsidePreferred, long gapMinutes, long excessSeats,
    long roomConflictPairs, long classConflictPairs, long sequenceViolations,
    long roomOverlapMinutes, long classOverlapMinutes, long sequenceExcessMinutes,
    long seatDeficit, long individualLongExceptions, long unassignedRooms,
    long incompleteOccurrences, long unassessedPreferences) {
  public long hardViolations() {
    return roomConflictPairs + classConflictPairs + sequenceViolations + seatDeficit
        + unassignedRooms + incompleteOccurrences;
  }
  public boolean feasible() {
    return hardViolations() == 0;
  }
}
