package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimetableProblemTest {
  @Test void evaluateAssignsHardConflictsAndNormalizedSoftPenaltyDirectly() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 90, "G1"), Fixtures.lesson("2", "B", 90, "G1"));
    var data = Fixtures.data(lessons, List.of(Fixtures.ROOM)); var config = Fixtures.config();
    var problem = new TimetableProblem(data, config, Preflight.inspect(data, Fixtures.policy(), config));
    var solution = problem.createSolution();
    for (int gene = 0; gene < 6; gene++) solution.variables().set(gene, 0);
    problem.evaluate(solution);
    assertArrayEquals(new double[]{2, 1}, solution.objectives(), 1e-12);
    assertEquals(0, solution.constraints().length);
  }

  @Test void sharedClassesAccumulateGapsButNotOccurrences() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 90, "G1", "G2"), Fixtures.lesson("2", "B", 90, "G1", "G2"));
    var m = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 540, 90, Fixtures.ROOM), Fixtures.at(1, 660, 90, Fixtures.ROOM)), Fixtures.config());
    assertEquals(60, m.gapMinutes());
    assertEquals(40, m.excessSeats());
    assertEquals(0, m.outsidePreferred());
    assertTrue(m.feasible());
  }
  @Test void sharedConflictPairIsNotCountedTwice() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 90, "G1", "G2"), Fixtures.lesson("2", "B", 90, "G1", "G2"));
    var m = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 540, 90, Fixtures.ROOM), Fixtures.at(1, 600, 90, Fixtures.ROOM)), Fixtures.config());
    assertEquals(1, m.classConflictPairs()); assertEquals(1, m.roomConflictPairs());
    assertEquals(30, m.classOverlapMinutes()); assertEquals(30, m.roomOverlapMinutes());
    assertEquals(0, m.gapMinutes()); assertFalse(m.feasible());
  }
  @Test void gapsUseUnionDuringOverlapAndExcludeDayEdges() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 180, "G1"), Fixtures.lesson("2", "B", 30, "G1"), Fixtures.lesson("3", "C", 30, "G1"));
    var m = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 540, 180, null), Fixtures.at(1, 600, 30, null), Fixtures.at(2, 750, 30, null)), Fixtures.config());
    assertEquals(30, m.gapMinutes());
  }
  @Test void lunchIsNotDeducted() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 60, "G2"), Fixtures.lesson("2", "B", 60, "G2"));
    var m = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 720, 60, null), Fixtures.at(1, 840, 60, null)), Fixtures.config());
    assertEquals(60, m.gapMinutes());
  }
  @Test void sharedPreferredViolationCountsOnce() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 60, "G1", "G3"));
    var m = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 540, 60, null)), Fixtures.config());
    assertEquals(1, m.outsidePreferred());
  }
  @Test void multipleProfilesAreSeparateAndMustContainEntireLesson() throws Exception {
    var config = Fixtures.config();
    assertFalse(config.preferred(Fixtures.lesson("1", "A", 90, "G2"), 750, 840));
    assertTrue(config.preferred(Fixtures.lesson("1", "A", 60, "G2"), 780, 840));
    assertTrue(config.preferred(Fixtures.lesson("1", "A", 90, "G1"), 690, 780));
    assertFalse(config.preferred(Fixtures.lesson("1", "A", 90, "G1"), 720, 810));
    assertFalse(config.preferred(Fixtures.lesson("1", "A", 60, "G3"), 1320, 1380));
  }
  @Test void consecutiveTeachingTimeExcludesPausesAndThirtyMinutesBreaksBlock() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 120, "G1"), Fixtures.lesson("2", "A", 120, "G1"));
    var touching = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 480, 120, null), Fixtures.at(1, 600, 120, null)), Fixtures.config());
    assertEquals(1, touching.sequenceViolations()); assertEquals(60, touching.sequenceExcessMinutes());
    var separated = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 480, 120, null), Fixtures.at(1, 630, 120, null)), Fixtures.config());
    assertEquals(0, separated.sequenceViolations()); assertEquals(30, separated.gapMinutes());
    var shortPause = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 480, 120, null), Fixtures.at(1, 615, 120, null)), Fixtures.config());
    assertEquals(60, shortPause.sequenceExcessMinutes());
  }
  @Test void longIndividualIsExceptionButCannotAbsorbAnotherOccurrence() throws Exception {
    var longLesson = Fixtures.lesson("1", "A", 240, "G1");
    var individual = TimetableProblem.calculateMetrics(List.of(longLesson), List.of(Fixtures.at(0, 480, 240, null)), Fixtures.config());
    assertEquals(1, individual.individualLongExceptions()); assertEquals(0, individual.sequenceViolations());
    var joined = TimetableProblem.calculateMetrics(List.of(longLesson, Fixtures.lesson("2", "A", 90, "G1")),
        List.of(Fixtures.at(0, 480, 240, null), Fixtures.at(1, 720, 90, null)), Fixtures.config());
    assertEquals(1, joined.sequenceViolations()); assertEquals(150, joined.sequenceExcessMinutes());
  }
  @Test void noRoomActivityStillOccupiesClassesAndDoesNotConsumeSeats() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 90, 30, "Não necessita de sala", "G1"), Fixtures.lesson("2", "B", 90, "G1"));
    var m = TimetableProblem.calculateMetrics(lessons, List.of(Fixtures.at(0, 540, 90, null), Fixtures.at(1, 540, 90, Fixtures.ROOM)), Fixtures.config());
    assertEquals(1, m.classConflictPairs()); assertEquals(0, m.roomConflictPairs()); assertEquals(20, m.excessSeats());
  }
  @Test void zeroStudentsAndDeficitRemainDistinct() throws Exception {
    var zero = TimetableProblem.calculateMetrics(List.of(Fixtures.lesson("1", "A", 90, 0, "Sala de Aulas normal", "G1")),
        List.of(Fixtures.at(0, 540, 90, Fixtures.ROOM)), Fixtures.config());
    assertEquals(50, zero.excessSeats()); assertEquals(0, zero.seatDeficit());
    var deficit = TimetableProblem.calculateMetrics(List.of(Fixtures.lesson("1", "A", 90, 60, "Sala de Aulas normal", "G1")),
        List.of(Fixtures.at(0, 540, 90, Fixtures.ROOM)), Fixtures.config());
    assertEquals(0, deficit.excessSeats()); assertEquals(10, deficit.seatDeficit()); assertFalse(deficit.feasible());
  }
}
