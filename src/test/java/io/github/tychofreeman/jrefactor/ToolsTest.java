package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ToolsTest {

  /** The most tools the server exposes. Raised only with the user. */
  static final int MAX_TOOLS = 50;

  @Test
  void theToolCountStaysWithinTheLimit() {
    assertTrue(
        Tools.ALL.size() <= MAX_TOOLS,
        "the server exposes " + Tools.ALL.size() + " tools, the limit is " + MAX_TOOLS);
  }

  @Test
  void everyToolNameIsUnique() {
    List<String> names = Tools.ALL.stream().map(Tools.Def::name).toList();
    Set<String> distinct = new HashSet<>(names);
    assertTrue(distinct.size() == names.size(), "a tool name is defined twice: " + names);
  }

  /** build stands in place of the typed argument, so a tool that takes it cannot require that. */
  @Test
  void aToolThatTakesBuildDoesNotRequireTheTypedText() throws Exception {
    List<String> taking = new java.util.ArrayList<>();
    for (Tools.Def def : Tools.ALL) {
      if (!def.args().contains("build")) {
        continue;
      }
      taking.add(def.name());
      String required = String.valueOf(new ObjectMapper().readTree(def.schema()).path("required"));
      assertTrue(
          !required.contains("\"content\"") && !required.contains("\"new\""),
          def.name() + " takes build and still requires " + required);
    }
    assertEquals(List.of("insert_statements", "replace_statements"), taking);
  }
}
