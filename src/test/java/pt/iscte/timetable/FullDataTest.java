package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifica o cenário completo sem executar milhares de avaliações evolutivas. */
class FullDataTest {
  @TempDir Path temporary;

  /** Lê, cria e avalia uma solução com todas as 26 019 ocorrências dos dados tratados. */
  @Test void preservaTodasAsOcorrenciasDoHorarioCompleto() throws Exception {
    var input = StudyInput.load(new String[]{"--output=" + temporary.resolve("completo")});
    assertEquals(26019, input.data().lessons().size());
    assertEquals(132, input.data().rooms().size());
    assertTrue(input.data().inputErrors().isEmpty());
    assertEquals(List.of(26019), input.config().problemSizes());
    assertTrue(input.validation().runnable(input.config()));
    assertEquals(24755, input.validation().report().roomCoverage().get("AVAILABLE"));
    assertEquals(1264, input.validation().report().roomCoverage().get("NO_PHYSICAL_ROOM"));
    for (int objectives : List.of(2, 4)) {
      var problem = new TimetableProblem(input.data(), input.config(), input.validation(), objectives);
      var solution = problem.createSolution();
      problem.evaluate(solution);
      assertEquals(26019, solution.variables().size());
      assertEquals(objectives, solution.objectives().length);
      assertEquals(1, solution.constraints().length);
      for (double objective : solution.objectives()) assertTrue(Double.isFinite(objective));
      var assignments = problem.decode(solution);
      assertEquals(26019, assignments.size());
      assertEquals(26019, assignments.stream().map(Assignment::lessonIndex).distinct().count());
      assertEquals(24755, assignments.stream().filter(a -> a.room() != null).count());
      for (var assignment : assignments) {
        var lesson = input.data().lessons().get(assignment.lessonIndex());
        assertEquals(lesson.originalDate(), assignment.date());
        assertEquals(lesson.start(), assignment.start());
        assertEquals(lesson.end(), assignment.end());
      }
      ResultExporter.validateLocal(problem, input.policy(), assignments);
      assertEquals(0, solution.objectives()[0]);
      if (objectives == 4) assertEquals(0, solution.objectives()[1]);
      else assertTrue(solution.objectives()[1] > 0);
      assertEquals(0, solution.constraints()[0], 0.0);
    }
  }
}
