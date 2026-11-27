package com.iscte.timetable;

import java.awt.Color;
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
import smile.plot.swing.Canvas;
import smile.plot.swing.ScatterPlot;

/** Configura e executa o estudo de atribuição de salas pela infraestrutura experimental do jMetal. */
public final class NSGAIIStudy {
  /** Impede a criação de instâncias desta classe de entrada. */
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
      System.out.println("Execução com avisos: consultar validation.json e os estados das aulas no CSV.");
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
      for (int objectives : List.of(2, 4)) {
        TimetableProblem problem = new TimetableProblem(size, data, config, policy, objectives);
        problemList.add(new ExperimentProblem<>(problem).setReferenceFront(problem.name() + ".csv"));
        System.out.printf("%s: %d aulas, %d variáveis, %d objetivos, 1 restrição.%n",
                problem.name(), size, problem.numberOfVariables(), problem.numberOfObjectives());
      }
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
                    + "Modelo: só salas (dia e hora do CSV). Cada tamanho executa dois problemas, ambos com uma restrição hard. "
                    + "2obj: FUN = [violações hard, penalização soft ponderada]. "
                    + "4obj: FUN = [choques de sala, aulas sem sala, tipo errado, lugares vazios].\n\n"
                    + "Cada problema tem o seu CSV recomendado pelo menor total hard e pela menor penalização soft. "
                    + "As frentes não são misturadas. Os horários podem conter violações; não são declarados ótimos. "
                    + "O algoritmo decide apenas as salas; as métricas de turma são diagnósticos dos horários fixos.\n\n"
                    + "Resultados nativos: NSGAIIStudy/data/ (FUN, VAR e indicadores), "
                    + "NSGAIIStudy/latex/, NSGAIIStudy/R/ e NSGAIIStudy/referenceFronts/. "
                    + "As referências são empíricas, obtidas das execuções; não são frentes ótimas conhecidas.\n\n"
                    + "Métricas de todas as alternativas: horarios/. Todos os CSV com objetivos distintos da frente de cada problema em problemas/<problema>/alternativas/. "
                    + "Comparação completa: alternatives-summary.csv e alternatives.json. Até seis destaques: alternatives-highlights.csv. "
                    + "CSV recomendado por problema: problemas/<problema>/horario-exemplo.csv. "
                    + "Relatório de seleção dos subproblemas: problem-instances.json.\n\n"
                    + "Os componentes WithR geram scripts; executar Render-Reports.ps1 para obter boxplots "
                    + "e tabelas Wilcoxon com R instalado.\n");
    // Mostra uma janela por versão (2 e 4 objetivos), quando existe ecrã.
    if (!java.awt.GraphicsEnvironment.isHeadless()) for (var p : problemList) showParetoFront(p, output, data);
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

  /** Cria o NSGA-II (variante NSGAIIa) para um problema e uma execução independente. */
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

  /**
   * Abre uma janela com a frente de Pareto de uma versão do problema.
   * Os dois gráficos usam os mesmos eixos, para poderem ser comparados:
   * X = aulas em sala de tipo diferente do pedido; Y = total de lugares vazios.
   * Cada ponto é um horário alternativo exportado (sem repetidos nem dominados).
   */
  private static void showParetoFront(ExperimentProblem<IntegerSolution> problem, Path output, DataSet data) {
    try {
      int objectives = problem.getProblem().numberOfObjectives();
      Path summary = output.resolve("problemas").resolve(problem.getTag()).resolve("alternatives-summary.csv");
      if (!Files.isRegularFile(summary)) summary = output.resolve("alternatives-summary.csv");
      double[][] points = readFront(summary);
      if (points.length == 0) return;

      // Vermelho para 2 objetivos, azul para 4 objetivos; '@' desenha círculos cheios.
      Canvas canvas = ScatterPlot.of(points, '@', objectives == 2 ? Color.RED : Color.BLUE).canvas();

      // Margem à volta dos pontos, para nenhum ficar cortado na borda do gráfico.
      double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
      for (double[] q : points) {
        minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]);
        minY = Math.min(minY, q[1]); maxY = Math.max(maxY, q[1]);
      }
      double marginX = Math.max(1, 0.1 * (maxX - minX)), marginY = Math.max(50, 0.1 * (maxY - minY));
      canvas.extendLowerBound(new double[]{minX - marginX, minY - marginY});
      canvas.extendUpperBound(new double[]{maxX + marginX, maxY + marginY});

      canvas.setTitle("Frente de Pareto - " + objectives + " objetivos (" + data.lessons().size() + " aulas, "
              + points.length + (points.length == 1 ? " horário alternativo)" : " horários alternativos)"));
      canvas.setAxisLabels("Aulas em sala de tipo diferente do pedido", "Lugares vazios (total)");
      canvas.window().setTitle("Frente de Pareto - " + objectives + " objetivos");
    } catch (Exception e) {
      System.out.println("Não foi possível abrir o gráfico de " + problem.getTag() + ": " + e.getMessage());
    }
  }

  /** Lê do alternatives-summary.csv o tipo errado (X) e os lugares vazios (Y) de cada alternativa. */
  private static double[][] readFront(Path summary) throws IOException {
    List<String> lines = Files.readAllLines(summary).stream().filter(l -> !l.isBlank()).toList();
    List<String> header = List.of(lines.get(0).replace("\uFEFF", "").split(";"));
    int type = header.indexOf("Tipo_sala_errado"), seats = header.indexOf("Lugares_vazios");
    double[][] points = new double[lines.size() - 1][2];
    for (int i = 1; i < lines.size(); i++) {
      String[] cells = lines.get(i).split(";");
      points[i - 1][0] = Double.parseDouble(cells[type]);
      points[i - 1][1] = Double.parseDouble(cells[seats]);
    }
    return points;
  }
}
