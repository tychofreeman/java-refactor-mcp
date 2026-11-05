package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Opt-in: writes every tool's schema and arguments, normalized (keys sorted, required as a sorted
 * list), to {@code -Djrefactor.schemas.out}, so two versions of {@link Tools} can be compared with
 * diff when the schemas are rewritten without meaning to change them.
 */
class ToolSchemasTest {

  @Test
  void writeSchemas() throws Exception {
    String out = System.getProperty("jrefactor.schemas.out");
    assumeTrue(out != null, "set -Djrefactor.schemas.out to a file to write the schemas to");
    ObjectMapper json =
        JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    ObjectNode all = json.createObjectNode();
    for (Tools.Def d : Tools.ALL) {
      ObjectNode schema = (ObjectNode) json.readTree(d.schema());
      if (schema.get("required") instanceof ArrayNode required) {
        List<String> names = new ArrayList<>();
        required.forEach(n -> names.add(n.asString()));
        ArrayNode sorted = schema.putArray("required");
        for (String n : new TreeSet<>(names)) {
          sorted.add(n);
        }
      }
      ObjectNode entry = all.putObject(d.name());
      entry.set("schema", schema);
      ArrayNode args = entry.putArray("args");
      for (String a : new TreeSet<>(d.args())) {
        args.add(a);
      }
    }
    Object tree = json.treeToValue(all, Object.class);
    Files.writeString(Path.of(out), json.writerWithDefaultPrettyPrinter().writeValueAsString(tree));
  }
}
