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
import org.uma.jmetal.qualityindicator.impl.Epsilon;
import org.uma.jmetal.qualityindicator.impl.GenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.util.comparator.dominanceComparator.impl.DominanceWithConstraintsComparator;
import pt.iscte.timetable.DataSet;
import pt.iscte.timetable.Preflight;
import pt.iscte.timetable.ResultExporter;
import pt.iscte.timetable.RoomPolicy;
import pt.iscte.timetable.StudyConfig;
import pt.iscte.timetable.StudyInput;
import pt.iscte.timetable.TimetableCrossover;
import pt.iscte.timetable.TimetableMutation;
import pt.iscte.timetable.TimetableProblem;

/** Configura e executa o estudo de atribuição de salas pela infraestrutura experimental do jMetal. */
public final class NSGAIIStudy {
  /** Impede a criação de instâncias desta classe utilitária. */
  private NSGAIIStudy() {}

  /** Lê o cenário, exporta a validação e executa o estudo quando a configuração o permite. */
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

  /** Executa a pipeline nativa de experiências e exporta os horários e indicadores. */
  public static Experiment<IntegerSolution, List<IntegerSolution>> execute(DataSet data,
                                                                           RoomPolicy policy, StudyConfig config, Preflight.Result validation, Path output) throws IOException {
    validation.requireRunnable(config);
    List<ExperimentProblem<IntegerSolution>> problemList = new ArrayList<>();
    for (int size : config.problemSizes()) {
      // Seleciona as primeiras N aulas na ordem do CSV, sem filtragem implícita.
      TimetableProblem problem = new TimetableProblem(size, data, config, policy);
      problemList.add(new ExperimentProblem<>(problem).setReferenceFront(problem.name() + ".csv"));
      System.out.printf("%s: %d aulas, %d variáveis, 2 objetivos, 1 constraint.%n",
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
                    // Evita Spread e NHV, que podem ficar indefinidos com objetivos constantes ou duplicados.
                    .setIndicatorList(List.of(new Epsilon(), new GenerationalDistance(), new PISAHypervolume(),
                            new InvertedGenerationalDistance(), new InvertedGenerationalDistancePlus()))
                    .setIndependentRuns(config.independentRuns())
                    .setNumberOfCores(config.numberOfCores())
                    .build();

    new ExecuteAlgorithms<>(experiment).run();
    // Constrói uma frente de referência empírica a partir das soluções obtidas.
    new GenerateReferenceParetoFront(experiment).run();
    new ComputeQualityIndicators<>(experiment).run();
    new GenerateLatexTablesWithStatistics(experiment).run();
    new GenerateWilcoxonTestTablesWithR<>(experiment).run();
    new GenerateFriedmanTestTables<>(experiment).run();
    new GenerateBoxplotsWithR<>(experiment).setRows(2).setColumns(3).run();

    // Exporta os resultados nativos para o formato de horário, sem nova otimização.
    ResultExporter.exportNativeSchedules(output, experiment, policy);
    Files.writeString(output.resolve("LEIA-ME.md"),
            "# Estudo nativo jMetal\n\n"
                    + "Modelo: só salas (dia e hora do CSV). Dois objetivos e uma restrição. FUN = [choques de sala + aulas sem sala, tipo errado + lugares vazios].\n\n"
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

  /** Prepara a variante NSGAIIa para cada problema e execução independente. */
  public static List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> configureAlgorithmList(
          List<ExperimentProblem<IntegerSolution>> problems, StudyConfig config) {
    List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> algorithms = new ArrayList<>();
    for (int run = 0; run < config.independentRuns(); run++) {
      for (var problem : problems) {
        nsgaIIa(algorithms, run, problem, config);
      }
    }
    return algorithms;
  }

  /** Configura uma execução do NSGA-II com operadores inteiros e comparação por restrições. */
  private static void nsgaIIa(List<ExperimentAlgorithm<IntegerSolution, List<IntegerSolution>>> algorithms,
                              int run, ExperimentProblem<IntegerSolution> problem, StudyConfig config) {
    TimetableProblem timetable = (TimetableProblem) problem.getProblem();
    // Usa SBX inteiro (1, 5) e mutação polinomial (1/N, 10) com correção de salas.
    // Os operadores tentam corrigir os choques antes da avaliação.
    Algorithm<List<IntegerSolution>> algorithm = new NSGAIIBuilder<>(timetable,
            new TimetableCrossover(1.0, 5, timetable), new TimetableMutation(
            1.0 / timetable.numberOfVariables(), 10.0, timetable), config.populationSize())
            // Compara primeiro as violações hard e depois a dominância dos objetivos.
            .setDominanceComparator(new DominanceWithConstraintsComparator<>())
            .setMaxEvaluations(config.maxEvaluations()).build();
    algorithms.add(new ExperimentAlgorithm<>(algorithm, "NSGAIIa", problem, run));
  }
}
