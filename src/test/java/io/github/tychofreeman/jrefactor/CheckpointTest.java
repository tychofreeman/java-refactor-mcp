package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckpointTest {

  private static final String A = "package com.example;\n\nclass A {}\n";

  private static final Map<String, Object> ADD =
      Map.of(
          "tool",
          "add_declaration",
          "args",
          Map.of("file", "com/example/A.java", "content", "int a() {\n  return 1;\n}\n"));

  private static final Map<String, Object> CHECK = Map.of("tool", "check");

  private static final Map<String, Object> MISSING =
      Map.of(
          "tool",
          "replace_text",
          "args",
          Map.of("file", "com/example/A.java", "old", "return 7;", "new", "return 2;"));

  private static SourceProject project(Path root) throws Exception {
    TestProjects.write(root, "com/example/A.java", A);
    return SourceProject.of(root);
  }

  @Test
  void aRefusalAfterAPassingCheckKeepsTheStepsBeforeIt(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    Refusal refused =
        assertThrows(
            Refusal.class,
            () ->
                Tools.call(
                    Tools.named("batch"), project, Map.of("steps", List.of(ADD, CHECK, MISSING))));
    assertTrue(refused.getMessage().contains("kept"), refused.getMessage());
    assertTrue(Files.readString(root.resolve("com/example/A.java")).contains("return 1;"));
    assertNull(project.session().refusedBatch);

    Journal.undo(project);
    assertEquals(A, Files.readString(root.resolve("com/example/A.java")));
  }

  @Test
  void aRefusalAfterACheckAtTheEndKeepsTheStepsBeforeIt(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    assertThrows(
        Refusal.class,
        () ->
            Tools.call(
                Tools.named("batch"),
                project,
                Map.of("check", "end", "steps", List.of(ADD, CHECK, MISSING))));
    assertTrue(Files.readString(root.resolve("com/example/A.java")).contains("return 1;"));
  }

  @Test
  void aCheckThatFailsKeepsNothing(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    Map<String, Object> broken =
        Map.of(
            "tool",
            "replace_text",
            "args",
            Map.of("file", "com/example/A.java", "old", "return 1;", "new", "return \"one\";"));
    Refusal refused =
        assertThrows(
            Refusal.class,
            () ->
                Tools.call(
                    Tools.named("batch"),
                    project,
                    Map.of("check", "end", "steps", List.of(ADD, broken, CHECK))));
    assertTrue(refused.getMessage().contains("step 3"), refused.getMessage());
    assertEquals(A, Files.readString(root.resolve("com/example/A.java")));
  }
}
