package pt.iscte.timetable;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only approved mappings participate. Historical assignments cannot add compatibility. */
public final class RoomPolicy {
  private final Map<String, Set<String>> approvedAlternatives;
  private final Set<String> pending;

  public RoomPolicy(Map<String, Set<String>> approvedAlternatives, Set<String> pending) {
    this.approvedAlternatives = new HashMap<>();
    approvedAlternatives.forEach((key, value) -> this.approvedAlternatives.put(key, Set.copyOf(value)));
    this.pending = Set.copyOf(pending);
  }
  public static RoomPolicy read(Path path) throws IOException {
    JsonNode root = JsonFiles.MAPPER.readTree(path.toFile());
    if (!root.path("capacidade_suficiente_obrigatoria").asBoolean()
        || root.path("sala_original_restringe_dominio").asBoolean()
        || !root.path("camada_correcoes").isArray() || !root.path("camada_correcoes").isEmpty()
        || !root.path("cenarios_experimentais_ativos").isArray()
        || !root.path("cenarios_experimentais_ativos").isEmpty())
      throw new IllegalArgumentException("Only the original strict room scenario is supported");
    Map<String, Set<String>> aliases = new HashMap<>();
    for (JsonNode rule : root.path("correspondencias_aprovadas")) {
      if (!rule.path("estado").asText().equals("aprovada_pelo_grupo")) continue;
      if (!rule.path("operador").asText().equals("OU"))
        throw new IllegalArgumentException("Unsupported compatibility operator");
      Set<String> features = new HashSet<>();
      rule.path("caracteristicas").forEach(n -> features.add(n.asText()));
      if (features.isEmpty() || aliases.put(rule.path("pedido").asText(), features) != null)
        throw new IllegalArgumentException("Empty or duplicate approved mapping");
    }
    Set<String> pending = new HashSet<>();
    root.path("pedidos_pendentes").forEach(n -> pending.add(n.path("pedido").asText()));
    return new RoomPolicy(aliases, pending);
  }
  public boolean resolved(String request, Set<String> columns) {
    return approvedAlternatives.containsKey(request) || !pending.contains(request) && columns.contains(request);
  }
  public boolean compatible(String request, Room room) {
    var alternatives = approvedAlternatives.get(request);
    return alternatives == null ? !pending.contains(request) && room.features().contains(request)
        : alternatives.stream().anyMatch(room.features()::contains);
  }
  public List<Room> byFeature(String request, List<Room> rooms) {
    return rooms.stream().filter(room -> compatible(request, room)).toList();
  }
}
