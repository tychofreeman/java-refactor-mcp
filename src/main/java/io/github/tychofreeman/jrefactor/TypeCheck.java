package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /**
   * A count of errors, with the project and, for each file it was made from, a hash of its text and
   * its {@link Headers}.
   */
  record Counted(
      SourceProject project,
      Map<Path, String> hashes,
      Map<Path, Map<String, String>> headers,
      Map<String, Integer> counts) {}

  /**
   * Compile errors in the project as it is on disk, counted per file and message. Keys are {@code
   * "<file> | <message>"}; line numbers are left out on purpose, so an edit that only moves code
   * does not count as a new error. When every file's text is what the last count saw, that count is
   * returned without parsing: an edit's count after is the next edit's count before. When some
   * changed, only the files whose errors can have changed are parsed: the changed ones, and those
   * that mention a name whose {@link Headers header} changed.
   */
  static Map<String, Integer> errors(SourceProject project) throws IOException {
    List<Path> files = project.javaFiles();
    Map<Path, String> hashes = hashes(files);
    Session session = project.session();
    Counted last = session.lastCount;
    if (last != null && last.project().equals(project)) {
      if (last.hashes().equals(hashes)) {
        session.lastParsed = List.of();
        return new TreeMap<>(last.counts());
      }
      session.lastCount = narrowed(project, files, hashes, last);
      return new TreeMap<>(session.lastCount.counts());
    }
    Map<Path, Map<String, String>> headers = new HashMap<>();
    Map<String, Integer> counts = count(project, files, headers);
    session.lastParsed = files;
    session.lastCount = new Counted(project, hashes, headers, new TreeMap<>(counts));
    return counts;
  }

  /**
   * The count after the files whose hashes differ from {@code last}'s changed: those files and the
   * files that mention a name whose header changed are counted again, the rest kept.
   */
  private static Counted narrowed(
      SourceProject project, List<Path> files, Map<Path, String> hashes, Counted last)
      throws IOException {
    Map<Path, Map<String, String>> headers = new HashMap<>(last.headers());
    Set<String> names = new HashSet<>();
    Set<Path> recheck = new LinkedHashSet<>();
    for (Path f : files) {
      if (!hashes.get(f).equals(last.hashes().get(f))) {
        String text = Files.readString(f);
        Map<String, String> now = Headers.of(text, Jdt.parseSyntax(text));
        names.addAll(Headers.changed(headers.getOrDefault(f, Map.of()), now));
        headers.put(f, now);
        recheck.add(f);
      }
    }
    Set<String> gone = new HashSet<>();
    for (Path f : last.hashes().keySet()) {
      if (!hashes.containsKey(f)) {
        names.addAll(headers.getOrDefault(f, Map.of()).keySet());
        headers.remove(f);
        gone.add(project.relative(f));
      }
    }
    if (!names.isEmpty()) {
      for (Path f : files) {
        if (!recheck.contains(f) && Headers.mentions(Files.readString(f), names)) {
          recheck.add(f);
        }
      }
    }
    Set<String> rels = new HashSet<>(gone);
    for (Path f : recheck) {
      rels.add(project.relative(f));
    }
    Map<String, Integer> counts = new TreeMap<>(last.counts());
    counts.keySet().removeIf(k -> rels.contains(k.substring(0, k.indexOf(" | "))));
    List<Path> parsed = new ArrayList<>(recheck);
    counts.putAll(count(project, parsed, null));
    project.session().lastParsed = parsed;
    return new Counted(project, hashes, headers, counts);
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
    return count(project, project.javaFiles(), null);
  }

  /**
   * The errors of {@code files}, parsed together; each file's {@link Headers} put in {@code
   * headers}, unless it is null.
   */
  private static Map<String, Integer> count(
      SourceProject project, List<Path> files, Map<Path, Map<String, String>> headers) {
    Map<String, Integer> counts = new TreeMap<>();
    project.session().parses++;
    Jdt.parseAll(
        project,
        files,
        (file, cu) -> {
          if (headers != null) {
            try {
              headers.put(file, Headers.of(Files.readString(file), cu));
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          }
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
    out.put("version", FORMAT);
    out.put("build", build(project));
    ObjectNode files = out.putObject("files");
    last.hashes()
        .forEach((path, hash) -> {
          ObjectNode f = files.putObject(path.toString());
          f.put("hash", hash);
          ObjectNode names = f.putObject("headers");
          last.headers().getOrDefault(path, Map.of()).forEach(names::put);
        });
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
    if (in.path("version").asInt(0) != FORMAT
        || !in.path("build").asString("").equals(build(project))) {
      return;
    }
    Map<Path, String> hashes = new HashMap<>();
    Map<Path, Map<String, String>> headers = new HashMap<>();
    for (Map.Entry<String, JsonNode> f : in.path("files").properties()) {
      Path path = Path.of(f.getKey());
      hashes.put(path, f.getValue().path("hash").asString());
      Map<String, String> names = new TreeMap<>();
      for (Map.Entry<String, JsonNode> n : f.getValue().path("headers").properties()) {
        names.put(n.getKey(), n.getValue().asString());
      }
      headers.put(path, names);
    }
    Map<String, Integer> counts = new TreeMap<>();
    for (Map.Entry<String, JsonNode> c : in.path("counts").properties()) {
      counts.put(c.getKey(), c.getValue().asInt());
    }
    project.session().lastCount = new Counted(project, hashes, headers, counts);
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  /** The version of what {@link #save} writes; a file of another version is not loaded. */
  private static final int FORMAT = 2;

  /** What a count depends on besides the files: the source roots and the classpath. */
  private static String build(SourceProject project) {
    return project.sourceRoots() + " " + project.classpath();
  }

  /** The files the last {@link #errors} of {@code project} parsed; empty when it parsed none. */
  static List<Path> lastParsed(SourceProject project) {
    return project.session().lastParsed;
  }
}
