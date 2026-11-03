package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
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
}
