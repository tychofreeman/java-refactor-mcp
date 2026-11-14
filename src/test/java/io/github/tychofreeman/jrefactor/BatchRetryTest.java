package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchRetryTest {

  private static final Map<String, Object> ADD =
      Map.of(
          "tool",
          "add_declaration",
          "args",
          Map.of("file", "com/example/A.java", "content", "int a() {\n  return 1;\n}\n"));

  private static SourceProject project(Path root) throws Exception {
    Files.createDirectories(root.resolve("com/example"));
    Files.writeString(root.resolve("com/example/A.java"), "package com.example;\n\nclass A {}\n");
    return SourceProject.of(root);
  }

  private static Map<String, Object> replace(String old, String now) {
    return Map.of(
        "tool",
        "replace_text",
        "args",
        Map.of("file", "com/example/A.java", "old", old, "new", now));
  }

  @Test
  void aRetryResendsOnlyTheStepsThatChange(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    Refusal refused =
        assertThrows(
            Refusal.class,
            () ->
                Tools.call(
                    Tools.named("batch"),
                    project,
                    Map.of("steps", List.of(ADD, replace("return 7;", "return 2;")))));
    assertTrue(refused.getMessage().contains("retry: true"), refused.getMessage());
    assertEquals(
        "package com.example;\n\nclass A {}\n",
        Files.readString(root.resolve("com/example/A.java")));

    Map<String, Object> fixed = new java.util.HashMap<>(replace("return 1;", "return 2;"));
    fixed.put("step", 2);
    Tools.call(Tools.named("batch"), project, Map.of("retry", true, "steps", List.of(fixed)));

    String text = Files.readString(root.resolve("com/example/A.java"));
    assertTrue(text.contains("return 2;"), text);
  }

  @Test
  void aRetryAddsAStepAfterOne(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    assertThrows(
        Refusal.class,
        () ->
            Tools.call(
                Tools.named("batch"),
                project,
                Map.of("steps", List.of(ADD, replace("return 2;", "return 3;")))));

    Map<String, Object> between = new java.util.HashMap<>(replace("return 1;", "return 2;"));
    between.put("after", 1);
    Tools.call(Tools.named("batch"), project, Map.of("retry", true, "steps", List.of(between)));

    assertTrue(Files.readString(root.resolve("com/example/A.java")).contains("return 3;"));
  }

  @Test
  void aRetryWithNothingRefusedIsRefused(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    Refusal refused =
        assertThrows(
            Refusal.class,
            () ->
                Tools.call(
                    Tools.named("batch"),
                    project,
                    Map.of("retry", true, "steps", List.of(Map.of("step", 1, "args", Map.of())))));
    assertTrue(refused.getMessage().contains("no refused batch"), refused.getMessage());
  }

  @Test
  void aStepOnePastTheEndIsAddedLast(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    assertThrows(
        Refusal.class,
        () ->
            Tools.call(
                Tools.named("batch"),
                project,
                Map.of("steps", List.of(ADD, replace("return 7;", "return 2;")))));

    Map<String, Object> fixed = new java.util.HashMap<>(replace("return 1;", "return 2;"));
    fixed.put("step", 2);
    Map<String, Object> last = new java.util.HashMap<>(replace("return 2;", "return 3;"));
    last.put("step", 3);
    Tools.call(Tools.named("batch"), project, Map.of("retry", true, "steps", List.of(fixed, last)));

    assertTrue(Files.readString(root.resolve("com/example/A.java")).contains("return 3;"));
  }

  @Test
  void aRetryMovesAStepAndChangesAStepsTool() {
    List<Map<String, Object>> base =
        List.of(
            Map.of("tool", "inline", "args", Map.of("n", 1)),
            Map.of("tool", "rename", "args", Map.of("n", 2)),
            Map.of("tool", "fold", "args", Map.of("n", 3)));
    List<Map<String, Object>> out =
        Batch.retried(
            base,
            List.of(
                Map.of("remove", 2),
                Map.of("after", 0, "tool", "rename", "args", Map.of("n", 2)),
                Map.of("step", 3, "tool", "extract_function", "args", Map.of("n", 4))));
    assertEquals(
        List.of("rename", "inline", "extract_function"),
        out.stream().map(s -> s.get("tool")).toList());
  }

  @Test
  void aStepNumberThatIsNoNumberIsRefused() {
    List<Map<String, Object>> base = List.of(Map.of("tool", "inline", "args", Map.of("n", 1)));
    Refusal refused =
        assertThrows(
            Refusal.class,
            () -> Batch.retried(base, List.of(Map.of("step", "1", "args", Map.of()))));
    assertTrue(refused.getMessage().contains("are numbers"), refused.getMessage());
  }

  @Test
  void aStepChangedTwiceIsRefused() {
    List<Map<String, Object>> base =
        List.of(
            Map.of("tool", "inline", "args", Map.of("n", 1)),
            Map.of("tool", "rename", "args", Map.of("n", 2)));
    for (List<Map<String, Object>> changes :
        List.of(
            List.of(Map.<String, Object>of("step", 2), Map.<String, Object>of("step", 2)),
            List.of(Map.<String, Object>of("step", 2), Map.<String, Object>of("remove", 2)))) {
      Refusal refused = assertThrows(Refusal.class, () -> Batch.retried(base, changes));
      assertTrue(refused.getMessage().contains("step 2 is changed twice"), refused.getMessage());
    }
  }
}
