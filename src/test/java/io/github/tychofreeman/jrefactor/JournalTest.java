package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
}
