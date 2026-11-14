package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A required argument left out is refused before the tool runs, in one wording for every tool. */
class RequiredArgsTest {

  @Test
  void everyToolRefusesEachRequiredArgumentLeftOut(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Map<String, Tools.Def> defs = new LinkedHashMap<>();
    for (Tools.Def d : Tools.ALL) {
      defs.put(d.name(), d);
    }
    for (Tools.Def d : Tools.ALIASES.values()) {
      defs.putIfAbsent(d.name(), d);
    }
    for (Tools.Def d : Tools.PUBLISHABLE_ALIASES) {
      defs.putIfAbsent(d.name(), d);
    }
    List<String> wrong = new ArrayList<>();
    for (Tools.Def d : defs.values()) {
      for (Arg left : d.arguments()) {
        if (!left.mandatory()) {
          continue;
        }
        Map<String, Object> values = new HashMap<>();
        for (Arg a : d.arguments()) {
          if (a.mandatory() && a != left) {
            values.put(a.name(), sample(a));
          }
        }
        String description = left.schema().path("description").asString("");
        String expected =
            d.name()
                + " needs "
                + left.name()
                + (description.isEmpty()
                    ? "."
                    : ": " + description + (description.endsWith(".") ? "" : "."))
                + " Pass "
                + left.name()
                + ".";
        try {
          Tools.call(d, project, values);
          wrong.add(d.name() + " without " + left.name() + ": ran");
        } catch (Exception e) {
          if (!expected.equals(e.getMessage())) {
            wrong.add(d.name() + " without " + left.name() + ": " + e.getMessage());
          }
        }
      }
    }
    assertEquals(List.of(), wrong);
  }

  @Test
  void theWordingNamesTheArgumentItsDescriptionAndTheFix(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Refusal refused =
        assertThrows(
            Refusal.class,
            () -> Tools.call(Tools.named("invert_if"), project, Map.of("file", "p/A.java")));
    assertEquals(
        "invert_if needs line: The 1-based line the if starts on. Pass line.",
        refused.getMessage());
    refused =
        assertThrows(
            Refusal.class,
            () ->
                Tools.call(Tools.named("rename"), project, Map.of("file", "p/A.java", "line", 3)));
    assertEquals(
        "rename needs name: The current name; Type.name for a member of Type, or local name for a"
            + " local, when the line has both. Pass name."
            + " rename needs new_name: The new name; a valid Java name. Pass new_name.",
        refused.getMessage());
  }

  /** A value of the argument's JSON type. */
  private static Object sample(Arg a) {
    return switch (a.schema().path("type").asString("string")) {
      case "integer" -> 1;
      case "boolean" -> true;
      case "array" -> List.of();
      case "object" -> Map.of();
      default -> "x";
    };
  }

  @Test
  void aBatchStepWithoutOneIsRefusedInTheSameWords(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Refusal refused =
        assertThrows(
            Refusal.class,
            () ->
                Tools.call(
                    Tools.named("batch"),
                    project,
                    Map.of(
                        "steps",
                        List.of(Map.of("tool", "invert_if", "args", Map.of("file", "p/A.java"))))));
    assertTrue(
        refused
            .getMessage()
            .startsWith(
                "step 1 of 1 (invert_if) refused: invert_if needs line: The 1-based line the if"
                    + " starts on. Pass line."),
        refused.getMessage());
  }
}
