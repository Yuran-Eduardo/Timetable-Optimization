package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.util.comparator.dominanceComparator.impl.DefaultDominanceComparator;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

class PreflightAndOperatorsTest {
  @Test void timetableProblemDeclaresTwoObjectivesNoConstraintsAndThreeGenesPerOccurrence() throws Exception {
    var data = Fixtures.demo(); var config = Fixtures.config(); var policy = Fixtures.policy();
    var validation = Preflight.inspect(data, policy, config);
    var problem = new TimetableProblem(data, config, validation);
    assertEquals(20, data.lessons().size());
    assertEquals(60, problem.numberOfVariables());
    assertEquals(2, problem.numberOfObjectives());
    assertEquals(0, problem.numberOfConstraints());
    var solution = problem.createSolution();
    assertEquals(60, solution.variables().size());
    assertEquals(2, solution.objectives().length);
    assertEquals(0, solution.constraints().length);
    assertThrows(IllegalArgumentException.class, () -> new TimetableProblem(21, data, config, policy));
    for (int i = 0; i < data.lessons().size(); i++) {
      assertEquals(validation.domains().get(i).dates().size() - 1, problem.variableBounds().get(3 * i).getUpperBound());
      assertEquals(validation.domains().get(i).lastStartIndex(), problem.variableBounds().get(3 * i + 1).getUpperBound());
      if (!data.lessons().get(i).needsRoom()) assertEquals(0, problem.variableBounds().get(3 * i + 2).getUpperBound());
    }
  }
  @Test void distinguishesPendingFeatureAbsenceAndInsufficientCapacity() throws Exception {
    var lessons = List.of(Fixtures.lesson("1", "A", 90, 10, "Lab ISTA", "G1"),
        Fixtures.lesson("2", "A", 90, 10, "MISSING-FEATURE", "G1"),
        Fixtures.lesson("3", "A", 90, 60, "Sala/anfiteatro aulas", "G1"),
        Fixtures.lesson("4", "A", 90, 10, "Sala/anfiteatro aulas", "G1"));
    var validation = Preflight.inspect(Fixtures.data(lessons, List.of(Fixtures.ROOM)), Fixtures.policy(), Fixtures.config());
    assertEquals(1, validation.report().roomCoverage().get("UNRESOLVED_REQUEST"));
    assertEquals(1, validation.report().roomCoverage().get("NO_FEATURE_ROOM"));
    assertEquals(1, validation.report().roomCoverage().get("INSUFFICIENT_CAPACITY"));
    assertEquals(1, validation.report().roomCoverage().get("AVAILABLE"));
    assertFalse(validation.report().fullyEvaluable());
    assertThrows(IllegalStateException.class, () -> new TimetableProblem(Fixtures.data(lessons, List.of(Fixtures.ROOM)), Fixtures.config(), validation));
  }
  @Test void calendarClipsWeeksHorizonAndClosuresWhilePreservingDuration() throws Exception {
    var config = Fixtures.change(Fixtures.change(Fixtures.change(Fixtures.config(), "horizonStart", "2026-11-04"),
        "horizonEnd", "2026-11-05"), "closedDates", List.of("2026-11-04"));
    var data = Fixtures.data(List.of(Fixtures.lesson("1", "A", 90, "G1")), List.of(Fixtures.ROOM));
    var validation = Preflight.inspect(data, Fixtures.policy(), config);
    assertTrue(validation.report().fullyEvaluable());
    assertEquals(List.of(LocalDate.of(2026, 11, 5)), validation.domains().getFirst().dates());
    assertEquals(27, validation.domains().getFirst().lastStartIndex());
    var problem = new TimetableProblem(data, config, validation);
    var solution = problem.createSolution(); solution.variables().set(1, 27);
    var assigned = problem.decode(solution).getFirst();
    assertEquals(1290, assigned.start()); assertEquals(1380, assigned.end());
    ResultExporter.validateLocal(problem, Fixtures.policy(), List.of(assigned));
  }
  @Test void emptyProfileIsNotSilentlyReplacedAndMissingDataIsRetained() throws Exception {
    var lesson = new Lesson("1", 2, "S", "A", "A1", Set.of(), 0, null, 90, "", "");
    var validation = Preflight.inspect(Fixtures.data(List.of(lesson), List.of(Fixtures.ROOM)), Fixtures.policy(), Fixtures.config());
    assertEquals(1, validation.report().occurrences().size());
    assertTrue(validation.report().occurrences().getFirst().blockers().contains("MISSING_DATE"));
    assertTrue(validation.report().occurrences().getFirst().blockers().contains("MISSING_CLASS"));
    assertEquals("UNRESOLVED_REQUEST", validation.report().occurrences().getFirst().roomStatus());
    assertThrows(IllegalStateException.class, () -> Fixtures.config().preferred(Fixtures.lesson("x", "A", 90, "UNMAPPED"), 480, 570));
  }
  @Test void occurrenceOperatorsPreserveLocalDomainsAndSourceCoverage() throws Exception {
    var data = Fixtures.demo(); var config = Fixtures.config(); var policy = Fixtures.policy();
    var problem = new TimetableProblem(data, config, Preflight.inspect(data, policy, config));
    JMetalRandom.getInstance().setSeed(51);
    var first = problem.createSolution(); var second = problem.createSolution();
    var originalFirst = List.copyOf(first.variables());
    var children = new IntegerSBXCrossover(1, 20).execute(List.of(first, second));
    var mutation = new IntegerPolynomialMutation(1.0 / problem.numberOfVariables(), 20);
    for (int repeat = 0; repeat < 100; repeat++) for (var child : children) {
      mutation.execute(child);
      var schedule = problem.decode(child);
      assertEquals(data.lessons().size(), schedule.size());
      ResultExporter.validateLocal(problem, policy, schedule);
    }
    assertEquals(originalFirst, first.variables());
  }
  @Test void pureParetoAllowsTradeoffBetweenHardAndSoftObjectives() throws Exception {
    var data = Fixtures.demo(); var config = Fixtures.config(); var policy = Fixtures.policy();
    var problem = new TimetableProblem(data, config, Preflight.inspect(data, policy, config));
    var conflictFree = problem.createSolution(); var conflicting = problem.createSolution();
    conflictFree.objectives()[0] = 0; conflictFree.objectives()[1] = 1;
    conflicting.objectives()[0] = 1; conflicting.objectives()[1] = 0;
    assertEquals(0, new DefaultDominanceComparator<>().compare(conflictFree, conflicting));
    assertEquals(0, conflicting.constraints().length);
  }
  @Test void exportValidationRejectsWrongWeekAndFictitiousCapacity() throws Exception {
    var config = Fixtures.config(); var policy = Fixtures.policy();
    var data = Fixtures.data(List.of(Fixtures.lesson("1", "A", 90, "G1")), List.of(Fixtures.ROOM));
    var problem = new TimetableProblem(data, config, Preflight.inspect(data, policy, config));
    assertThrows(IllegalStateException.class, () -> ResultExporter.validateLocal(problem, policy,
        List.of(new Assignment(0, Fixtures.MONDAY.plusWeeks(1), 480, 570, Fixtures.ROOM))));
    var forged = new Room(Fixtures.ROOM.id(), Fixtures.ROOM.building(), 500, Fixtures.ROOM.features());
    assertThrows(IllegalStateException.class, () -> ResultExporter.validateLocal(problem, policy, List.of(Fixtures.at(0, 480, 90, forged))));
  }
}
