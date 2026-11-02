package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FullDataTest {
  @TempDir Path temporary;

  @Test void defaultLoadsAndEvaluatesAll26019OccurrencesWithoutDroppingUnknownRows() throws Exception {
    var input = StudyInput.load(new String[]{"--output=" + temporary.resolve("full")});
    assertEquals(26019, input.data().lessons().size());
    assertEquals(List.of(26019), input.config().problemSizes());
    assertFalse(input.validation().report().fullyEvaluable());
    assertTrue(input.validation().runnable(input.config()));
    var problem = new TimetableProblem(input.data(), input.config(), input.validation());
    assertEquals(78057, problem.numberOfVariables());
    var solution = problem.createSolution(); problem.evaluate(solution);
    assertEquals(2, solution.objectives().length); assertEquals(0, solution.constraints().length);
    assertTrue(Double.isFinite(solution.objectives()[0]));
    assertTrue(Double.isFinite(solution.objectives()[1]));
    var assignments = problem.decode(solution);
    assertEquals(26019, assignments.size());
    assertEquals(26019, assignments.stream().map(Assignment::lessonIndex).distinct().count());
    assertEquals(16, assignments.stream().filter(a -> a.date() == null).count());
    var metrics = TimetableProblem.calculateMetrics(input.data().lessons(), assignments, input.config());
    assertEquals(672, metrics.unassignedRooms());
    assertEquals(26019, metrics.unassessedPreferences());
    ResultExporter.validateLocal(problem, input.policy(), assignments);
  }

  @Test void unknownRequestsAndMissingDatesRemainExplicitInsteadOfInventingAssignments() throws Exception {
    var config = Fixtures.change(Fixtures.change(Fixtures.config(), "allowIncompleteData", true),
        "softWeights", java.util.Map.of("preferred", 0.0, "gaps", 1.0, "seats", 1.0));
    var missing = new Lesson("3", 4, "S", "C", "T", Set.of(), 60, null, 90, "Sala de Aulas normal", "");
    var data = Fixtures.data(List.of(Fixtures.lesson("1", "A", 90, "G1"),
        Fixtures.lesson("2", "B", 90, 30, "Lab ISTA", "G1"), missing), List.of(Fixtures.ROOM));
    var validation = Preflight.inspect(data, Fixtures.policy(), config);
    var problem = new TimetableProblem(data, config, validation);
    var solution = problem.createSolution();
    for (int i = 0; i < solution.variables().size(); i++) solution.variables().set(i, 0);
    problem.evaluate(solution);
    var assignments = problem.decode(solution);
    assertNull(assignments.get(1).room());
    assertNull(assignments.get(2).date()); assertEquals(-1, assignments.get(2).start());
    var metrics = TimetableProblem.calculateMetrics(data.lessons(), assignments, config);
    assertEquals(2, metrics.unassignedRooms()); assertEquals(1, metrics.incompleteOccurrences());
    assertEquals(4, solution.objectives()[0]);
    assertFalse(validation.report().fullyEvaluable());
    ResultExporter.validateLocal(problem, Fixtures.policy(), assignments);
  }
}
