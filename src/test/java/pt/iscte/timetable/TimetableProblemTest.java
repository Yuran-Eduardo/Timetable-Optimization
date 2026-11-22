package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.util.comparator.dominanceComparator.impl.DominanceWithConstraintsComparator;

/** Verifica os objetivos e as regras do modelo atual, que decide apenas as salas. */
class TimetableProblemTest {
  /** Confirma os valores das quatro regras num horário com um choque e um tipo errado. */
  @Test void avaliaQuatroObjetivosERestricaoHard() {
    var data = Fixtures.data(List.of(Fixtures.lesson("A", 480, 90, 30, Fixtures.NORMAL),
        Fixtures.lesson("B", 510, 90, 40, Fixtures.LAB)), List.of(Fixtures.ROOM));
    var problem = Fixtures.problem(data);
    var solution = problem.createSolution();
    problem.evaluate(solution);
    assertArrayEquals(new double[]{1, 0, 1, 30}, solution.objectives(), 1e-12);
    assertEquals(-1, solution.constraints()[0]);
    var valid = solution.copy();
    valid.constraints()[0] = 0;
    valid.objectives()[2] = 1000;
    valid.objectives()[3] = 1000;
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
    assertEquals(0, solution.objectives()[1]);
    for (var assignment : assignments) {
      var lesson = problem.data().lessons().get(assignment.lessonIndex());
      assertEquals(lesson.originalDate(), assignment.date());
      assertEquals(lesson.start(), assignment.start());
      assertEquals(lesson.end(), assignment.end());
    }
  }
}
