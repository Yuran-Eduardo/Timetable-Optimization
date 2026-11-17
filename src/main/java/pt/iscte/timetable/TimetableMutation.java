package pt.iscte.timetable;

import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

/** jMetal's IntegerPolynomialMutation followed by the same repair as the crossover:
 *  a mutation that moves a lesson into an occupied room is fixed straight away. */
public class TimetableMutation extends IntegerPolynomialMutation {
    private final TimetableProblem problem;

    public TimetableMutation(double mutationProbability, double distributionIndex, TimetableProblem problem) {
        super(mutationProbability, distributionIndex);
        this.problem = problem;
    }

    @Override public IntegerSolution execute(IntegerSolution solution) {
        IntegerSolution mutated = super.execute(solution); // normal jMetal mutation
        problem.repair(mutated); // remove room clashes
        return mutated;
    }
}
