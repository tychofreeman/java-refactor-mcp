package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditsTest {

  private static final String SHOP = "class Shop {\n  Timer timer;\n}\n";

  @Test
  void aJavaFileOutsideTheSourceRootsGetsNoImportsFixed(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("src"));
    SourceProject project =
        new SourceProject(root, List.of(root.resolve("src")), List.of(), null, new Session());
    Path file = root.resolve("scripts/Shop.java");

    Edits.gated(project, file, "", SHOP, List.of(), "creating Shop", "Fix it.");

    assertEquals(SHOP, Files.readString(file));
  }

  @Test
  void aJavaFileUnderASourceRootStillHasItsImportsFixed(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("src"));
    SourceProject project =
        new SourceProject(root, List.of(root.resolve("src")), List.of(), null, new Session());
    Path file = root.resolve("src/Shop.java");

    assertThrows(
        Refusal.class,
        () -> Edits.gated(project, file, "", SHOP, List.of(), "creating Shop", "Fix it."));
  }
}
