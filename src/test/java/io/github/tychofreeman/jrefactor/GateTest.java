package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GateTest {

  @Test
  void aRefusalAboutAnUnresolvedNameSaysWhyTheClasspathIsMissing(@TempDir Path root)
      throws Exception {
    Path file = root.resolve("com/example/A.java");
    Files.createDirectories(file.getParent());
    String text = "package com.example;\n\nimport org.lib.Foo;\n\nclass A {\n  Foo f;\n}\n";
    SourceProject project =
        new SourceProject(root, List.of(root), List.of(), "mvn exit 1: no network", new Session());

    Refusal refusal =
        assertThrows(
            Refusal.class,
            () -> Gate.applyOrRefuse(project, Map.of(file, text), "creating A", "Fix it."));
    assertTrue(
        refusal
            .getMessage()
            .contains(
                "Maven gave no classpath, so library types do not resolve: mvn exit 1: no network"),
        refusal::getMessage);
  }

  @Test
  void aRestoreRecreatesTheDirectoryOfTheFileItWrites(@TempDir Path root) throws Exception {
    Path file = root.resolve("gone/A.java");
    Journal.restore(Map.of(file, "old text"));
    assertEquals("old text", Files.readString(file));
  }

  @Test
  void aNestedDeferredCallLeavesTheOuterOneDeferred(@TempDir Path root) throws Exception {
    SourceProject project = new SourceProject(root, List.of(root), List.of(), null, new Session());
    List<Boolean> afterInner = new ArrayList<>();

    Gate.deferred(
        project,
        () -> {
          Gate.deferred(project, () -> null);
          afterInner.add(project.session().deferred);
          return null;
        });

    assertTrue(afterInner.get(0), "the outer call is still deferred after a nested one ends");
  }
}
