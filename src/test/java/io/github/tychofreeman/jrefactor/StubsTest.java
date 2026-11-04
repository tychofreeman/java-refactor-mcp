package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StubsTest {

  private static final String TEST_CLASS =
      "package com.example;\n\nclass ShopTest {\n  Helper helper;\n}\n";

  @Test
  void aClassUsedOnlyFromATestDeclaredInTheTestRoot(@TempDir Path root) throws Exception {
    Path main = root.resolve("src/main/java");
    Path test = root.resolve("src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Session session = new Session();
    session.editing = true;
    SourceProject project = new SourceProject(root, List.of(main, test), List.of(), null, session);
    Path file = test.resolve("com/example/ShopTest.java");

    Edits.gated(project, file, "", TEST_CLASS, List.of(), "creating ShopTest", "Fix it.");

    assertTrue(Files.exists(test.resolve("com/example/Helper.java")));
    assertFalse(Files.exists(main.resolve("com/example/Helper.java")));
  }
}
