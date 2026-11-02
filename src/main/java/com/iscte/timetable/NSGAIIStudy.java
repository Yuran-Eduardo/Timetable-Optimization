package com.iscte.timetable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.lab.experiment.Experiment;
import org.uma.jmetal.lab.experiment.ExperimentBuilder;
import org.uma.jmetal.lab.experiment.component.impl.ComputeQualityIndicators;
import org.uma.jmetal.lab.experiment.component.impl.ExecuteAlgorithms;
import org.uma.jmetal.lab.experiment.component.impl.GenerateBoxplotsWithR;
import org.uma.jmetal.lab.experiment.component.impl.GenerateFriedmanTestTables;
import org.uma.jmetal.lab.experiment.component.impl.GenerateLatexTablesWithStatistics;
import org.uma.jmetal.lab.experiment.component.impl.GenerateReferenceParetoFront;
import org.uma.jmetal.lab.experiment.component.impl.GenerateWilcoxonTestTablesWithR;
import org.uma.jmetal.lab.experiment.util.ExperimentAlgorithm;
import org.uma.jmetal.lab.experiment.util.ExperimentProblem;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.qualityindicator.impl.Epsilon;
import org.uma.jmetal.qualityindicator.impl.GenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import pt.iscte.timetable.DataSet;
import pt.iscte.timetable.Preflight;
import pt.iscte.timetable.ResultExporter;
import pt.iscte.timetable.RoomPolicy;
import pt.iscte.timetable.StudyConfig;
import pt.iscte.timetable.StudyInput;
import pt.iscte.timetable.TimetableProblem;

/** The professor's native experimental pipeline, with integer timetable problems. */
public final class NSGAIIStudy {
  private NSGAIIStudy() {}

  public static void main(String[] args) throws IOException {
    StudyInput input = StudyInput.load(args);
    ResultExporter.exportValidation(input);
    var report = input.validation().report();
    System.out.println("Cenário " + report.scenario() + ": " + report.lessons()
        + " ocorrências, " + report.catalogRooms() + " salas.");
    System.out.println("Cobertura: " + report.roomCoverage());
    System.out.println("Resultados: " + input.output());
    if (input.validationOnly() || !input.validation().runnable(input.config())) {
      System.out.println(input.validationOnly() ? "Validação concluída."
          : "NSGA-II não executado: consulte validation.json.");
      return;
    }
    if (!report.fullyEvaluable())
      System.out.println("Execução diagnóstica completa: dados pendentes permanecem identificados no CSV e no objetivo 0.");
    execute(input.data(), input.policy(), input.config(), input.validation(), input.output());
    ResultExporter.publishLatest(input.output(), StudyInput.locateProject());
  }

  public static Experiment<IntegerSolution, List<IntegerSolution>> execute(DataSet data,
      RoomPolicy policy, StudyConfig config, Preflight.Result validation, Path output) throws IOException {
    validation.requireRunnable(config);
    List<ExperimentProblem<IntegerSolution>> problemList = new ArrayList<>();
    for (int size : config.problemSizes()) {
      // Explicit nested subsets in source CSV order, never fabricated or silently filtered.
      TimetableProblem problem = new TimetableProblem(size, data, config, policy);
      problemList.add(new ExperimentProblem<IntegerSolution>(problem).setReferenceFront(problem.name() + ".csv"));
      System.out.printf("%s: %d aulas, %d variáveis, 2 objetivos, 0 constraints.%n",
          problem.name(), size, problem.numberOfVariables());
    }
    List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> algorithmList =
        configureAlgorithmList(problemList, config);

    Experiment<IntegerSolution, List<IntegerSolution>> experiment =
        new ExperimentBuilder<IntegerSolution, List<IntegerSolution>>("NSGAIIStudy")
            .setAlgorithmList(algorithmList)
            .setProblemList(problemList)
            .setExperimentBaseDirectory(output.toString())
            .setOutputParetoFrontFileName("FUN")
            .setOutputParetoSetFileName("VAR")
            .setReferenceFrontDirectory(output.resolve("NSGAIIStudy/referenceFronts").toString())
            // Discrete duplicates can make Spread undefined; NHV divides by reference HV, possibly zero.
            .setIndicatorList(List.of(new Epsilon(), new GenerationalDistance(), new PISAHypervolume(),
                new InvertedGenerationalDistance(), new InvertedGenerationalDistancePlus()))
            .setIndependentRuns(config.independentRuns())
            .setNumberOfCores(config.numberOfCores())
            .build();

    new ExecuteAlgorithms<>(experiment).run();
    // Native component for an unknown real Pareto front; this is an empirical reference.
    new GenerateReferenceParetoFront(experiment).run();
    new ComputeQualityIndicators<>(experiment).run();
    new GenerateLatexTablesWithStatistics(experiment).run();
    new GenerateWilcoxonTestTablesWithR<>(experiment).run();
    new GenerateFriedmanTestTables<>(experiment).run();
    new GenerateBoxplotsWithR<>(experiment).setRows(2).setColumns(3).run();

    // Only decode the native FUN/VAR files for the timetable user; never run an algorithm here.
    ResultExporter.exportNativeSchedules(output, experiment, policy);
    Files.writeString(output.resolve("LEIA-ME.md"),
        "# Estudo nativo jMetal\n\n"
        + "Modelo: dois objetivos, sem vetor de restrições. FUN = [violações rígidas, penalização flexível].\n\n"
        + "O CSV horario-exemplo.csv representa o menor objetivo 0, seguido do menor objetivo 1, "
        + "entre as alternativas exportadas do maior problema. Pode conter violações; não é declarado ótimo. "
        + "O modelo usa os recursos identificáveis nos dados: salas e turmas.\n\n"
        + "Resultados nativos: NSGAIIStudy/data/ (FUN, VAR e indicadores), "
        + "NSGAIIStudy/latex/, NSGAIIStudy/R/ e NSGAIIStudy/referenceFronts/. "
        + "As referências são empíricas, obtidas das execuções; não são frentes ótimas conhecidas.\n\n"
        + "Métricas de todas as alternativas: horarios/. CSV representativo: horario-exemplo.csv. "
        + "Relatório de seleção dos subproblemas: problem-instances.json.\n\n"
        + "Os componentes WithR geram scripts; executar Render-Reports.ps1 para obter boxplots "
        + "e tabelas Wilcoxon com R instalado.\n");
    return experiment;
  }

  /** Same four NSGA-II variants and loops as the supplied study; native integer operators. */
  public static List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> configureAlgorithmList(
      List<ExperimentProblem<IntegerSolution>> problems, StudyConfig config) {
    List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> algorithms = new ArrayList<>();
    for (int run = 0; run < config.independentRuns(); run++) {
      for (var problem : problems) {
        nsgaIIa(algorithms, run, problem, config);
        nsgaIIb(algorithms, run, problem, config);
        nsgaIIc(algorithms, run, problem, config);
        nsgaIId(algorithms, run, problem, config);
      }
    }
    return algorithms;
  }

  private static void nsgaIIa(List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> algorithms,
      int run, ExperimentProblem<IntegerSolution> problem, StudyConfig config) {
    Algorithm<List<IntegerSolution>> algorithm = new NSGAIIBuilder<>(problem.getProblem(),
        new IntegerSBXCrossover(1.0, 5), new IntegerPolynomialMutation(
            1.0 / problem.getProblem().numberOfVariables(), 10.0), config.populationSize())
        .setMaxEvaluations(config.maxEvaluations()).build();
    algorithms.add(new ExperimentAlgorithm<>(algorithm, "NSGAIIa", problem, run));
  }

  private static void nsgaIIb(List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> algorithms,
      int run, ExperimentProblem<IntegerSolution> problem, StudyConfig config) {
    Algorithm<List<IntegerSolution>> algorithm = new NSGAIIBuilder<>(problem.getProblem(),
        new IntegerSBXCrossover(1.0, 20.0), new IntegerPolynomialMutation(
            1.0 / problem.getProblem().numberOfVariables(), 20.0), config.populationSize())
        .setMaxEvaluations(config.maxEvaluations()).build();
    algorithms.add(new ExperimentAlgorithm<>(algorithm, "NSGAIIb", problem, run));
  }

  private static void nsgaIIc(List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> algorithms,
      int run, ExperimentProblem<IntegerSolution> problem, StudyConfig config) {
    Algorithm<List<IntegerSolution>> algorithm = new NSGAIIBuilder<>(problem.getProblem(),
        new IntegerSBXCrossover(1.0, 40.0), new IntegerPolynomialMutation(
            1.0 / problem.getProblem().numberOfVariables(), 40.0), 10)
        .setMaxEvaluations(config.maxEvaluations()).build();
    algorithms.add(new ExperimentAlgorithm<>(algorithm, "NSGAIIc", problem, run));
  }

  private static void nsgaIId(List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> algorithms,
      int run, ExperimentProblem<IntegerSolution> problem, StudyConfig config) {
    Algorithm<List<IntegerSolution>> algorithm = new NSGAIIBuilder<>(problem.getProblem(),
        new IntegerSBXCrossover(1.0, 80.0), new IntegerPolynomialMutation(
            1.0 / problem.getProblem().numberOfVariables(), 80.0), config.populationSize())
        .setMaxEvaluations(config.maxEvaluations()).build();
    algorithms.add(new ExperimentAlgorithm<>(algorithm, "NSGAIId", problem, run));
  }
}
