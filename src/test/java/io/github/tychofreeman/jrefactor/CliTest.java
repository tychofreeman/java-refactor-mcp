package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

  private static final String COUNTER =
      """
      package com.example;

      class Counter {
        int next(int step) {
          return step + 1;
        }
      }
      """;

  @TempDir Path root;
  @TempDir Path undo;
  private Path file;
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  @BeforeEach
  void project() throws Exception {
    file = root.resolve("com/example/Counter.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, COUNTER);
  }

  /** Runs the command line in a fresh process's place: a new project, the same undo stack. */
  private int run(String... args) {
    bytes.reset();
    PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    return Cli.run(List.of(args), root, undo, out);
  }

  private String out() {
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void aRenameByKeyValueIsUndoneByALaterRun() throws Exception {
    assertEquals(
        0,
        run("rename", "file=com/example/Counter.java", "line=4", "name=step", "new_name=by"),
        out());
    assertTrue(Files.readString(file).contains("return by + 1;"), Files.readString(file));

    assertEquals(0, run("undo"), out());
    assertEquals(COUNTER, Files.readString(file));
    assertEquals(2, run("undo"), out());
    assertTrue(out().contains("Nothing to undo"), out());
  }

  @Test
  void argumentsComeAsJsonOrFromAFile() throws Exception {
    String json =
        "{\"file\": \"com/example/Counter.java\", \"line\": 4, \"name\": \"step\","
            + " \"new_name\": \"by\"}";
    assertEquals(0, run("rename", json), out());
    assertEquals(0, run("undo"), out());

    Path args = undo.resolve("args.json");
    Files.writeString(args, json);
    assertEquals(0, run("rename", "@" + args), out());
    assertTrue(Files.readString(file).contains("int next(int by)"), Files.readString(file));
  }

  @Test
  void aTextArgumentComesFromAFile() throws Exception {
    Path content = undo.resolve("notes.txt");
    Files.writeString(content, "first line\nsecond line\n");
    assertEquals(0, run("create_file", "file=notes.txt", "content_file=" + content), out());
    assertEquals("first line\nsecond line\n", Files.readString(root.resolve("notes.txt")));
  }

  @Test
  void aRefusalExitsTwoWithItsReason() throws Exception {
    assertEquals(
        2,
        run("rename", "file=com/example/Counter.java", "line=4", "name=missing", "new_name=x"),
        out());
    assertTrue(out().startsWith(Refusal.PREFIX), out());
    assertEquals(COUNTER, Files.readString(file));
  }

  @Test
  void listNamesEveryTool() {
    assertEquals(0, run("list"), out());
    for (Tools.Def def : Tools.ALL) {
      assertTrue(out().contains(def.name()), def.name() + " in:\n" + out());
    }
  }
}
