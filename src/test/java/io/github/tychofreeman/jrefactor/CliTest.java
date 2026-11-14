package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

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

  /** A JSON object alone names its tool with "tool"; after a tool's name, the key may repeat it. */
  @Test
  void aJsonObjectAloneNamesItsTool() throws Exception {
    String json =
        "{\"tool\": \"rename\", \"file\": \"com/example/Counter.java\", \"line\": 4,"
            + " \"name\": \"step\", \"new_name\": \"by\"}";
    Path args = undo.resolve("call.json");
    Files.writeString(args, json);
    assertEquals(0, run("@" + args), out());
    assertTrue(Files.readString(file).contains("int next(int by)"), Files.readString(file));
    assertEquals(0, run("undo"), out());

    assertEquals(0, run("rename", "@" + args), out());
    assertEquals(0, run("undo"), out());

    assertEquals(2, run("inline_variable", "@" + args), out());
    assertTrue(out().contains("names the tool rename"), out());
    assertEquals(2, run("{\"file\": \"x\"}"), out());
    assertTrue(out().contains("\"tool\""), out());
  }

  @Test
  void aTextArgumentComesFromAFile() throws Exception {
    Path content = undo.resolve("notes.txt");
    Files.writeString(content, "first line\nsecond line\n");
    assertEquals(0, run("create_file", "file=notes.txt", "content_file=" + content), out());
    assertEquals("first line\nsecond line\n", Files.readString(root.resolve("notes.txt")));
  }

  /** A key_file of "-" reads the text from stdin, as a shell heredoc gives it; only one may. */
  @Test
  void aTextArgumentComesFromStdin() throws Exception {
    InputStream stdin = System.in;
    try {
      System.setIn(
          new ByteArrayInputStream("first line\nsecond line\n".getBytes(StandardCharsets.UTF_8)));
      assertEquals(0, run("create_file", "file=notes.txt", "content_file=-"), out());
      assertEquals("first line\nsecond line\n", Files.readString(root.resolve("notes.txt")));

      System.setIn(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
      assertEquals(2, run("replace_text", "file=notes.txt", "old_file=-", "new_file=-"), out());
      assertTrue(out().contains("one argument can come from stdin"), out());
    } finally {
      System.setIn(stdin);
    }
  }

  /**
   * Text after the JSON object, in "@@name" sections, stands for each string "@name" of it, so code
   * needs no escaping; a section not used, or a name with no section, is refused.
   */
  @Test
  void codeComesInSectionsAfterTheJson() throws Exception {
    InputStream stdin = System.in;
    try {
      String call =
          """
          {"tool": "create_file", "file": "Notes.txt", "content": "@notes"}
          @@notes
          a "quoted" line\\n
            indented
          """;
      System.setIn(new ByteArrayInputStream(call.getBytes(StandardCharsets.UTF_8)));
      assertEquals(0, run("-"), out());
      assertEquals("a \"quoted\" line\\n\n  indented", Files.readString(root.resolve("Notes.txt")));

      System.setIn(
          new ByteArrayInputStream(
              "{\"tool\": \"create_file\", \"file\": \"B.txt\", \"content\": \"@b\"}\n@@c\nx\n"
                  .getBytes(StandardCharsets.UTF_8)));
      assertEquals(2, run("-"), out());
      assertTrue(out().contains("section @@c is not used"), out());

      System.setIn(
          new ByteArrayInputStream(
              "{\"tool\": \"create_file\", \"file\": \"B.txt\", \"content\": \"@b\", \"x\": \"@a\"}\n@@a\nx\n"
                  .getBytes(StandardCharsets.UTF_8)));
      assertEquals(2, run("-"), out());
      assertTrue(out().contains("names section @@b, which is not given"), out());
    } finally {
      System.setIn(stdin);
    }
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

  /** A batch refused in one run is fixed by retry in the next, as within one server. */
  @Test
  void aRefusedBatchIsRetriedByALaterRun() throws Exception {
    String refused =
        "{\"steps\": [{\"tool\": \"rename\", \"args\": {\"file\": \"com/example/Counter.java\","
            + " \"line\": 4, \"name\": \"nope\", \"new_name\": \"by\"}}]}";
    assertEquals(2, run("batch", refused), out());
    assertTrue(out().contains("retry"), out());

    String retry =
        "{\"retry\": true, \"steps\": [{\"step\": 1, \"args\": {\"file\":"
            + " \"com/example/Counter.java\", \"line\": 4, \"name\": \"step\", \"new_name\":"
            + " \"by\"}}]}";
    assertEquals(0, run("batch", retry), out());
    assertTrue(Files.readString(file).contains("return by + 1;"), Files.readString(file));

    assertEquals(2, run("batch", retry), out());
    assertTrue(out().contains("no refused batch"), out());
  }

  @Test
  void aLoggedRunReadsAsAShellCallInTheSessionReport() {
    Instant now = Instant.parse("2026-11-14T10:00:00Z");
    String json =
        "{\"tool\": \"replace_text\", \"file\": \"A.txt\", \"old\": \"a;\", \"new\": \"it's | b\"}";
    String refused = Cli.logLine(List.of("-"), json, root, "refused: no match", 2, now);
    JsonNode e = SessionLog.JSON.readTree(refused);
    assertEquals("PostToolUseFailure", e.path("hook_event_name").asString());
    assertEquals("Bash", e.path("tool_name").asString());
    assertEquals(root.toString(), e.path("cwd").asString());
    assertEquals("refused: no match", e.path("result").asString());
    assertEquals("2026-11-14T10:00:00Z", e.path("time").asString());
    String command = e.path("tool_input").path("command").asString();
    assertTrue(command.startsWith("bin/jrefactor '"), command);
    assertEquals(
        SessionLog.JSON.readTree(json),
        SessionLog.JSON.readTree(command.substring(15, command.length() - 1)),
        command);
    String done =
        Cli.logLine(
            List.of(
                "rename", "file=com/example/Counter.java", "line=4", "name=step", "new_name=by"),
            "",
            root,
            "renamed",
            0,
            now);
    assertEquals("PostToolUse", SessionLog.JSON.readTree(done).path("hook_event_name").asString());
    String report = SessionReport.report(List.of(refused, done));
    assertTrue(report.contains("- bin/jrefactor calls (in Bash): 2"), report);
    assertTrue(report.contains("- refused by jrefactor: 1"), report);
    assertTrue(report.contains("1 refactoring step"), report);
  }

  @Test
  void malformedJsonIsARefusalWithOrWithoutSections() throws Exception {
    assertEquals(2, run("{\"tool\": \"list\""), out());
    assertTrue(out().startsWith(Refusal.PREFIX + "the JSON"), out());
    assertEquals(2, run("inspect", "names=[\"Counter\""), out());
    assertTrue(out().startsWith(Refusal.PREFIX + "names"), out());
  }
}
