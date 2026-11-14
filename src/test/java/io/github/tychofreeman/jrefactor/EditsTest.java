package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void aRefactoringWhoseResultDoesNotParseSaysTheToolWroteIt(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("src/p"));
    SourceProject project =
        new SourceProject(root, List.of(root.resolve("src")), List.of(), null, new Session());
    Path file = root.resolve("src/p/Shop.java");
    String before = "package p;\n\nclass Shop {}\n";
    Files.writeString(file, before);

    Refusal refusal =
        assertThrows(
            Refusal.class,
            () ->
                Edits.gated(
                    project,
                    file,
                    before,
                    "package p;\n\nclass Shop {\n",
                    List.of(),
                    "inlining x",
                    "Keep it."));

    assertTrue(refusal.getMessage().startsWith("inlining x wrote code that does not parse"));
    assertTrue(refusal.getMessage().contains("src/p/Shop.java is unchanged"));
    assertFalse(refusal.getMessage().contains(root.toString()));
  }

  @Test
  void anEditWhoseResultDoesNotParseAsksForTheSyntaxFixed(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("src/p"));
    SourceProject project =
        new SourceProject(root, List.of(root.resolve("src")), List.of(), null, new Session());
    project.session().editing = true;
    Path file = root.resolve("src/p/Shop.java");
    String before = "package p;\n\nclass Shop {}\n";
    Files.writeString(file, before);

    Refusal refusal =
        assertThrows(
            Refusal.class,
            () ->
                Edits.gated(
                    project,
                    file,
                    before,
                    "package p;\n\nclass Shop {\n",
                    List.of(),
                    "editing",
                    "Fix it."));

    assertTrue(refusal.getMessage().startsWith("the result does not parse"));
    assertTrue(refusal.getMessage().endsWith("Fix the syntax and send it again."));
  }
}
