package pt.iscte.timetable;

import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

/** Aplica a mutação inteira do jMetal e tenta corrigir os choques na solução alterada. */
public class TimetableMutation extends IntegerPolynomialMutation {
    private final TimetableProblem problem;

    /** Configura a mutação e guarda o problema usado na correção da solução. */
    public TimetableMutation(double mutationProbability, double distributionIndex, TimetableProblem problem) {
        super(mutationProbability, distributionIndex);
        this.problem = problem;
    }

    /** Altera os genes e tenta corrigir os choques da solução resultante. */
    @Override public IntegerSolution execute(IntegerSolution solution) {
        IntegerSolution mutated = super.execute(solution); // Mutação nativa do jMetal.
        problem.repair(mutated); // Tenta resolver os choques de sala.
        return mutated;
    }
}
