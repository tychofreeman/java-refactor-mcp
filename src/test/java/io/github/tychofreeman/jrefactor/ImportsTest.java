package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportsTest {

  private static final String BEFORE =
      "package com.example;\n\nimport java.util.Map;\n\nclass A {\n  Map<String, String> m;\n}\n";

  private static final String AFTER =
      "package com.example;\n\nimport java.util.Map;\n\nclass A {\n  List<String> m;\n}\n";

  @Test
  void planAddsWhatTheEditUsesAndRemovesWhatItNoLongerDoes(@TempDir Path root) throws Exception {
    Path file = TestProjects.write(root, "com/example/A.java", BEFORE);
    Imports.Plan plan = Imports.plan(SourceProject.of(root), file, BEFORE, AFTER, List.of());
    assertEquals(Set.of("java.util.List"), plan.add());
    assertEquals(
        List.of("java.util.Map"),
        plan.remove().stream()
            .map((ImportDeclaration i) -> i.getName().getFullyQualifiedName())
            .toList());
  }

  @Test
  void renderWritesThePlan(@TempDir Path root) throws Exception {
    Path file = TestProjects.write(root, "com/example/A.java", BEFORE);
    Imports.Fixed fixed =
        Imports.render(AFTER, Imports.plan(SourceProject.of(root), file, BEFORE, AFTER, List.of()));
    assertEquals(
        "package com.example;\n\nimport java.util.List;\n\nclass A {\n  List<String> m;\n}\n",
        fixed.text());
    assertEquals(List.of("imported java.util.List", "removed import java.util.Map"), fixed.notes());
    assertEquals(0, fixed.lineShift());
  }

  @Test
  void anEmptyPlanLeavesTheTextAlone(@TempDir Path root) throws Exception {
    Path file = TestProjects.write(root, "com/example/A.java", BEFORE);
    Imports.Fixed fixed =
        Imports.render(
            BEFORE, Imports.plan(SourceProject.of(root), file, BEFORE, BEFORE, List.of()));
    assertEquals(BEFORE, fixed.text());
    assertEquals(List.of(), fixed.notes());
  }
}
