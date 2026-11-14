package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A Claude Code hook (PreToolUse, PostToolUse, PostToolUseFailure) that appends each tool call to
 * the log of the branch being worked on, {@code .claude/session-log/<branch>.jsonl} (gitignored),
 * with the session's transcript and working directory. A call logged before it ran and never after
 * was denied (by a hook or the user). A refusal comes as PostToolUseFailure, its text in {@code
 * error}. {@link SessionReport} reports on a log.
 *
 * <p>It never blocks a call: whatever goes wrong, it exits 0 and prints nothing.
 */
final class SessionLog {

  /** How much of a result's text a line keeps. */
  static final int MAX_RESULT = 50_000;

  /** The mapper of the session log and of its report: one set of JSON settings for both. */
  static final ObjectMapper JSON = Json.MAPPER;

  /** The hook's input fields a line keeps, beside the time and the result. */
  private static final List<String> KEPT =
      List.of(
          "hook_event_name",
          "session_id",
          "tool_use_id",
          "tool_name",
          "tool_input",
          "cwd",
          "transcript_path");

  private SessionLog() {}

  public static void main(String[] args) {
    try {
      String in = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
      String line = entry(in, Instant.now());
      if (line == null) {
        return;
      }
      String root = System.getenv("CLAUDE_PROJECT_DIR");
      if (root == null || root.isEmpty()) {
        root = JSON.readTree(in).path("cwd").asString("");
      }
      if (root.isEmpty()) {
        return;
      }
      Path dir = Path.of(root, ".claude", "session-log");
      Files.createDirectories(dir);
      Files.writeString(
          dir.resolve(logName(branch(Path.of(root)))),
          line + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (Exception | Error e) {
      // A log line lost is better than a call blocked.
    }
  }

  /**
   * The log line for a hook's input {@code in}: the call, with the text of its result (or its
   * error) in place of the response, and the time; null when {@code in} is not a JSON object.
   */
  static String entry(String in, Instant now) {
    JsonNode hook;
    try {
      hook = JSON.readTree(in);
    } catch (RuntimeException e) {
      return null;
    }
    if (hook == null || !hook.isObject()) {
      return null;
    }
    ObjectNode e = JSON.createObjectNode();
    e.put("time", now.toString());
    for (String field : KEPT) {
      if (hook.has(field)) {
        e.set(field, hook.get(field));
      }
    }
    String text = null;
    if (hook.has("tool_response")) {
      text = text(hook.get("tool_response"));
    } else if (hook.has("error")) {
      text = text(hook.get("error"));
    }
    if (text != null) {
      e.put("result", text.length() > MAX_RESULT ? text.substring(0, MAX_RESULT) : text);
      e.put("result_chars", text.length());
    }
    return JSON.writeValueAsString(e);
  }

  /**
   * The text of a tool's response: a string, the text blocks of an MCP result, a command's output
   * and errors, or else the JSON itself.
   */
  static String text(JsonNode response) {
    if (response == null || response.isNull()) {
      return "";
    }
    if (response.isString()) {
      return response.asString();
    }
    if (response.isArray()) {
      List<String> texts = new ArrayList<>();
      for (JsonNode block : response.values()) {
        texts.add(block.path("text").asString(""));
      }
      return String.join("\n", texts);
    }
    if (response.has("content")) {
      return text(response.get("content"));
    }
    if (response.has("stdout")) {
      String out = response.path("stdout").asString("");
      String err = response.path("stderr").asString("");
      return (out + "\n" + err).stripTrailing();
    }
    return JSON.writeValueAsString(response);
  }

  /** The log file of a branch: "detached" when no branch is checked out. */
  static String logName(String branch) {
    if (branch == null || branch.isEmpty() || branch.equals("HEAD")) {
      branch = "detached";
    }
    return branch.replace('/', '-') + ".jsonl";
  }

  /** The branch checked out in {@code root}, or "" if none. */
  private static String branch(Path root) throws IOException, InterruptedException {
    Process git =
        new ProcessBuilder("git", "-C", root.toString(), "symbolic-ref", "--short", "HEAD")
            .redirectErrorStream(false)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    String out = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
    if (!git.waitFor(5, TimeUnit.SECONDS) || git.exitValue() != 0) {
      return "";
    }
    return out;
  }
}
