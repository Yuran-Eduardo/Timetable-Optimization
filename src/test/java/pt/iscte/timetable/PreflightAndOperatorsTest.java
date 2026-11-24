package pt.iscte.timetable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifica os domínios de salas e a correção usada pelos operadores. */
class PreflightAndOperatorsTest {
  /** Cada aula tem um gene, com limites definidos pela respetiva lista de salas. */
  @Test void declaraUmGenePorAulaDoisObjetivosEUmaRestricao() {
    var data = Fixtures.smallData();
    var validation = Preflight.inspect(data, Fixtures.policy(), Fixtures.config());
    var problem = Fixtures.problem(data);
    var solution = problem.createSolution();
    assertEquals(4, problem.numberOfVariables());
    assertEquals(2, solution.objectives().length);
    assertEquals(1, solution.constraints().length);
    for (int i = 0; i < 4; i++) {
      assertEquals(0, problem.variableBounds().get(i).getLowerBound());
      assertEquals(Math.max(0, validation.domains().get(i).rooms().size() - 1),
          problem.variableBounds().get(i).getUpperBound());
    }
    assertThrows(IllegalArgumentException.class,
        () -> new TimetableProblem(5, data, Fixtures.config(), Fixtures.policy()));
  }

  /** Capacidade e espaço letivo são obrigatórios, mas o tipo pedido é uma preferência. */
  @Test void excluiSalasPequenasEEspacosNaoLetivosMasPermiteOutroTipo() {
    var normal = new Room("N", "TESTE", 60, Set.of(Fixtures.NORMAL));
    var other = new Room("L", "TESTE", 40, Set.of(Fixtures.LAB));
    var small = new Room("P", "TESTE", 20, Set.of(Fixtures.NORMAL));
    var meeting = new Room("M", "TESTE", 100, Set.of("sala_reuniao"));
    var data = Fixtures.data(List.of(Fixtures.lesson("A", 480, 90, 30, Fixtures.NORMAL)),
        List.of(other, small, meeting, normal));
    var validation = Preflight.inspect(data, Fixtures.policy(), Fixtures.config());
    assertEquals(List.of(normal, other), validation.domains().getFirst().rooms());
    var problem = Fixtures.problem(data);
    var solution = problem.createSolution();
    solution.variables().set(0, 1);
    problem.evaluate(solution);
    assertArrayEquals(new double[]{0, 1.0 + 10.0 / 30.0}, solution.objectives(), 1e-12);
    ResultExporter.validateLocal(problem, Fixtures.policy(), problem.decode(solution));
  }

  /** Distingue um pedido pendente de uma falta de capacidade e penaliza ambos como falta de sala. */
  @Test void identificaPedidosPendentesECapacidadeInsuficiente() {
    var data = Fixtures.data(List.of(Fixtures.lesson("A", 480, 90, 30, "lab_pendente"),
        Fixtures.lesson("B", 570, 90, 60, Fixtures.NORMAL)), List.of(Fixtures.ROOM));
    var config = Fixtures.config();
    var validation = Preflight.inspect(data, Fixtures.policy(), config);
    assertEquals("UNRESOLVED_REQUEST", validation.report().occurrences().get(0).roomStatus());
    assertEquals("INSUFFICIENT_CAPACITY", validation.report().occurrences().get(1).roomStatus());
    assertFalse(validation.report().jointlyFeasibleProven());
    var problem = new TimetableProblem(data, config, validation);
    var solution = problem.createSolution();
    problem.evaluate(solution);
    assertArrayEquals(new double[]{2, 0}, solution.objectives(), 1e-12);
    assertEquals(-2, solution.constraints()[0]);
    assertTrue(problem.decode(solution).stream().allMatch(a -> a.room() == null));
    var strict = Fixtures.change(config, "allowIncompleteData", false);
    assertThrows(IllegalStateException.class,
        () -> new TimetableProblem(data, strict, Preflight.inspect(data, Fixtures.policy(), strict)));
  }

  /** Uma sala livre resolve o choque; cruzamento e mutação preservam os domínios e horários. */
  @Test void corrigeChoquesNosOperadoresSemAlterarProgenitores() {
    var secondRoom = new Room("R2", "TESTE", 50, Set.of(Fixtures.NORMAL));
    var data = Fixtures.data(List.of(Fixtures.lesson("A", 480, 90, 30, Fixtures.NORMAL),
        Fixtures.lesson("B", 480, 90, 30, Fixtures.NORMAL)), List.of(Fixtures.ROOM, secondRoom));
    var problem = Fixtures.problem(data);
    var first = problem.createSolution();
    first.variables().set(0, 0);
    first.variables().set(1, 0);
    problem.evaluate(first);
    assertEquals(1, first.objectives()[0]);
    problem.repair(first);
    problem.evaluate(first);
    assertEquals(0, first.objectives()[0]);
    var second = problem.createSolution();
    var savedFirst = List.copyOf(first.variables());
    var savedSecond = List.copyOf(second.variables());
    var children = new TimetableCrossover(1, 5, problem).execute(List.of(first, second));
    for (var child : children) {
      new TimetableMutation(1, 10, problem).execute(child);
      problem.evaluate(child);
      assertEquals(0, child.objectives()[0]);
      ResultExporter.validateLocal(problem, Fixtures.policy(), problem.decode(child));
    }
    assertEquals(savedFirst, first.variables());
    assertEquals(savedSecond, second.variables());
  }

  /** Sem alternativa livre, a correção termina e o choque continua explicitamente penalizado. */
  @Test void naoOcultaChoqueQuandoNaoExisteSalaLivre() {
    var data = Fixtures.data(List.of(Fixtures.lesson("A", 480, 90, 30, Fixtures.NORMAL),
        Fixtures.lesson("B", 480, 90, 30, Fixtures.NORMAL)), List.of(Fixtures.ROOM));
    var problem = Fixtures.problem(data);
    var solution = problem.createSolution();
    problem.repair(solution);
    problem.evaluate(solution);
    assertEquals(1, solution.objectives()[0]);
    assertEquals(-1, solution.constraints()[0]);
    assertEquals(2, problem.decode(solution).size());
  }
}
