package pt.iscte.timetable;

import java.util.List;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

/** jMetal's IntegerSBXCrossover followed by a repair: children never keep two lessons in the same room
 *  at the same time (professor's suggestion). */
    public class TimetableCrossover extends IntegerSBXCrossover {
    private final TimetableProblem problem;

    public TimetableCrossover(double crossoverProbability, double distributionIndex, TimetableProblem problem) {
        super(crossoverProbability, distributionIndex);
        this.problem = problem;
    }

    @Override public List<IntegerSolution> execute(List<IntegerSolution> parents) {
        List<IntegerSolution> children = super.execute(parents); // normal jMetal crossover
        for (IntegerSolution child : children) problem.repair(child); // remove room clashes
        return children;
    }
}
