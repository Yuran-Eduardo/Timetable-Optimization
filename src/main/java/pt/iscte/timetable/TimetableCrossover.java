package pt.iscte.timetable;

import java.util.List;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

/** Aplica o cruzamento inteiro do jMetal e tenta corrigir os choques nos descendentes. */
public class TimetableCrossover extends IntegerSBXCrossover {
    private final TimetableProblem problem;

    /** Configura o cruzamento e guarda o problema usado na correção dos descendentes. */
    public TimetableCrossover(double crossoverProbability, double distributionIndex, TimetableProblem problem) {
        super(crossoverProbability, distributionIndex);
        this.problem = problem;
    }

    /** Cruza os progenitores e corrige os descendentes antes de os devolver. */
    @Override public List<IntegerSolution> execute(List<IntegerSolution> parents) {
        List<IntegerSolution> children = super.execute(parents); // Cruzamento nativo do jMetal.
        for (IntegerSolution child : children) problem.repair(child); // Tenta resolver os choques de sala.
        return children;
    }
}
