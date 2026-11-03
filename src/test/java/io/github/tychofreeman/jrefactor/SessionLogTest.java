package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SessionLogTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

  @Test
  void anMcpResultKeepsItsText() {
    String line =
        SessionLog.entry(
            """
            {"hook_event_name": "PostToolUse", "session_id": "s", "tool_use_id": "t1",
             "tool_name": "mcp__jrefactor__inspect", "tool_input": {"file": "A.java"},
             "tool_response": [{"type": "text", "text": "line one"}, {"type": "text", "text": "two"}],
             "cwd": "/repo", "transcript_path": "/t.jsonl", "permission_mode": "auto"}
            """,
            NOW);
    JsonNode e = JSON.readTree(line);
    assertEquals("2026-11-03T10:00:00Z", e.path("time").asString());
    assertEquals("PostToolUse", e.path("hook_event_name").asString());
    assertEquals("t1", e.path("tool_use_id").asString());
    assertEquals("A.java", e.path("tool_input").path("file").asString());
    assertEquals("line one\ntwo", e.path("result").asString());
    assertEquals(12, e.path("result_chars").asInt());
    assertEquals("/repo", e.path("cwd").asString());
    assertEquals("/t.jsonl", e.path("transcript_path").asString());
    assertEquals(true, e.path("permission_mode").isMissingNode());
  }

  @Test
  void aFailureKeepsItsError() {
    String line =
        SessionLog.entry(
            """
            {"hook_event_name": "PostToolUseFailure", "tool_use_id": "t2",
             "tool_name": "mcp__jrefactor__inspect", "tool_input": {},
             "error": "refused: No Java file Nope.java"}
            """,
            NOW);
    assertEquals("refused: No Java file Nope.java", JSON.readTree(line).path("result").asString());
  }

  @Test
  void aCommandKeepsItsOutput() {
    String line =
        SessionLog.entry(
            """
            {"hook_event_name": "PostToolUse", "tool_use_id": "t3", "tool_name": "Bash",
             "tool_input": {"command": "ls"}, "tool_response": {"stdout": "a\\nb", "stderr": "warn"}}
            """,
            NOW);
    assertEquals("a\nb\nwarn", JSON.readTree(line).path("result").asString());
  }

  @Test
  void aLongResultIsCut() {
    String text = "x".repeat(SessionLog.MAX_RESULT + 10);
    String line =
        SessionLog.entry(
            "{\"hook_event_name\": \"PostToolUse\", \"tool_use_id\": \"t4\", \"tool_name\":"
                + " \"Read\", \"tool_input\": {}, \"tool_response\": \""
                + text
                + "\"}",
            NOW);
    JsonNode e = JSON.readTree(line);
    assertEquals(SessionLog.MAX_RESULT, e.path("result").asString().length());
    assertEquals(SessionLog.MAX_RESULT + 10, e.path("result_chars").asInt());
  }

  @Test
  void inputThatIsNotJsonIsNotLogged() {
    assertNull(SessionLog.entry("not json", NOW));
  }

  @Test
  void eachBranchHasItsOwnLog() {
    assertEquals("session-log.jsonl", SessionLog.logName("session-log"));
    assertEquals("feature-x.jsonl", SessionLog.logName("feature/x"));
    assertEquals("detached.jsonl", SessionLog.logName(""));
  }
}
