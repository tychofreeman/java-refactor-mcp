package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalTest {

  @Test
  void undoRemovesAFileTheChangeCreatedAndThenEdited(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Path file = root.resolve("notes.txt");
    Journal.begin(project, "batch");
    Files.writeString(file, "first");
    Journal.wrote(project, file, null, "first");
    Files.writeString(file, "second");
    Journal.wrote(project, file, "first", "second");
    Journal.commit(project);
    Journal.undo(project);
    assertFalse(Files.exists(file));
  }

  @Test
  void aFailedUndoDoesNotHideTheErrorThatCausedIt(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Path file = root.resolve("notes.txt");
    Files.writeString(file, "first");
    Journal.begin(project, "batch");
    Journal.wrote(project, file, "first", "second");
    Files.delete(file);
    Files.createDirectory(file); // the undo cannot write over a directory
    IllegalStateException failure = new IllegalStateException("the call failed");
    Tools.abort(project, failure);
    assertEquals(1, failure.getSuppressed().length);
  }

  @Test
  void undoOfATypeRenameMovesTheFileBack(@TempDir Path root) throws Exception {
    Path plant = root.resolve("com/example/Plant.java");
    Files.createDirectories(plant.getParent());
    Files.writeString(plant, "package com.example;\n\npublic class Plant {}\n");
    SourceProject project = SourceProject.of(root);
    Tools.call(
        Tools.named("rename"),
        project,
        Map.of("file", "com/example/Plant.java", "line", 3, "name", "Plant", "new_name", "Herb"));
    assertFalse(Files.exists(plant));
    assertEquals(
        "undid rename: restored com/example/Plant.java; removed com/example/Herb.java",
        Journal.undo(project));
    assertEquals("package com.example;\n\npublic class Plant {}\n", Files.readString(plant));
    assertFalse(Files.exists(root.resolve("com/example/Herb.java")));
  }

  @Test
  void aProjectOpenedAgainHasNoHistory(@TempDir Path root) throws Exception {
    SourceProject first = SourceProject.of(root);
    Path file = root.resolve("notes.txt");
    Journal.begin(first, "batch");
    Files.writeString(file, "x");
    Journal.wrote(first, file, null, "x");
    Journal.commit(first);

    SourceProject second = SourceProject.of(root);
    Refusal refusal = assertThrows(Refusal.class, () -> Journal.undo(second));
    assertEquals("Nothing to undo in this session.", refusal.getMessage());
  }

  /** A file deleted by the journal takes the directories it alone filled with it. */
  @Test
  void deletingAFileRemovesTheDirectoriesItLeavesEmpty(@TempDir Path root) throws Exception {
    Path kept = root.resolve("a/keep.txt");
    Files.createDirectories(kept.getParent());
    Files.writeString(kept, "kept");
    Path file = root.resolve("a/b/c/new.txt");
    SourceProject project = SourceProject.of(root);
    Journal.put(project, file, "new");
    Journal.put(project, file, null);
    assertFalse(Files.exists(root.resolve("a/b")));
    assertTrue(Files.exists(kept));
  }

  /** A file move_file moves keeps its mode, and undo puts the source back with it. */
  @Test
  void moveKeepsTheModeAndUndoRestoresIt(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Path script = executable(root.resolve("mvnw"));
    Journal.begin(project, "move_file");
    MoveFile.run(project, "mvnw", "fx/mvnw", false, false);
    Journal.commit(project);
    assertEquals("rwxr-xr-x", mode(root.resolve("fx/mvnw")));
    Journal.undo(project);
    assertEquals("rwxr-xr-x", mode(script));
  }

  @Test
  void aCopyKeepsTheMode(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    executable(root.resolve("mvnw"));
    Journal.begin(project, "move_file");
    MoveFile.run(project, "mvnw", "fx/mvnw", true, false);
    Journal.commit(project);
    assertEquals("rwxr-xr-x", mode(root.resolve("fx/mvnw")));
  }

  /** The command line saves the history between runs; a deleted file's mode is in it. */
  @Test
  void theSavedHistoryKeepsADeletedFilesMode(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Path script = executable(root.resolve("mvnw"));
    Journal.begin(project, "move_file");
    MoveFile.run(project, "mvnw", null, false, true);
    Journal.commit(project);
    Path saved = root.resolve(".journal.json");
    Journal.save(project, saved);
    SourceProject again = SourceProject.of(root);
    Journal.load(again, saved);
    Journal.undo(again);
    assertEquals("rwxr-xr-x", mode(script));
  }

  private static Path executable(Path file) throws Exception {
    Files.writeString(file, "#!/bin/sh\n");
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    return file;
  }

  private static String mode(Path file) throws Exception {
    return PosixFilePermissions.toString(Files.getPosixFilePermissions(file));
  }

  /** Deleting the project's only file leaves the project's directory. */
  @Test
  void deletingTheOnlyFileKeepsTheProjectRoot(@TempDir Path parent) throws Exception {
    Path root = Files.createDirectories(parent.resolve("project"));
    SourceProject project = SourceProject.of(root);
    Path file = root.resolve("A.txt");
    Journal.put(project, file, "a");
    Journal.put(project, file, null);
    assertTrue(Files.isDirectory(root));
  }

  /** A source root left empty stays: the user made it. */
  @Test
  void deletingTheOnlyFileOfASourceRootKeepsIt(@TempDir Path root) throws Exception {
    Path tests = Files.createDirectories(root.resolve("src/test/java"));
    SourceProject project = new SourceProject(root, List.of(tests), List.of(), null, new Session());
    Path file = tests.resolve("p/ATest.java");
    Journal.put(project, file, "class ATest {}");
    Journal.put(project, file, null);
    assertFalse(Files.exists(tests.resolve("p")));
    assertTrue(Files.isDirectory(tests));
  }

  /** An edit through a link to a file outside the project is refused, and the file is unchanged. */
  @Test
  void anEditThroughALinkOutOfTheProjectIsRefused(@TempDir Path root, @TempDir Path away)
      throws Exception {
    Path target = Files.writeString(away.resolve("outside.txt"), "hi\n");
    Files.createSymbolicLink(root.resolve("link.txt"), target);
    SourceProject project = SourceProject.of(root);
    Refusal r =
        assertThrows(
            Refusal.class,
            () ->
                Tools.call(
                    Tools.named("replace_text"),
                    project,
                    Map.of("file", "link.txt", "old", "hi", "new", "changed")));
    assertTrue(
        r.getMessage().contains("leads out of the project through a symbolic link"),
        r.getMessage());
    assertEquals("hi\n", Files.readString(target));
  }

  /** A new file under a link to a directory outside the project is refused, and not written. */
  @Test
  void aNewFileUnderALinkedDirectoryOutOfTheProjectIsRefused(@TempDir Path root, @TempDir Path away)
      throws Exception {
    Files.createSymbolicLink(root.resolve("docs"), away);
    SourceProject project = SourceProject.of(root);
    assertThrows(
        Refusal.class,
        () ->
            Tools.call(
                Tools.named("create_file"),
                project,
                Map.of("file", "docs/new.txt", "content", "x\n")));
    assertFalse(Files.exists(away.resolve("new.txt")));
  }

  /** A link that points nowhere yet, outside the project, is followed by its text. */
  @Test
  void aDanglingLinkOutOfTheProjectIsRefused(@TempDir Path root, @TempDir Path away)
      throws Exception {
    Files.createSymbolicLink(root.resolve("later.txt"), away.resolve("later.txt"));
    SourceProject project = SourceProject.of(root);
    assertThrows(Refusal.class, () -> Journal.put(project, root.resolve("later.txt"), "x\n"));
    assertFalse(Files.exists(away.resolve("later.txt")));
  }

  /** A link from one file of the project to another is edited as the file it names. */
  @Test
  void anEditThroughALinkInsideTheProjectApplies(@TempDir Path root) throws Exception {
    Path target = Files.writeString(root.resolve("real.txt"), "hi\n");
    Files.createSymbolicLink(root.resolve("link.txt"), target);
    SourceProject project = SourceProject.of(root);
    Tools.call(
        Tools.named("replace_text"),
        project,
        Map.of("file", "link.txt", "old", "hi", "new", "changed"));
    assertEquals("changed\n", Files.readString(target));
  }
}
