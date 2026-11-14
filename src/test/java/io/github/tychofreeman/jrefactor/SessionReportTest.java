package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class SessionReportTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final List<String> log = new ArrayList<>();
  private int ids;

  /** Logs a call: before it ran, then after with {@code result} (none when null). */
  private void call(String hook, String tool, String input, String result) {
    String id = "t" + ++ids;
    log.add(line("PreToolUse", id, tool, input, null));
    if (hook != null) {
      log.add(line(hook, id, tool, input, result));
    }
  }

  private static String line(String hook, String id, String tool, String input, String result) {
    ObjectNode e = JSON.createObjectNode();
    e.put("hook_event_name", hook);
    e.put("tool_use_id", id);
    e.put("tool_name", tool);
    e.set("tool_input", JSON.readTree(input));
    e.put("cwd", "/repo");
    e.put("transcript_path", "/t.jsonl");
    if (result != null) {
      e.put("result", result);
    }
    return JSON.writeValueAsString(e);
  }

  @Test
  void reportsWhatWentThroughTheToolsAndWhatWasDoneByHand() {
    String post = "PostToolUse";
    call(post, "mcp__jrefactor__inspect", "{\"file\": \"src/A.java\"}", "...");
    call(
        "PostToolUseFailure",
        "mcp__jrefactor__replace_text",
        "{\"file\": \"docs/a.md\", \"old\": \"x\", \"new\": \"y\"}",
        "refused: old is not in docs/a.md.\nThe nearest lines are 3, 4.");
    call(post, "Edit", "{\"file_path\": \"/repo/docs/a.md\", \"new_string\": \"y\"}", "ok");
    String content = "class A {\n" + "  int x;\n".repeat(23) + "}\n";
    call(
        post,
        "mcp__jrefactor__batch",
        "{\"steps\": [{\"tool\": \"create_file\", \"args\": {\"file\": \"src/A.java\", \"content\": "
            + JSON.writeValueAsString(content)
            + "}}, {\"tool\": \"rename\", \"args\": {\"file\": \"src/A.java\"}}]}",
        "batch of 2 steps");
    call(null, "Bash", "{\"command\": \"rm -rf build\"}", null);
    call(post, "Write", "{\"file_path\": \"/tmp/scratch/x.txt\", \"content\": \"z\"}", "ok");
    call(post, "mcp__jr__extract_method", "{\"file\": \"src/A.java\"}", "extracted");
    call(post, "Bash", "{\"command\": \"cat > /repo/src/B.java <<'EOF'\\nclass B {}\\nEOF\"}", "");
    call(post, "Read", "{\"file_path\": \"/repo/src/A.java\"}", "...");
    call(post, "Bash", "{\"command\": \"./mvnw -q test > /dev/null 2>&1\"}", "");

    assertEquals(
        """
        - tool calls: 10
        - through jrefactor: 2 refactoring steps, 1 edit step
        - edit steps by what carried the code: content 1
        - Java typed in edits: 219 characters, 0 in build strings; names in build: 0 characters
        - refused by jrefactor: 1
        - by hand: 2
          - Edit docs/a.md
          - Bash: cat > /repo/src/B.java <<'EOF'
        - refused, then done by hand: 1
          - replace_text (refused: old is not in docs/a.md.), then Edit docs/a.md
        - large writes (20 lines of Java or more in one call): 1
          - create_file src/A.java: 25 lines
        - written to scratch files: 1
          - Write /tmp/scratch/x.txt
        - denied or failed (no result): 1
          - Bash: rm -rf build
        - reads: Read java 1, inspect 1
        """,
        SessionReport.report(log));
  }

  @Test
  void countsEveryRefactoringAndEditTheServerHas() {
    int changes = 0;
    for (Tools.Def def : Tools.ALL) {
      if (!def.readOnly() && !def.name().equals("batch") && !def.name().equals("undo")) {
        changes++;
        call("PostToolUse", "mcp__jrefactor__" + def.name(), "{\"file\": \"a.txt\"}", "done");
      }
    }

    String report = SessionReport.report(log);
    int counted = 0;
    Matcher m = Pattern.compile("(\\d+) (refactoring|edit) steps?").matcher(report);
    while (m.find()) {
      counted += Integer.parseInt(m.group(1));
    }
    assertEquals(changes, counted, report);
  }

  @Test
  void aGrepWithAngleBracketsInItsPatternIsARead() {
    call("PostToolUse", "Bash", "{\"command\": \"grep -n 'List<Path> x' src/A.java\"}", "");

    assertEquals(
        "- tool calls: 1\n- through jrefactor: 0 refactoring steps, 0 edit steps\n"
            + "- refused by jrefactor: 0\n- by hand: 0\n- refused, then done by hand: 0\n"
            + "- large writes (20 lines of Java or more in one call): 0\n"
            + "- written to scratch files: 0\n- denied or failed (no result): 0\n"
            + "- reads: Bash java 1\n",
        SessionReport.report(log));
  }

  @Test
  void aHeredocThatWritesMarkdownPutsInNoJava() {
    String command = "cat > docs/a.md <<'EOF'\\n" + "see A.java\\n".repeat(25) + "EOF";
    call("PostToolUse", "Bash", "{\"command\": \"" + command + "\"}", "");

    String report = SessionReport.report(log);
    assertTrue(report.contains("- by hand: 1\n"), report);
    assertTrue(report.contains("in one call): 0\n"), report);
  }

  /** A Bash command that runs bin/jrefactor is a call through the tools, not a write by hand. */
  @Test
  void aCallOfBinJrefactorCountsAsTheTools() throws Exception {
    String post = "PostToolUse";
    String batch =
        "{\"steps\": [{\"tool\": \"create_file\", \"args\": {\"file\": \"src/A.java\"}},"
            + " {\"tool\": \"rename\", \"args\": {\"file\": \"src/A.java\"}}]}";
    call(
        post,
        "Bash",
        JSON.writeValueAsString(
            Map.of("command", "bin/jrefactor --rebuild batch '" + batch + "' 2>&1 | tail -2")),
        "batch of 2 steps: ...");
    call(
        post,
        "Bash",
        "{\"command\": \"bin/jrefactor -C /tmp/x rename file=p/A.java line=3 name=a new_name=b\"}",
        "refused: 'a' is not on line 3.");
    call(post, "Bash", "{\"command\": \"bin/jrefactor @$S/steps.json\"}", "created ...");
    String report = SessionReport.report(log);
    assertTrue(report.contains("through jrefactor: 1 refactoring step, 1 edit step"), report);
    assertTrue(report.contains("bin/jrefactor calls (in Bash): 3"), report);
    assertTrue(report.contains("refused by jrefactor: 1"), report);
    assertTrue(report.contains("- by hand: 0"), report);
  }

  /** Edit steps to Java by what carried the code, and the Java typed against the names built. */
  @Test
  void countsWhatCarriedTheCodeOfEachEdit() {
    String post = "PostToolUse";
    call(
        post,
        "mcp__jrefactor__insert_statements",
        "{\"file\": \"src/A.java\", \"line\": 3, \"build\": [{\"call\": \"Money.zero\", \"into\":"
            + " \"total\"}, \"return total;\"]}",
        "inserted 2 lines");
    call(
        post,
        "mcp__jrefactor__replace_statements",
        "{\"file\": \"src/A.java\", \"start_line\": 3, \"end_line\": 3, \"surround\": \"if (ready)\"}",
        "replaced lines 3-3");
    call(
        post,
        "mcp__jrefactor__insert_statements",
        "{\"file\": \"src/A.java\", \"line\": 3, \"content\": \"x++;\"}",
        "inserted 1 line");

    String report = SessionReport.report(log);
    assertTrue(
        report.contains("- edit steps by what carried the code: build 1, content 1, surround 1\n"),
        report);
    assertTrue(
        report.contains(
            "- Java typed in edits: 17 characters, 13 in build strings; names in build: 15"
                + " characters\n"),
        report);
  }

  /**
   * The report judges which shell commands read or write Java source as the read and write guards
   * do ({@link Guard#use}): nl, more and a cat after a cd read it, a pattern naming a .java file
   * does not, and install writes it.
   */
  @Test
  void shellCommandsAreJudgedAsTheGuardsJudgeThem() {
    String post = "PostToolUse";
    call(post, "Bash", "{\"command\": \"nl -ba src/A.java\"}", "");
    call(post, "Bash", "{\"command\": \"cd src && cat A.java\"}", "");
    call(post, "Bash", "{\"command\": \"more src/B.java\"}", "");
    call(post, "Bash", "{\"command\": \"grep -n 'A.java' docs/notes.md\"}", "");
    call(post, "Bash", "{\"command\": \"cat src/test/resources/golden/c/in/A.java\"}", "");
    call(post, "Bash", "{\"command\": \"install -m 644 /tmp/A.java src/A.java\"}", "");

    String report = SessionReport.report(log);
    assertTrue(report.contains("- reads: Bash java 3\n"), report);
    assertTrue(report.contains("- by hand: 1\n  - Bash: install -m 644"), report);
  }
}
