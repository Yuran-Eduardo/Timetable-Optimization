package pt.iscte.timetable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Reads the complete UTF-8 reference. Every source lesson gets an ID, including invalid rows. */
public final class CsvDataLoader {
  // Nomes das colunas dos ficheiros tratados (data/tratados). Se os cabeçalhos mudarem, basta mudar aqui.
  // salas.csv: as 5 primeiras colunas identificam a sala; as restantes são características (X = tem).
  private static final String ROOM_BUILDING = "edificio", ROOM_ID = "sala", ROOM_CAPACITY = "cap_normal";
  // horario_completo.csv / horario_teste.csv
  private static final String LESSON_ID = "id", COURSE = "curso", UNIT = "uc", SHIFT = "turno", CLASSES = "turma",
          STUDENTS = "inscritos", START = "inicio", END = "fim", DATE = "data",
          REQUESTED_FEATURE = "caracteristica_pedida", ORIGINAL_ROOM = "sala";
  private CsvDataLoader() {}
  public static DataSet load(Path schedule, Path catalog) throws IOException {
    List<String> errors = new ArrayList<>();
    List<Room> rooms = new ArrayList<>();
    Set<String> columns;
    try (var parser = parser(catalog)) {
      List<String> headers = parser.getHeaderNames();
      if (headers.size() < 6) throw new IOException("Missing room feature columns");
      columns = new LinkedHashSet<>(headers.subList(5, headers.size()));
      Set<String> ids = new HashSet<>();
      for (CSVRecord row : parser) {
        String id = row.get(ROOM_ID);
        if (id.isBlank() || !ids.add(id)) errors.add("Duplicate/blank room ID: " + id);
        Set<String> features = new LinkedHashSet<>();
        for (String feature : columns) {
          String flag = row.get(feature).trim();
          if (flag.equals("X")) features.add(feature);
          else if (!flag.isEmpty()) errors.add("Unknown feature flag for " + id + ": " + flag);
        }
        int capacity = number(row.get(ROOM_CAPACITY), "room " + id, errors);
        if (capacity >= 0) rooms.add(new Room(id, row.get(ROOM_BUILDING), capacity, features));
      }
    }
    List<Lesson> lessons = new ArrayList<>();
    try (var parser = parser(schedule)) {
      for (CSVRecord row : parser) {
        int line = Math.toIntExact(row.getRecordNumber() + 1);
        // Usa o id do ficheiro; se estiver vazio, gera um a partir da linha.
        String sourceId = row.get(LESSON_ID).trim();
        String id = sourceId.isEmpty() ? "CSV-%06d".formatted(line) : sourceId;
        LocalDate date = null;
        String rawDate = row.get(DATE);
        if (!rawDate.isBlank()) {
          try { date = LocalDate.parse(rawDate.trim(), DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)); }
          catch (RuntimeException e) { errors.add(id + ": invalid date " + rawDate); }
        }
        int duration = -1, startMinute = -1;
        try {
          int start = LocalTime.parse(row.get(START).trim()).toSecondOfDay();
          int end = LocalTime.parse(row.get(END).trim()).toSecondOfDay();
          if (start % 60 != 0 || end % 60 != 0) throw new IllegalArgumentException("Seconds are not supported");
          duration = (end - start) / 60;
          startMinute = start / 60; // hora de início original, em minutos
        } catch (RuntimeException e) { errors.add(id + ": invalid original time/duration"); }
        Set<String> groups = new LinkedHashSet<>();
        Arrays.stream(row.get(CLASSES).split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(groups::add);
        lessons.add(new Lesson(id, line, row.get(COURSE), row.get(UNIT), row.get(SHIFT),
                groups, number(row.get(STUDENTS), id, errors), date, duration, startMinute,
                row.get(REQUESTED_FEATURE), row.get(ORIGINAL_ROOM)));
      }
    }
    return new DataSet(lessons, rooms, columns, errors,
            Map.of(schedule.toString(), sha256(schedule), catalog.toString(), sha256(catalog)));
  }
  public static String sha256(Path path) throws IOException {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private static int number(String raw, String where, List<String> errors) {
    try {
      int n = Integer.parseInt(raw.trim());
      if (n < 0) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException e) { errors.add(where + ": invalid/missing nonnegative number " + raw); return -1; }
  }
  private static CSVParser parser(Path path) throws IOException {
    BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
    reader.mark(65536);
    String first = reader.readLine();
    if (first != null && first.startsWith("\uFEFF")) first = first.substring(1);
    if (first != null && first.equals("sep=;")) { /* Excel separator line is not a header. */ }
    else {
      reader.reset(); reader.mark(1);
      if (reader.read() != '\uFEFF') reader.reset();
    }
    try { return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(false).build().parse(reader); }
    catch (IOException | RuntimeException e) { reader.close(); throw e; }
  }
}
