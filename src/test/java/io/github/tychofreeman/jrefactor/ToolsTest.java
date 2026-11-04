package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
}
