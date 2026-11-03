package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A report on a session log ({@link SessionLog}), for an item's record in {@code docs/sessions/}:
 * what went through java-refactor-mcp, what was done by hand, by hand after a refusal, in large
 * writes, and what was read.
 *
 * <pre>java -cp target/java-refactor-mcp.jar io.github.tychofreeman.jrefactor.SessionReport
 * [.claude/session-log/BRANCH.jsonl]...</pre>
 */
final class SessionReport {

  private SessionReport() {}

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final Set<String> REFACTORINGS =
      Set.of("rename", "add_parameter", "remove_parameter", "move_parameter", "extract_method");

  private static final Set<String> EDITS =
      Set.of("create_file", "replace_text", "add_declaration", "replace_declaration");

  private static final Set<String> OTHERS = Set.of("inspect", "batch", "undo");

  /** How many lines of Java written in one call make it a large write. */
  static final int LARGE_WRITE = 20;

  /** A redirect into a file: not {@code 2>&1}, not {@code > /dev/null}. */
  private static final Pattern REDIRECT =
      Pattern.compile("(?<![0-9&>])>>?\\s*(?!&|/dev/null)([^\\s;&|]+)");

  /** Commands that change files in place. */
  private static final Pattern WRITES =
      Pattern.compile(
          "(^|[;&|(]\\s*|\\s)(sed\\s+-i|perl\\s+-\\w*i|tee|cp|mv|rm|touch|patch)(\\s|$)");

  /** Commands that read a Java file. */
  private static final Pattern READS =
      Pattern.compile("^\\s*(cat|head|tail|sed\\s+-n|grep|rg|less|awk)\\b.*\\.java\\b");

  public static void main(String[] args) throws Exception {
    List<Path> logs = new ArrayList<>();
    for (String a : args) {
      logs.add(Path.of(a));
    }
    if (logs.isEmpty()) {
      Process git = new ProcessBuilder("git", "symbolic-ref", "--short", "HEAD").start();
      String branch = new String(git.getInputStream().readAllBytes()).strip();
      git.waitFor();
      logs.add(Path.of(".claude", "session-log", SessionLog.logName(branch)));
    }
    List<String> lines = new ArrayList<>();
    for (Path log : logs) {
      if (!Files.exists(log)) {
        System.err.println("no session log " + log);
        System.exit(1);
      }
      lines.addAll(Files.readAllLines(log));
    }
    System.out.print(report(lines));
  }

  /** One call: its last logged line, and whether it ran (a line after it ran was logged). */
  private static final class Call {
    JsonNode event;
    boolean ran;
    boolean failed;
  }

  /** The report on the lines of a log, as Markdown list items. */
  static String report(List<String> lines) {
    Map<String, Call> calls = new LinkedHashMap<>();
    for (String line : lines) {
      JsonNode e;
      try {
        e = JSON.readTree(line);
      } catch (RuntimeException ex) {
        continue;
      }
      String id = e.path("tool_use_id").asString("");
      if (id.isEmpty()) {
        continue;
      }
      Call c = calls.computeIfAbsent(id, k -> new Call());
      String hook = e.path("hook_event_name").asString("");
      if (c.event == null || !hook.equals("PreToolUse")) {
        c.event = e;
      }
      if (hook.equals("PostToolUse") || hook.equals("PostToolUseFailure")) {
        c.ran = true;
        c.failed = hook.equals("PostToolUseFailure");
      }
    }

    int refactorings = 0;
    int edits = 0;
    int refusals = 0;
    List<String> byHand = new ArrayList<>();
    List<String> afterRefusal = new ArrayList<>();
    List<String> large = new ArrayList<>();
    List<String> scratch = new ArrayList<>();
    List<String> noResult = new ArrayList<>();
    Map<String, Integer> reads = new TreeMap<>();
    List<String> pending = new ArrayList<>();
    for (Call c : calls.values()) {
      JsonNode e = c.event;
      String tool = e.path("tool_name").asString("");
      JsonNode input = e.path("tool_input");
      String cwd = e.path("cwd").asString("");
      String result = e.path("result").asString("");
      String what = describe(tool, input, cwd);
      String ours = ours(tool);
      if (!c.ran) {
        noResult.add(what);
        continue;
      }
      if (ours != null) {
        if (c.failed || result.startsWith("refused:")) {
          refusals++;
          pending.add(ours + " (" + result.lines().findFirst().orElse("") + ")");
          continue;
        }
        List<Map.Entry<String, JsonNode>> steps = steps(ours, input);
        for (Map.Entry<String, JsonNode> step : steps) {
          if (REFACTORINGS.contains(step.getKey())) {
            refactorings++;
          } else if (EDITS.contains(step.getKey())) {
            edits++;
          }
          String big = largeStep(step.getKey(), step.getValue());
          if (big != null) {
            large.add(big);
          }
        }
        if (ours.equals("inspect")) {
          count(reads, "inspect");
        }
        if (!steps.isEmpty() && !ours.equals("inspect")) {
          pending.clear();
        }
        continue;
      }
      Boolean writes = writes(tool, input, cwd);
      if (writes == null) {
        scratch.add(what);
      } else if (writes) {
        byHand.add(what);
        int n = handLines(tool, input);
        if (n >= LARGE_WRITE) {
          large.add(what + ": " + n + " lines");
        }
        for (String r : pending) {
          afterRefusal.add(r + ", then " + what);
        }
        pending.clear();
      } else {
        String kind = readKind(tool, input);
        if (kind != null) {
          count(reads, kind);
        }
      }
    }

    StringBuilder out = new StringBuilder();
    out.append("- tool calls: ").append(calls.size()).append("\n");
    out.append("- through jrefactor: ")
        .append(plural(refactorings, "refactoring step"))
        .append(", ")
        .append(plural(edits, "edit step"))
        .append("\n");
    out.append("- refused by jrefactor: ").append(refusals).append("\n");
    list(out, "by hand", byHand);
    list(out, "refused, then done by hand", afterRefusal);
    list(out, "large writes (" + LARGE_WRITE + " lines of Java or more in one call)", large);
    list(out, "written to scratch files", scratch);
    list(out, "denied or failed (no result)", noResult);
    if (!reads.isEmpty()) {
      List<String> parts = new ArrayList<>();
      for (Map.Entry<String, Integer> r : reads.entrySet()) {
        parts.add(r.getKey() + " " + r.getValue());
      }
      out.append("- reads: ").append(String.join(", ", parts)).append("\n");
    }
    return out.toString();
  }

  /** The name of a java-refactor-mcp tool without its server prefix, or null for another tool. */
  private static String ours(String tool) {
    if (!tool.startsWith("mcp__")) {
      return null;
    }
    String name = tool.substring(tool.lastIndexOf("__") + 2);
    return REFACTORINGS.contains(name) || EDITS.contains(name) || OTHERS.contains(name)
        ? name
        : null;
  }

  /** A call's steps, by tool: a batch's, or the call itself. */
  private static List<Map.Entry<String, JsonNode>> steps(String tool, JsonNode input) {
    List<Map.Entry<String, JsonNode>> out = new ArrayList<>();
    if (tool.equals("batch")) {
      for (JsonNode step : input.path("steps").values()) {
        out.add(Map.entry(step.path("tool").asString(""), step.path("args")));
      }
    } else {
      out.add(Map.entry(tool, input));
    }
    return out;
  }

  /**
   * "create_file A.java: 25 lines" when a step wrote {@link #LARGE_WRITE} lines of Java or more.
   */
  private static String largeStep(String tool, JsonNode args) {
    String file = args.path("file").asString("");
    if (!file.endsWith(".java") || !EDITS.contains(tool)) {
      return null;
    }
    String text = args.path(tool.equals("replace_text") ? "new" : "content").asString("");
    int n = lines(text);
    if (n < LARGE_WRITE) {
      return null;
    }
    return tool
        + " "
        + file
        + ": "
        + n
        + " lines"
        + (args.path("overwrite").asBoolean(false) ? ", replacing the file" : "");
  }

  /**
   * Whether a call that is not ours changed files of the project: true by hand, false not at all,
   * null only files outside the working directory (scratch files).
   */
  private static Boolean writes(String tool, JsonNode input, String cwd) {
    switch (tool) {
      case "Edit", "Write", "MultiEdit", "NotebookEdit" -> {
        String path = input.path("file_path").asString(input.path("notebook_path").asString(""));
        return outside(cwd, path) ? null : Boolean.TRUE;
      }
      case "Bash" -> {
        String command = input.path("command").asString("");
        boolean inPlace = WRITES.matcher(command).find();
        List<String> targets = new ArrayList<>();
        Matcher m = REDIRECT.matcher(command);
        while (m.find()) {
          targets.add(m.group(1));
        }
        if (!inPlace && targets.isEmpty()) {
          return false;
        }
        if (!inPlace && targets.stream().allMatch(t -> outside(cwd, t))) {
          return null;
        }
        return true;
      }
      default -> {
        return false;
      }
    }
  }

  /** The lines of Java a write by hand put in. */
  private static int handLines(String tool, JsonNode input) {
    if (tool.equals("Bash")) {
      String command = input.path("command").asString("");
      return command.contains(".java") ? (int) command.lines().count() : 0;
    }
    if (!input.path("file_path").asString("").endsWith(".java")) {
      return 0;
    }
    int n =
        lines(input.path("content").asString("")) + lines(input.path("new_string").asString(""));
    for (JsonNode edit : input.path("edits").values()) {
      n += lines(edit.path("new_string").asString(""));
    }
    return n;
  }

  private static String readKind(String tool, JsonNode input) {
    if (tool.equals("Read")) {
      return input.path("file_path").asString("").endsWith(".java") ? "Read java" : "Read other";
    }
    if (tool.equals("Bash") && READS.matcher(input.path("command").asString("")).find()) {
      return "Bash java";
    }
    return null;
  }

  /** The tool and the file or command it names, paths relative to {@code cwd}. */
  private static String describe(String tool, JsonNode input, String cwd) {
    String name = ours(tool) != null ? ours(tool) : tool;
    if (input.has("command")) {
      String first = input.path("command").asString("").lines().findFirst().orElse("");
      return name + ": " + (first.length() > 100 ? first.substring(0, 100) + "..." : first);
    }
    for (String key : List.of("file_path", "notebook_path", "file")) {
      String path = input.path(key).asString("");
      if (!path.isEmpty()) {
        String prefix = cwd.endsWith("/") ? cwd : cwd + "/";
        return name
            + " "
            + (!cwd.isEmpty() && path.startsWith(prefix) ? path.substring(prefix.length()) : path);
      }
    }
    return name;
  }

  /** Whether {@code path} is absolute and outside {@code cwd}. */
  private static boolean outside(String cwd, String path) {
    String prefix = cwd.endsWith("/") ? cwd : cwd + "/";
    return !cwd.isEmpty() && path.startsWith("/") && !path.startsWith(prefix);
  }

  private static int lines(String s) {
    return s.isBlank() ? 0 : (int) s.stripTrailing().lines().count();
  }

  private static void count(Map<String, Integer> into, String key) {
    into.merge(key, 1, Integer::sum);
  }

  private static String plural(int n, String what) {
    return n + " " + what + (n == 1 ? "" : "s");
  }

  private static void list(StringBuilder out, String title, List<String> items) {
    out.append("- ").append(title).append(": ").append(items.size()).append("\n");
    for (String item : items) {
      out.append("  - ").append(item).append("\n");
    }
  }
}
