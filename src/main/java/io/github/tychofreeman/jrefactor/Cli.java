package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The tools from a shell, one call per process, for a session that cannot run the MCP server. The
 * same definitions, arguments and results as the server; {@link #USAGE} says how to call them.
 */
final class Cli {

  private Cli() {}

  private static final ObjectMapper JSON = new ObjectMapper();

  static final String USAGE =
      """
      usage: jrefactor list [tool...]                 (descriptions and arguments)
             jrefactor [-C dir] <tool> key=value...   (typed by the tool's schema)
             jrefactor [-C dir] <tool> '<json>' | @file | -
             jrefactor [-C dir] undo

      The project is the nearest directory with a pom.xml at or above dir (default: the
      current directory); files are relative to it. key_file=path gives the text argument
      key from a file (content_file, new_file, old_file). @file reads the JSON arguments
      from a file, - from stdin. undo reverts the last call, also one made by an earlier run.
      Exit status: 0 done, 2 refused (nothing changed), 1 error.
      """;

  public static void main(String[] args) {
    int status = run(List.of(args), Path.of("").toAbsolutePath(), cacheDir(), System.out);
    System.out.flush();
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
      Tools.Def def = Tools.named(name);
      Map<String, Object> values = arguments(def, words);
      Path at = root(dir).toAbsolutePath().normalize();
      String cached = cacheName(at);
      SourceProject project = SourceProject.load(at, cacheDir.resolve(cached + "-classpath.txt"));
      Path stack = cacheDir.resolve(cached + ".json");
      Path count = cacheDir.resolve(cached + "-count.json");
      Journal.load(project, stack);
      TypeCheck.load(project, count);
      try {
        Tools.Outcome outcome = Tools.call(def, project, values);
        Journal.save(project, stack);
        out.println(outcome.render());
        return 0;
      } finally {
        TypeCheck.save(project, count);
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
   * @file} or {@code -} for stdin), or {@code key=value} pairs typed by the tool's schema; then each
   * {@code key_file} read into {@code key}.
   */
  static Map<String, Object> arguments(Tools.Def def, List<String> words) throws IOException {
    Map<String, Object> values = new LinkedHashMap<>();
    if (words.size() == 1 && isJson(words.get(0))) {
      String word = words.get(0);
      String text =
          word.equals("-")
              ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
              : word.startsWith("@") ? Files.readString(Path.of(word.substring(1))) : word;
      values.putAll(JSON.readValue(text, new TypeReference<Map<String, Object>>() {}));
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
    for (String key : List.copyOf(values.keySet())) {
      String base = key.endsWith("_file") ? key.substring(0, key.length() - "_file".length()) : "";
      if (!base.isEmpty() && !def.args().contains(key) && def.args().contains(base)) {
        Object path = values.remove(key);
        values.put(base, Files.readString(Path.of(String.valueOf(path))));
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
        return JSON.readValue(value, Object.class);
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
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(root.toString().getBytes(StandardCharsets.UTF_8));
      return root.getFileName() + "-" + HexFormat.of().formatHex(hash, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Where undo stacks, classpaths and type checks are kept: {@code $XDG_CACHE_HOME/jrefactor}, or
   * {@code ~/.cache/jrefactor}.
   */
  private static Path cacheDir() {
    String cache = System.getenv("XDG_CACHE_HOME");
    Path base =
        cache != null && !cache.isEmpty()
            ? Path.of(cache)
            : Path.of(System.getProperty("user.home"), ".cache");
    return base.resolve("jrefactor");
  }
}
