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

  /** A tool of {@code kind} that writes Shop.java calling Cart.total(), which nothing declares. */
  private static Tools.Def writer(Tools.Kind kind) {
    return new Tools.Def(
        "writer",
        "test",
        "{\"type\": \"object\"}",
        java.util.Set.of(),
        kind,
        (project, args) -> {
          Path shop = project.root().resolve("com/example/Shop.java");
          String text =
              "package com.example;\n\npublic class Shop {\n"
                  + "  int total(Cart c) { return c.total(); }\n}\n";
          Stubs.Check check = Gate.apply(project, Map.of(shop, text));
          if (!check.introduced().isEmpty()) {
            throw new Refusal("writer: " + check.introduced());
          }
          return new Tools.Outcome("wrote", check.declared());
        });
  }

  private static SourceProject cartProject(Path root) throws Exception {
    Path cart = root.resolve("com/example/Cart.java");
    Files.createDirectories(cart.getParent());
    Files.writeString(cart, "package com.example;\n\npublic class Cart {\n}\n");
    return SourceProject.of(root);
  }

  /** A refactoring changes no behavior, so a name its output leaves unresolved is its own bug. */
  @Test
  void aRefactoringGetsNoStubs(@TempDir Path root) throws Exception {
    SourceProject project = cartProject(root);
    Refusal refused =
        org.junit.jupiter.api.Assertions.assertThrows(
            Refusal.class, () -> Tools.call(writer(Tools.Kind.REFACTORING), project, Map.of()));
    assertTrue(refused.getMessage().contains("total()"), refused.getMessage());
    assertFalse(Files.readString(root.resolve("com/example/Cart.java")).contains("total"));
  }

  @Test
  void anEditGetsStubs(@TempDir Path root) throws Exception {
    SourceProject project = cartProject(root);
    Tools.Outcome outcome = Tools.call(writer(Tools.Kind.EDIT), project, Map.of());
    assertEquals("com/example/Cart.java", outcome.declared().byFile().keySet().iterator().next());
  }
}
