package pt.iscte.timetable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Reads experiment inputs and diagnoses them; contains no algorithm configuration. */
public record StudyInput(StudyConfig config, DataSet data, RoomPolicy policy,
    Preflight.Result validation, Path output, Path configFile, Path policyFile,
    boolean validationOnly) {

  public static StudyInput load(String[] args) throws IOException {
    // Argument files avoid Windows cmd.exe ambiguity for paths with spaces/apostrophes.
    if (args.length == 1 && args[0].startsWith("--arguments-file="))
      args = JsonFiles.MAPPER.readValue(Path.of(args[0].substring(17)).toFile(), String[].class);
    boolean demo = false, realExample = false, validationOnly = false;
    Path configuration = null, output = null;
    for (String arg : args) {
      if (arg.equals("--demo")) demo = true;
      else if (arg.equals("--real-example")) realExample = true;
      else if (arg.equals("--validate-only")) validationOnly = true;
      else if (arg.startsWith("--config=")) configuration = Path.of(arg.substring(9)).toAbsolutePath();
      else if (arg.startsWith("--output=")) output = Path.of(arg.substring(9)).toAbsolutePath();
      else if (!arg.startsWith("--") && output == null) output = Path.of(arg).toAbsolutePath();
      else throw new IllegalArgumentException("Unknown argument: " + arg);
    }
    Path project = locateProject();
    if (demo && realExample) throw new IllegalArgumentException("Choose --demo or --real-example");
    if (realExample && configuration != null) throw new IllegalArgumentException("Use --real-example or --config");
    if (configuration == null) configuration = project.resolve("config/" +
        (demo ? "demo.json" : realExample ? "real-example.json" : "full.json"));
    StudyConfig config = JsonFiles.MAPPER.readValue(configuration.toFile(), StudyConfig.class);
    config.validate();
    if (demo && !config.synthetic()) throw new IllegalArgumentException("--demo requires a synthetic configuration");
    Path base = configuration.getParent();
    Path policyFile = base.resolve(config.policyFile()).normalize();
    RoomPolicy policy = RoomPolicy.read(policyFile);
    DataSet data = CsvDataLoader.load(base.resolve(config.scheduleFile()).normalize(), base.resolve(config.roomsFile()).normalize());
    if (output == null) output = project.resolve("Resultados").resolve(config.synthetic() ? "sinteticos" : "reais")
        .resolve(config.scenario().replaceAll("[^\\p{L}\\p{N}._-]", "_"))
        .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")));
    return new StudyInput(config, data, policy, Preflight.inspect(data, policy, config), output,
        configuration, policyFile, validationOnly);
  }

  public static Path locateProject() {
    for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
      if (Files.isRegularFile(root.resolve("config/real.json"))) return root;
      if (Files.isRegularFile(root.resolve("Timetable-Optimization/config/real.json"))) return root.resolve("Timetable-Optimization");
    }
    throw new IllegalStateException("Use the project directory as the working directory");
  }
}
