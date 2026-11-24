package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.util.comparator.dominanceComparator.impl.DominanceWithConstraintsComparator;

/** Verifica os objetivos e as regras do modelo atual, que decide apenas as salas. */
class TimetableProblemTest {
  /** Nesta branch, os pesos alteram o objetivo soft durante a procura, sem alterar as violações hard. */
  @Test void pesosDaConfiguracaoInfluenciamObjetivoSoft() {
    var data = Fixtures.data(List.of(Fixtures.lesson("A", 480, 90, 30, Fixtures.NORMAL),
        Fixtures.lesson("B", 510, 90, 40, Fixtures.LAB)), List.of(Fixtures.ROOM));
    var base = Fixtures.problem(data);
    var first = base.createSolution();
    base.evaluate(first);
    var config = Fixtures.change(Fixtures.config(), "softWeights", java.util.Map.of("type", 2.0, "seats", 1.0));
    var weighted = new TimetableProblem(data, config, Preflight.inspect(data, Fixtures.policy(), config));
    var second = weighted.createSolution();
    weighted.evaluate(second);
    assertEquals(1.5, first.objectives()[1], 1e-12);
    assertEquals(2.0, second.objectives()[1], 1e-12);
    assertEquals(first.objectives()[0], second.objectives()[0]);
    assertEquals(first.constraints()[0], second.constraints()[0]);
  }

  /** Confirma os dois objetivos num horário com um choque e um tipo errado. */
  @Test void avaliaDoisObjetivosERestricaoHard() {
    var data = Fixtures.data(List.of(Fixtures.lesson("A", 480, 90, 30, Fixtures.NORMAL),
        Fixtures.lesson("B", 510, 90, 40, Fixtures.LAB)), List.of(Fixtures.ROOM));
    var problem = Fixtures.problem(data);
    var solution = problem.createSolution();
    problem.evaluate(solution);
    assertArrayEquals(new double[]{1, 1.5}, solution.objectives(), 1e-12);
    assertEquals(-1, solution.constraints()[0]);
    var valid = solution.copy();
    valid.constraints()[0] = 0;
    valid.objectives()[0] = 0;
    valid.objectives()[1] = 1000;
    assertTrue(new DominanceWithConstraintsComparator<>().compare(valid, solution) < 0);
  }

  /** Conta pares de conflito e aceita aulas consecutivas sem sobreposição. */
  @Test void contaParesSemConfundirAulasConsecutivas() {
    var overlaps = List.of(Fixtures.at(0, 480, 90, Fixtures.ROOM),
        Fixtures.at(1, 510, 60, Fixtures.ROOM), Fixtures.at(2, 540, 30, Fixtures.ROOM));
    var lessons = List.of(Fixtures.lesson("A", 480, 90, 30, Fixtures.NORMAL),
        Fixtures.lesson("B", 510, 60, 30, Fixtures.NORMAL),
        Fixtures.lesson("C", 540, 30, 30, Fixtures.NORMAL));
    assertEquals(3, TimetableProblem.roomMetrics(lessons, overlaps).roomConflictPairs());
    assertEquals(3, TimetableProblem.roomConflictLessons(overlaps).size());
    assertEquals(0, TimetableProblem.roomMetrics(lessons.subList(0, 2),
        List.of(Fixtures.at(0, 480, 90, Fixtures.ROOM), Fixtures.at(1, 570, 60, Fixtures.ROOM)))
        .roomConflictPairs());
  }

  /** Não transforma uma atividade sem sala numa falta de atribuição. */
  @Test void preservaSemSalaEAtribuiSalaComZeroInscritos() {
    var problem = Fixtures.problem(Fixtures.smallData());
    var solution = problem.createSolution();
    problem.evaluate(solution);
    var assignments = problem.decode(solution);
    assertNotNull(assignments.get(2).room());
    assertNull(assignments.get(3).room());
    assertEquals(0, solution.objectives()[0]);
    for (var assignment : assignments) {
      var lesson = problem.data().lessons().get(assignment.lessonIndex());
      assertEquals(lesson.originalDate(), assignment.date());
      assertEquals(lesson.start(), assignment.start());
      assertEquals(lesson.end(), assignment.end());
    }
  }
}
