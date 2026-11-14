package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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

  private static final ObjectMapper JSON = SessionLog.JSON;

  /** The server's tools by name. */
  private static final Map<String, Tools.Kind> KINDS = kinds();

  private static Map<String, Tools.Kind> kinds() {
    Map<String, Tools.Kind> out = new LinkedHashMap<>();
    for (Tools.Def def : Tools.ALL) {
      out.put(def.name(), def.kind());
    }
    Tools.ALIASES.forEach((alias, def) -> out.put(alias, def.kind()));
    return out;
  }

  /** How many lines of Java written in one call make it a large write. */
  static final int LARGE_WRITE = 20;

  /** A redirect into a file: not {@code 2>&1}, not {@code > /dev/null}. */
  private static final Pattern REDIRECT =
      Pattern.compile("(?<![0-9&>])>>?\\s*(?!&|/dev/null)([^\\s;&|]+)");

  /**
   * Commands that change files in place, any file (Java as the write guard judges it: {@link
   * Guard#use}).
   */
  private static final Pattern WRITES =
      Pattern.compile(
          "(^|[;&|(]\\s*|\\s)(sed\\s+-i|perl\\s+-\\w*i|tee|cp|mv|rm|touch|patch)(\\s|$)");

  /** The start of a heredoc, with its delimiter (group 2). */
  private static final Pattern HEREDOC = Pattern.compile("<<-?\\s*(['\"]?)(\\w+)\\1");

  /** A quoted string in a command. */
  private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"(\\\\.|[^\"\\\\])*\"");

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
    int cliCalls = 0;
    List<String> byHand = new ArrayList<>();
    List<String> afterRefusal = new ArrayList<>();
    List<String> large = new ArrayList<>();
    List<String> scratch = new ArrayList<>();
    List<String> noResult = new ArrayList<>();
    Map<String, Integer> reads = new TreeMap<>();
    List<String> pending = new ArrayList<>();
    Typed typed = new Typed();
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
        if (c.failed || result.startsWith(Refusal.PREFIX)) {
          refusals++;
          pending.add(ours + " (" + result.lines().findFirst().orElse("") + ")");
          continue;
        }
        List<Map.Entry<String, JsonNode>> steps = steps(ours, input);
        for (Map.Entry<String, JsonNode> step : steps) {
          typed.add(step.getKey(), step.getValue());
          Tools.Kind kind = KINDS.get(step.getKey());
          if (kind == Tools.Kind.REFACTORING) {
            refactorings++;
          } else if (kind == Tools.Kind.EDIT) {
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
      CliCalls viaCli = tool.equals("Bash") ? cli(input.path("command").asString(""), cwd) : null;
      if (viaCli != null && viaCli.calls() > 0) {
        cliCalls += viaCli.calls();
        if (result.lines().anyMatch(l -> l.startsWith(Refusal.PREFIX))) {
          refusals++;
          pending.add("bin/jrefactor (" + result.lines().findFirst().orElse("") + ")");
          continue;
        }
        for (Map.Entry<String, JsonNode> step : viaCli.steps()) {
          typed.add(step.getKey(), step.getValue());
          Tools.Kind kind = KINDS.get(step.getKey());
          if (kind == Tools.Kind.REFACTORING) {
            refactorings++;
          } else if (kind == Tools.Kind.EDIT) {
            edits++;
          }
          String big = largeStep(step.getKey(), step.getValue());
          if (big != null) {
            large.add(big);
          }
        }
        if (!viaCli.steps().isEmpty()) {
          pending.clear();
        }
        continue;
      }
      Boolean writes = writes(tool, input, cwd);
      if (writes == null) {
        scratch.add(what);
      } else if (writes) {
        byHand.add(what);
        int n = handLines(tool, input, cwd);
        if (n >= LARGE_WRITE) {
          large.add(what + ": " + n + " lines");
        }
        for (String r : pending) {
          afterRefusal.add(r + ", then " + what);
        }
        pending.clear();
      } else {
        String kind = readKind(tool, input, cwd);
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
    if (!typed.carriers.isEmpty()) {
      List<String> parts = new ArrayList<>();
      typed.carriers.forEach((carrier, n) -> parts.add(carrier + " " + n));
      out.append("- edit steps by what carried the code: ")
          .append(String.join(", ", parts))
          .append("\n- Java typed in edits: ")
          .append(plural(typed.java, "character"))
          .append(", ")
          .append(typed.inBuild)
          .append(" in build strings; names in build: ")
          .append(plural(typed.names, "character"))
          .append("\n");
    }
    if (cliCalls > 0) {
      out.append("- bin/jrefactor calls (in Bash): ").append(cliCalls).append("\n");
    }
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
    return KINDS.containsKey(name) ? name : null;
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

  /** A run of bin/jrefactor in a shell command, and the words after it up to the next command. */
  private static final Pattern CLI =
      Pattern.compile("(?:^|[\\s;&|(])(?:\\S*/)?bin/jrefactor\\s+([^;&|\\n]*)");

  /** The bin/jrefactor runs of a shell command: how many, and the steps the command shows. */
  private record CliCalls(int calls, List<Map.Entry<String, JsonNode>> steps) {}

  /**
   * The runs of bin/jrefactor in {@code command}, each with its tool and arguments as written
   * (key=value pairs, a JSON object, or an {@code @file} still readable from {@code cwd}); a run
   * whose arguments the log cannot show counts as a call with no steps, and list and undo as calls
   * that change nothing.
   */
  private static CliCalls cli(String command, String cwd) {
    Matcher m = CLI.matcher(command);
    int calls = 0;
    List<Map.Entry<String, JsonNode>> out = new ArrayList<>();
    while (m.find()) {
      calls++;
      List<String> words = words(m.group(1));
      int i = 0;
      while (i < words.size() && (words.get(i).equals("--rebuild") || words.get(i).equals("-C"))) {
        i += words.get(i).equals("-C") ? 2 : 1;
      }
      if (i >= words.size()) {
        continue;
      }
      String first = words.get(i);
      JsonNode json = json(first, cwd);
      String tool = json != null ? json.path("tool").asString("") : first;
      if (json == null && i + 1 < words.size()) {
        json = json(words.get(i + 1), cwd);
      }
      if (!KINDS.containsKey(tool)) {
        continue;
      }
      if (json == null) {
        ObjectNode args = JSON.createObjectNode();
        for (String w : words.subList(i + 1, words.size())) {
          int eq = w.indexOf('=');
          if (eq > 0) {
            args.put(w.substring(0, eq), w.substring(eq + 1));
          }
        }
        json = args;
      }
      out.addAll(steps(tool, json));
    }
    return new CliCalls(calls, out);
  }

  /**
   * {@code word} as a JSON object: inline, or an {@code @file} that can still be read; else null.
   */
  private static JsonNode json(String word, String cwd) {
    try {
      if (word.startsWith("{")) {
        return JSON.readTree(word);
      }
      if (word.startsWith("@") && !word.contains("$")) {
        Path file = Path.of(cwd).resolve(word.substring(1));
        return Files.isRegularFile(file) ? JSON.readTree(Files.readString(file)) : null;
      }
    } catch (RuntimeException | java.io.IOException e) {
      return null;
    }
    return null;
  }

  /** The words of {@code text} as a shell splits them, quotes removed; up to a redirect or pipe. */
  private static List<String> words(String text) {
    List<String> out = new ArrayList<>();
    StringBuilder w = new StringBuilder();
    char quote = 0;
    boolean any = false;
    for (char c : text.toCharArray()) {
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        } else {
          w.append(c);
        }
      } else if (c == '\'' || c == '"') {
        quote = c;
        any = true;
      } else if (Character.isWhitespace(c)) {
        if (any || w.length() > 0) {
          out.add(w.toString());
        }
        w.setLength(0);
        any = false;
      } else if (c == '>' || c == '<') {
        break;
      } else {
        w.append(c);
      }
    }
    if (any || w.length() > 0) {
      out.add(w.toString());
    }
    return out;
  }

  /**
   * "create_file A.java: 25 lines" when a step wrote {@link #LARGE_WRITE} lines of Java or more.
   */
  private static String largeStep(String tool, JsonNode args) {
    String file = args.path("file").asString("");
    if (!file.endsWith(".java") || KINDS.get(tool) != Tools.Kind.EDIT) {
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
   * null only files outside the working directory (scratch files). A shell command writes the files
   * it redirects into, any file with {@link #WRITES}, and Java as the write guard judges it.
   */
  private static Boolean writes(String tool, JsonNode input, String cwd) {
    switch (tool) {
      case "Edit", "Write", "MultiEdit", "NotebookEdit" -> {
        String path = input.path("file_path").asString(input.path("notebook_path").asString(""));
        return outside(cwd, path) ? null : Boolean.TRUE;
      }
      case "Bash" -> {
        String command = input.path("command").asString("");
        String shell = unquoted(command);
        boolean inPlace = WRITES.matcher(shell).find();
        List<String> targets = targets(shell);
        Guard.Use java = Guard.use(command, cwd, true);
        if (java != null) {
          targets.add(java.path());
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

  /**
   * The lines of Java a write by hand put in; a shell command's, when it writes Java as the write
   * guard judges it.
   */
  private static int handLines(String tool, JsonNode input, String cwd) {
    if (tool.equals("Bash")) {
      String command = input.path("command").asString("");
      return Guard.use(command, cwd, true) != null ? (int) command.lines().count() : 0;
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

  /** The files a command redirects its output into. */
  private static List<String> targets(String shell) {
    List<String> out = new ArrayList<>();
    Matcher m = REDIRECT.matcher(shell);
    while (m.find()) {
      out.add(m.group(1));
    }
    return out;
  }

  /**
   * A command without its quoted text and heredoc bodies: what the shell runs, not the text it
   * passes, so a {@code >} in a pattern or a script is not a redirect.
   */
  private static String unquoted(String command) {
    StringBuilder out = new StringBuilder();
    String delimiter = null;
    for (String line : command.split("\n", -1)) {
      if (delimiter != null) {
        if (line.strip().equals(delimiter)) {
          delimiter = null;
        }
        continue;
      }
      Matcher h = HEREDOC.matcher(line);
      if (h.find()) {
        delimiter = h.group(2);
      }
      out.append(QUOTED.matcher(line).replaceAll("''")).append('\n');
    }
    return out.toString();
  }

  /**
   * The kind of a read, for the counts: a Bash command that reads Java as the read guard judges it.
   */
  private static String readKind(String tool, JsonNode input, String cwd) {
    if (tool.equals("Read")) {
      return input.path("file_path").asString("").endsWith(".java") ? "Read java" : "Read other";
    }
    if (tool.equals("Bash") && Guard.use(input.path("command").asString(""), cwd, false) != null) {
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

  /**
   * What carried the code of the edit steps to Java files (build, surround, constructor, ...,
   * content, new), the Java typed in them (content, new, declaration, and the strings of build that
   * are statements), and the characters of the other strings given to build (names, arguments,
   * conditions).
   */
  private static final class Typed {
    final Map<String, Integer> carriers = new TreeMap<>();
    int java;
    int inBuild;
    int names;

    void add(String tool, JsonNode args) {
      if (KINDS.get(tool) != Tools.Kind.EDIT || !args.path("file").asString("").endsWith(".java")) {
        return;
      }
      String carrier = "other";
      for (String key :
          List.of(
              "build",
              "surround",
              "constructor",
              "implement",
              "test_for",
              "declaration",
              "content",
              "new")) {
        if (args.has(key)) {
          carrier = key;
          break;
        }
      }
      count(carriers, carrier);
      for (String key : List.of("content", "new", "declaration")) {
        java += args.path(key).asString("").length();
      }
      built(args.path("build"));
    }

    private void built(JsonNode node) {
      if (node.isString()) {
        java += node.asString().length();
        inBuild += node.asString().length();
      } else if (node.isArray()) {
        for (JsonNode entry : node.values()) {
          built(entry);
        }
      } else if (node.isObject()) {
        for (Map.Entry<String, JsonNode> key : node.properties()) {
          if (key.getKey().equals("body")) {
            built(key.getValue());
          } else {
            named(key.getValue());
          }
        }
      }
    }

    private void named(JsonNode node) {
      if (node.isString()) {
        names += node.asString().length();
      }
      for (JsonNode n : node.isArray() ? node.values() : List.<JsonNode>of()) {
        named(n);
      }
    }
  }
}
