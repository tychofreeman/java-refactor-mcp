package io.github.tychofreeman.jrefactor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The tools from a shell, one call per process, for a session that cannot run the MCP server. The
 * same definitions, arguments and results as the server; {@link #USAGE} says how to call them.
 */
final class Cli {

  private Cli() {}

  private static final ObjectMapper JSON = Json.MAPPER;

  static final String USAGE =
      """
      usage: jrefactor list [tool...]                 (descriptions and arguments)
             jrefactor [-C dir] <tool> key=value...   (typed by the tool's schema)
             jrefactor [-C dir] <tool> '<json>' | @file | -
             jrefactor [-C dir] @file | -              (a JSON object with "tool")
             jrefactor [-C dir] undo

      The project is the nearest directory with a pom.xml at or above dir (default: the
      current directory); files are relative to it. key_file=path gives the text argument
      key from a file (content_file, new_file, old_file); key_file=- gives it from stdin, so
      a heredoc can pass it (one argument per call). @file reads the JSON arguments from a
      file, - from stdin. After the JSON object, a line @@name starts a section, its text
      the lines up to the next such line or the end, the last newline dropped; a JSON
      string "@name" is that text, so code goes in unescaped. undo reverts the last call,
      also one made by an earlier run.
      Exit status: 0 done, 2 refused (nothing changed), 1 error.
      """;

  public static void main(String[] args) {
    String log = System.getenv("JREFACTOR_LOG");
    if (log == null || log.isEmpty()) {
      int status = run(List.of(args), Path.of("").toAbsolutePath(), cacheDir(), System.out);
      System.out.flush();
      System.exit(status);
    }
    Path cwd = Path.of("").toAbsolutePath();
    String stdin = List.of(args).contains("-") ? keepStdin() : "";
    ByteArrayOutputStream copy = new ByteArrayOutputStream();
    OutputStream both =
        new OutputStream() {
          @Override
          public void write(int b) {
            System.out.write(b);
            copy.write(b);
          }

          @Override
          public void write(byte[] b, int off, int len) {
            System.out.write(b, off, len);
            copy.write(b, off, len);
          }
        };
    PrintStream out = new PrintStream(both, true, System.out.charset());
    int status = run(List.of(args), cwd, cacheDir(), out);
    out.flush();
    System.out.flush();
    appendLog(Path.of(log), List.of(args), stdin, cwd, copy.toString(System.out.charset()), status);
    System.exit(status);
  }

  /**
   * Runs one command line {@code args} from directory {@code cwd}, keeping undo stacks, the
   * classpath and the last type check in {@code cacheDir}; returns the exit status.
   */
  static int run(List<String> args, Path cwd, Path cacheDir, PrintStream out) {
    List<String> rest = new ArrayList<>(args);
    Path dir = cwd;
    if (rest.size() >= 2 && rest.get(0).equals("-C")) {
      dir = cwd.resolve(rest.get(1)).toAbsolutePath().normalize();
      rest = rest.subList(2, rest.size());
    }
    if (rest.isEmpty() || rest.get(0).equals("-h") || rest.get(0).equals("--help")) {
      out.print(USAGE);
      return rest.isEmpty() ? 1 : 0;
    }
    String name = rest.get(0);
    List<String> words = rest.subList(1, rest.size());
    try {
      if (name.equals("list")) {
        list(words, out);
        return 0;
      }
      Tools.Def def;
      Map<String, Object> values;
      if (isJson(name) && words.isEmpty()) {
        Map<String, Object> json = new LinkedHashMap<>(readJson(name));
        if (!(json.remove("tool") instanceof String tool)) {
          throw new Refusal(
              "A JSON object given alone names its tool, as \"tool\": {\"tool\": \"rename\", ...};"
                  + " or put the tool's name before it.");
        }
        def = Tools.named(tool);
        values = withFiles(def, json, name.equals("-"));
      } else {
        def = Tools.named(name);
        values = arguments(def, words);
      }
      Path at = root(dir).toAbsolutePath().normalize();
      String cached = cacheName(at);
      SourceProject project = SourceProject.load(at, classpathCache(cacheDir, at));
      Path stack = cacheDir.resolve(cached + ".json");
      Path count = cacheDir.resolve(cached + "-count.json");
      Path refused = cacheDir.resolve(cached + "-batch.json");
      Journal.load(project, stack);
      TypeCheck.load(project, count);
      Batch.load(project, refused);
      try {
        Outcome outcome = Tools.call(def, project, values);
        Journal.save(project, stack);
        out.println(outcome.render());
        if (!outcome.exceptional().isEmpty()) {
          out.println();
          out.println(outcome.renderExceptional());
        }
        return 0;
      } finally {
        TypeCheck.save(project, count);
        Batch.save(project, refused);
      }
    } catch (Refusal refusal) {
      out.println(Refusal.PREFIX + refusal.getMessage());
      return 2;
    } catch (Exception e) {
      out.println("error (not a refusal): " + e);
      e.printStackTrace(System.err);
      return 1;
    }
  }

  /** Each tool's name and description; with {@code names}, only those, with their schemas. */
  private static void list(List<String> names, PrintStream out) {
    if (names.isEmpty()) {
      for (Tools.Def def : Tools.ALL) {
        out.println(def.name() + ": " + def.description().lines().findFirst().orElse(""));
      }
      return;
    }
    for (String name : names) {
      Tools.Def def = Tools.named(name);
      out.println(def.name());
      out.println(def.description());
      out.println(def.schema());
      out.println();
    }
  }

  /**
   * The arguments of {@code def} from the words after its name: one JSON object (inline, {@code
   * @file} or {@code -} for stdin, its "tool" key, if any, naming {@code def}), or {@code key=value}
   * pairs typed by the tool's schema; then each {@code key_file} read into {@code key}.
   */
  static Map<String, Object> arguments(Tools.Def def, List<String> words) throws IOException {
    Map<String, Object> values = new LinkedHashMap<>();
    if (words.size() == 1 && isJson(words.get(0))) {
      values.putAll(readJson(words.get(0)));
      Object tool = values.remove("tool");
      if (tool != null && !Tools.named(String.valueOf(tool)).name().equals(def.name())) {
        throw new Refusal(
            "the JSON object names the tool "
                + tool
                + ", and the command line "
                + def.name()
                + ". Leave out one of them.");
      }
    } else {
      JsonNode properties = JSON.readTree(def.schema()).path("properties");
      for (String word : words) {
        int eq = word.indexOf('=');
        if (eq <= 0) {
          throw new Refusal(
              "Arguments are key=value pairs, or one JSON object ('{...}', @file or -); got "
                  + word
                  + ".");
        }
        String key = word.substring(0, eq);
        String value = word.substring(eq + 1);
        String type = properties.path(key).path("type").asString("string");
        values.put(key, typed(key, value, type));
      }
    }
    return withFiles(def, values, words.size() == 1 && words.get(0).equals("-"));
  }

  /** One JSON object: {@code word} itself, the file after its {@code @}, or stdin for {@code -}. */
  private static Map<String, Object> readJson(String word) throws IOException {
    String text =
        word.equals("-")
            ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
            : word.startsWith("@") ? Files.readString(Path.of(word.substring(1))) : word;
    return withSections(text);
  }

  /** A line that starts a code section: {@code @@name} alone on it. */
  private static final Pattern SECTION_LINE =
      Pattern.compile("(?m)^@@([A-Za-z_][A-Za-z0-9_-]*)[ \\t]*$");

  /** A section's name, as a string {@code "@name"} gives it. */
  private static final Pattern SECTION_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");

  /**
   * The JSON object {@code text} begins with, each string {@code "@name"} of it replaced by the
   * text of section {@code name} after the object: {@code @@name} alone on a line and the lines
   * after it, to the next section or the end, its last newline dropped. A section no string names,
   * a section given twice, and a name with no section beside sections that are given are refused.
   */
  private static Map<String, Object> withSections(String text) {
    Matcher line = SECTION_LINE.matcher(text);
    List<MatchResult> starts = new ArrayList<>();
    while (line.find()) {
      starts.add(line.toMatchResult());
    }
    if (starts.isEmpty()) {
      try {
        return JSON.readValue(text, new TypeReference<Map<String, Object>>() {});
      } catch (JacksonException e) {
        throw new Refusal("the JSON: " + e.getOriginalMessage());
      }
    }
    Map<String, String> sections = new LinkedHashMap<>();
    for (int i = 0; i < starts.size(); i++) {
      MatchResult start = starts.get(i);
      int end = i + 1 < starts.size() ? starts.get(i + 1).start() : text.length();
      String body = text.substring(start.end(), end);
      body = body.startsWith("\n") ? body.substring(1) : body;
      body = body.endsWith("\n") ? body.substring(0, body.length() - 1) : body;
      if (sections.putIfAbsent(start.group(1), body) != null) {
        throw new Refusal("section @@" + start.group(1) + " is given twice.");
      }
    }
    Map<String, Object> json;
    try {
      json =
          JSON.readValue(
              text.substring(0, starts.get(0).start()),
              new TypeReference<Map<String, Object>>() {});
    } catch (JacksonException e) {
      throw new Refusal(
          "the JSON before section @@" + starts.get(0).group(1) + ": " + e.getOriginalMessage());
    }
    Map<String, Boolean> used = new LinkedHashMap<>();
    json.replaceAll((key, value) -> replaceSections(value, sections, used));
    for (String name : sections.keySet()) {
      if (!used.containsKey(name)) {
        throw new Refusal(
            "section @@" + name + " is not used: no string of the JSON is \"@" + name + "\".");
      }
    }
    for (Map.Entry<String, Boolean> name : used.entrySet()) {
      if (!name.getValue()) {
        throw new Refusal(
            "the JSON names section @@"
                + name.getKey()
                + ", which is not given (did a heredoc end before it?).");
      }
    }
    return json;
  }

  /**
   * {@code value} with each string {@code "@name"} that names a section replaced by its text,
   * marking the sections it used true in {@code used} and the names it has no section for false.
   */
  private static Object replaceSections(
      Object value, Map<String, String> sections, Map<String, Boolean> used) {
    switch (value) {
      case String s when s.startsWith("@") -> {
        String name = s.substring(1);
        if (sections.containsKey(name)) {
          used.put(name, true);
          return sections.get(name);
        }
        if (SECTION_NAME.matcher(name).matches()) {
          used.putIfAbsent(name, false);
        }
      }
      case List<?> list -> {
        List<Object> replaced = new ArrayList<>();
        for (Object element : list) {
          replaced.add(replaceSections(element, sections, used));
        }
        return replaced;
      }
      case Map<?, ?> map -> {
        Map<Object, Object> replaced = new LinkedHashMap<>();
        map.forEach((key, element) -> replaced.put(key, replaceSections(element, sections, used)));
        return replaced;
      }
      case null, default -> {}
    }
    return value;
  }

  /**
   * {@code values} with each {@code key_file} that {@code def} does not take read into {@code key}:
   * from the file, or from stdin for {@code -}, which one argument may use, and none when {@code
   * stdinRead} (the JSON object came from it).
   */
  private static Map<String, Object> withFiles(
      Tools.Def def, Map<String, Object> values, boolean stdinRead) throws IOException {
    for (String key : List.copyOf(values.keySet())) {
      String base = key.endsWith("_file") ? key.substring(0, key.length() - "_file".length()) : "";
      if (!base.isEmpty() && !def.args().contains(key) && def.args().contains(base)) {
        String path = String.valueOf(values.remove(key));
        if (path.equals("-")) {
          if (stdinRead) {
            throw new Refusal(
                "only one argument can come from stdin; give "
                    + key
                    + " a file's path, or send the arguments as one JSON object.");
          }
          stdinRead = true;
          values.put(base, new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
        } else {
          values.put(base, Files.readString(Path.of(path)));
        }
      }
    }
    return values;
  }

  private static boolean isJson(String word) {
    return word.equals("-") || word.startsWith("@") || word.stripLeading().startsWith("{");
  }

  /** {@code value} as the schema's {@code type} says: a number, a flag, JSON, or the text. */
  private static Object typed(String key, String value, String type) {
    switch (type) {
      case "integer" -> {
        try {
          return Long.parseLong(value);
        } catch (NumberFormatException e) {
          throw new Refusal(key + " must be a whole number; got " + value + ".");
        }
      }
      case "boolean" -> {
        if (!value.equals("true") && !value.equals("false")) {
          throw new Refusal(key + " must be true or false; got " + value + ".");
        }
        return Boolean.parseBoolean(value);
      }
      case "array", "object" -> {
        try {
          return JSON.readValue(value, Object.class);
        } catch (JacksonException e) {
          throw new Refusal(key + " must be JSON (an " + type + "): " + e.getOriginalMessage());
        }
      }
      default -> {
        return value;
      }
    }
  }

  /** The nearest directory at or above {@code dir} with a {@code pom.xml}, else {@code dir}. */
  static Path root(Path dir) {
    for (Path d = dir; d != null; d = d.getParent()) {
      if (Files.isRegularFile(d.resolve("pom.xml"))) {
        return d;
      }
    }
    return dir;
  }

  /**
   * The start of the cache files' names for the project at {@code root} (its undo stack, classpath
   * and last type check): its name and a hash of its path.
   */
  private static String cacheName(Path root) {
    return root.getFileName()
        + "-"
        + Hashes.sha256Hex(root.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16);
  }

  /** The file that keeps the classpath of the project at {@code root} in {@code cacheDir}. */
  static Path classpathCache(Path cacheDir, Path root) {
    return cacheDir.resolve(cacheName(root) + "-classpath.txt");
  }

  /**
   * Where undo stacks, classpaths and type checks are kept: {@code $XDG_CACHE_HOME/jrefactor}, or
   * {@code ~/.cache/jrefactor}.
   */
  static Path cacheDir() {
    String cache = System.getenv("XDG_CACHE_HOME");
    Path base =
        cache != null && !cache.isEmpty()
            ? Path.of(cache)
            : Path.of(System.getProperty("user.home"), ".cache");
    return base.resolve("jrefactor");
  }

  /**
   * The session log line ({@link SessionLog#entry}) of a run of {@code args} from {@code cwd} that
   * printed {@code result} and exited with {@code status}, as a shell call of {@code bin/jrefactor}
   * that {@link SessionReport} reads: each JSON argument written out as its object, {@code stdin}
   * for {@code -}.
   */
  static String logLine(
      List<String> args, String stdin, Path cwd, String result, int status, Instant now) {
    ObjectNode hook = SessionLog.JSON.createObjectNode();
    hook.put("hook_event_name", status == 0 ? "PostToolUse" : "PostToolUseFailure");
    hook.put("tool_use_id", "jrefactor-" + UUID.randomUUID());
    hook.put("tool_name", "Bash");
    hook.putObject("tool_input").put("command", command(args, stdin));
    hook.put("cwd", cwd.toString());
    hook.put(status == 0 ? "tool_response" : "error", result.stripTrailing());
    return SessionLog.entry(SessionLog.JSON.writeValueAsString(hook), now);
  }

  /** Reads stdin whole and puts it back for {@link #run}; "" when it cannot be read. */
  private static String keepStdin() {
    try {
      byte[] in = System.in.readAllBytes();
      System.setIn(new ByteArrayInputStream(in));
      return new String(in, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  /** Appends the log line of a run to {@code log}, the file JREFACTOR_LOG names. */
  private static void appendLog(
      Path log, List<String> args, String stdin, Path cwd, String result, int status) {
    try {
      Files.writeString(
          log,
          logLine(args, stdin, cwd, result, status, Instant.now()) + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (Exception | Error e) {
      // A log line lost is better than a run changed.
    }
  }

  /**
   * {@code bin/jrefactor} and {@code args} as one shell command, each JSON argument as its object.
   */
  private static String command(List<String> args, String stdin) {
    StringBuilder out = new StringBuilder("bin/jrefactor");
    for (String word : args) {
      out.append(' ').append(isJson(word) ? jsonWord(word, stdin) : quoted(word));
    }
    return out.toString();
  }

  /**
   * A JSON argument as one shell word: its object, sections filled in, on one line in single
   * quotes, with the characters that end a word or a command written as JSON escapes; the word
   * itself, quoted, when it cannot be read.
   */
  private static String jsonWord(String word, String stdin) {
    try {
      String text =
          word.equals("-")
              ? stdin
              : word.startsWith("@") ? Files.readString(Path.of(word.substring(1))) : word;
      String json = JSON.writeValueAsString(withSections(text));
      return "'"
          + json.replace("'", "\\u0027")
              .replace(";", "\\u003b")
              .replace("&", "\\u0026")
              .replace("|", "\\u007c")
          + "'";
    } catch (IOException | RuntimeException e) {
      return quoted(word);
    }
  }

  /** {@code word} as one shell word: in single quotes unless every character is safe bare. */
  private static String quoted(String word) {
    if (word.matches("[A-Za-z0-9_./=:,@+-]+")) {
      return word;
    }
    return "'" + word.replace("'", "'\\''") + "'";
  }
}
