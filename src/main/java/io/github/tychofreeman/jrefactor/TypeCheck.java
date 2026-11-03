package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jdt.core.compiler.IProblem;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The gate every refactoring passes: count the compile errors in the whole project before and after
 * an edit, and refuse when the edit adds any.
 */
final class TypeCheck {

  private TypeCheck() {}

  /** A count of errors, with the project and a hash of each file's text it was made from. */
  record Counted(SourceProject project, Map<Path, String> hashes, Map<String, Integer> counts) {}

  /**
   * Compile errors in the project as it is on disk, counted per file and message. Keys are {@code
   * "<file> | <message>"}; line numbers are left out on purpose, so an edit that only moves code
   * does not count as a new error. When every file's text is what the last count saw, that count is
   * returned without parsing: an edit's count after is the next edit's count before.
   */
  static Map<String, Integer> errors(SourceProject project) throws IOException {
    List<Path> files = project.javaFiles();
    Map<Path, String> hashes = hashes(files);
    Counted last = project.session().lastCount;
    if (last != null && last.project().equals(project) && last.hashes().equals(hashes)) {
      return new TreeMap<>(last.counts());
    }
    Map<String, Integer> counts = count(project, files);
    project.session().lastCount = new Counted(project, hashes, new TreeMap<>(counts));
    return counts;
  }

  /** A SHA-256 of each file's bytes, to tell whether any changed since a count. */
  private static Map<Path, String> hashes(List<Path> files) throws IOException {
    Map<Path, String> out = new HashMap<>();
    try {
      for (Path file : files) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        out.put(file, HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file))));
      }
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    return out;
  }

  /** {@link #errors} counted from scratch, never from the last count: what a fresh load gives. */
  static Map<String, Integer> uncached(SourceProject project) throws IOException {
    return count(project, project.javaFiles());
  }

  private static Map<String, Integer> count(SourceProject project, List<Path> files) {
    Map<String, Integer> counts = new TreeMap<>();
    project.session().parses++;
    Jdt.parseAll(
        project,
        files,
        (file, cu) -> {
          String rel = project.relative(file);
          for (IProblem problem : cu.getProblems()) {
            if (problem.isError()) {
              counts.merge(rel + " | " + problem.getMessage(), 1, Integer::sum);
            }
          }
        });
    return counts;
  }

  /** Entries of {@code after} that have more occurrences than in {@code before}. */
  static List<String> introduced(Map<String, Integer> before, Map<String, Integer> after) {
    List<String> out = new ArrayList<>();
    for (Map.Entry<String, Integer> e : after.entrySet()) {
      int extra = e.getValue() - before.getOrDefault(e.getKey(), 0);
      if (extra > 0) {
        out.add(e.getKey() + (extra > 1 ? " (x" + extra + ")" : ""));
      }
    }
    return out;
  }

  /** How many times {@link #errors} has parsed {@code project}, for tests of the reuse. */
  static int parses(SourceProject project) {
    return project.session().parses;
  }

  /**
   * Writes {@code project}'s last count to {@code file}, with the source roots and classpath it was
   * made with, so a later process can {@link #load} it: the command line runs one call per process.
   */
  static void save(SourceProject project, Path file) throws IOException {
    Counted last = project.session().lastCount;
    if (last == null) {
      return;
    }
    ObjectNode out = JSON.createObjectNode();
    out.put("build", build(project));
    ObjectNode hashes = out.putObject("files");
    last.hashes().forEach((path, hash) -> hashes.put(path.toString(), hash));
    ObjectNode counts = out.putObject("counts");
    last.counts().forEach(counts::put);
    Files.createDirectories(file.getParent());
    Files.writeString(file, JSON.writeValueAsString(out));
  }

  /**
   * Makes the count {@link #save} wrote to {@code file} {@code project}'s last count, when it was
   * made with the same source roots and classpath; {@link #errors} still checks every file's hash.
   */
  static void load(SourceProject project, Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonNode in = JSON.readTree(Files.readString(file));
    if (!in.path("build").asString("").equals(build(project))) {
      return;
    }
    Map<Path, String> hashes = new HashMap<>();
    for (Map.Entry<String, JsonNode> f : in.path("files").properties()) {
      hashes.put(Path.of(f.getKey()), f.getValue().asString());
    }
    Map<String, Integer> counts = new TreeMap<>();
    for (Map.Entry<String, JsonNode> c : in.path("counts").properties()) {
      counts.put(c.getKey(), c.getValue().asInt());
    }
    project.session().lastCount = new Counted(project, hashes, counts);
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  /** What a count depends on besides the files: the source roots and the classpath. */
  private static String build(SourceProject project) {
    return project.sourceRoots() + " " + project.classpath();
  }
}
