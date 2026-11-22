package pt.iscte.timetable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Centraliza a leitura e escrita de JSON com suporte para datas Java. */
public final class JsonFiles {
  public static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .enable(SerializationFeature.INDENT_OUTPUT);
  /** Impede a criação de instâncias desta classe utilitária. */
  private JsonFiles() {}
  /** Cria a pasta de destino e grava um objeto em JSON. */
  public static void write(Path path, Object data) throws IOException {
    Files.createDirectories(path.toAbsolutePath().getParent());
    MAPPER.writeValue(path.toFile(), data);
  }
}
