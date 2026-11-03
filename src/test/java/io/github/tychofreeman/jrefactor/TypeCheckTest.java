package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeCheckTest {

  @Test
  void aCountFollowsEveryChangeOnDisk(@TempDir Path root) throws Exception {
    Path file = root.resolve("com/example/A.java");
    Files.createDirectories(file.getParent());
    String good = "package com.example;\n\nclass A {\n  int x = 1;\n}\n";
    Files.writeString(file, good);
    SourceProject project = SourceProject.of(root);

    assertEquals(Map.of(), TypeCheck.errors(project));
    Files.writeString(file, good.replace("1", "\"one\""));
    assertEquals(
        Map.of("com/example/A.java | Type mismatch: cannot convert from String to int", 1),
        TypeCheck.errors(project));
    Files.writeString(file, good);
    assertEquals(Map.of(), TypeCheck.errors(project));
  }

  @Test
  void aCountIsReusedWhileNothingChanged(@TempDir Path root) throws Exception {
    Path file = root.resolve("com/example/A.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "package com.example;\n\nclass A {}\n");
    SourceProject project = SourceProject.of(root);

    TypeCheck.errors(project);
    int parsed = TypeCheck.parses(project);
    TypeCheck.errors(project);
    assertEquals(parsed, TypeCheck.parses(project));
  }
}
