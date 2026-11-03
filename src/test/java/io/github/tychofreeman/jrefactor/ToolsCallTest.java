package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolsCallTest {

  @Test
  void stubsTheCallDeclaredAreInItsResultNotOnlyInItsText(@TempDir Path root) throws Exception {
    Path cart = root.resolve("com/example/Cart.java");
    Files.createDirectories(cart.getParent());
    Files.writeString(cart, "package com.example;\n\npublic class Cart {\n}\n");
    SourceProject project = SourceProject.of(root);

    Tools.Outcome outcome =
        Tools.call(
            Tools.named("create_file"),
            project,
            Map.of(
                "file",
                "com/example/Shop.java",
                "content",
                "package com.example;\n\npublic class Shop {\n"
                    + "  int total(Cart c) { return c.total(); }\n}\n"));

    assertEquals("com/example/Cart.java", outcome.declared().byFile().keySet().iterator().next());
    assertTrue(outcome.text().startsWith("created com/example/Shop.java"));
    assertFalse(outcome.text().contains("declared stubs"));
  }
}
