package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalTest {

  @Test
  void undoRemovesAFileTheChangeCreatedAndThenEdited(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Path file = root.resolve("notes.txt");
    Journal.begin("batch");
    Files.writeString(file, "first");
    Journal.wrote(file, null, "first");
    Files.writeString(file, "second");
    Journal.wrote(file, "first", "second");
    Journal.commit(project);
    Journal.undo(project);
    assertFalse(Files.exists(file));
  }
}
