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
    SourceProject project = TestProjects.maven(root, true);
    Path main = root.resolve("src/main/java");
    Path test = root.resolve("src/test/java");
    Path file = test.resolve("com/example/ShopTest.java");

    Edits.gated(project, file, "", TEST_CLASS, List.of(), "creating ShopTest", "Fix it.");

    assertTrue(Files.exists(test.resolve("com/example/Helper.java")));
    assertFalse(Files.exists(main.resolve("com/example/Helper.java")));
  }

  @Test
  void aProductionClassATestUsesFirstDeclaredInTheMainRoot(@TempDir Path root) throws Exception {
    SourceProject project = TestProjects.maven(root, true);
    Path main = root.resolve("src/main/java");
    Path test = root.resolve("src/test/java");
    Path file = test.resolve("com/example/CartTest.java");

    Edits.gated(
        project,
        file,
        "",
        "package com.example;\n\nclass CartTest {\n  Cart cart;\n}\n",
        List.of(),
        "creating CartTest",
        "Fix it.");

    assertTrue(Files.exists(main.resolve("com/example/Cart.java")));
    assertFalse(Files.exists(test.resolve("com/example/Cart.java")));
  }
}
