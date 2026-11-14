package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.eclipse.jdt.core.compiler.IProblem;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
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
      Map<Path, Map<String, Headers.Decl>> headers,
      Map<Problem, Integer> counts) {}

  /**
   * Compile errors in the project as it is on disk, counted per file and message ({@link Problem});
   * line numbers are left out on purpose, so an edit that only moves code does not count as a new
   * error. When every file's text is what the last count saw, that count is returned without
   * parsing: an edit's count after is the next edit's count before. When some changed, only the
   * files whose errors can have changed are parsed: the changed ones, and those that mention a name
   * whose {@link Headers header} changed.
   */
  static Map<Problem, Integer> errors(SourceProject project) throws IOException {
    List<Path> files = project.javaFiles();
    Map<Path, String> hashes = hashes(files);
    Session session = project.session();
    Counted last = session.lastCount;
    if (last != null
        && last.project().equals(project)
        && namedModule(project, last.hashes().keySet()) == namedModule(project, files)) {
      if (last.hashes().equals(hashes)) {
        session.lastParsed = List.of();
        return new TreeMap<>(last.counts());
      }
      session.lastCount = narrowed(project, files, hashes, last);
      return new TreeMap<>(session.lastCount.counts());
    }
    Map<Path, Map<String, Headers.Decl>> headers = new HashMap<>();
    Map<Problem, Integer> counts = count(project, files, headers);
    session.lastParsed = files;
    session.lastCount = new Counted(project, hashes, headers, new TreeMap<>(counts));
    return counts;
  }

  /**
   * The count after the files whose hashes differ from {@code last}'s changed. Counted again: those
   * files; the files that declare a subtype of a type whose header or member changed, or a type
   * whose header names one, and so on; and the files that mention a name any of those changes
   * touched, or create one of those types. The rest are kept.
   */
  private static Counted narrowed(
      SourceProject project, List<Path> files, Map<Path, String> hashes, Counted last)
      throws IOException {
    Map<Path, Map<String, Headers.Decl>> headers = new HashMap<>(last.headers());
    Set<String> changed = new HashSet<>();
    Set<Path> recheck = new LinkedHashSet<>();
    for (Path f : files) {
      if (!hashes.get(f).equals(last.hashes().get(f))) {
        String text = Files.readString(f);
        Map<String, Headers.Decl> now = Headers.of(text, Jdt.parseSyntax(text));
        changed.addAll(Headers.changed(headers.getOrDefault(f, Map.of()), now));
        headers.put(f, now);
        recheck.add(f);
      }
    }
    Set<String> gone = new HashSet<>();
    for (Path f : last.hashes().keySet()) {
      if (!hashes.containsKey(f)) {
        changed.addAll(headers.getOrDefault(f, Map.of()).keySet());
        headers.remove(f);
        gone.add(project.relative(f));
      }
    }
    // The names to look for, and the types whose subtypes and dependents change with them.
    Set<String> names = new HashSet<>();
    Set<String> types = new HashSet<>();
    for (String key : changed) {
      int member = key.indexOf('#');
      names.add(member < 0 ? key : key.substring(member + 1));
      types.add(member < 0 ? key : key.substring(0, member));
    }
    for (boolean grew = !types.isEmpty(); grew; ) {
      grew = false;
      for (Map.Entry<Path, Map<String, Headers.Decl>> file : headers.entrySet()) {
        for (Map.Entry<String, Headers.Decl> d : file.getValue().entrySet()) {
          if (Collections.disjoint(d.getValue().types(), types)) {
            continue;
          }
          String key = d.getKey();
          int member = key.indexOf('#');
          if (member >= 0) {
            // A member whose header names a changed type changed too: its type's subtypes follow.
            names.add(key.substring(member + 1));
            grew |= types.add(key.substring(0, member));
          } else {
            grew |= types.add(key);
            names.add(key);
            recheck.add(file.getKey());
          }
        }
      }
    }
    if (!names.isEmpty()) {
      for (Path f : files) {
        if (!recheck.contains(f)) {
          String text = Files.readString(f);
          if (Headers.mentions(text, names) || Headers.creates(text, types)) {
            recheck.add(f);
          }
        }
      }
    }
    Set<String> rels = new HashSet<>(gone);
    for (Path f : recheck) {
      rels.add(project.relative(f));
    }
    Map<Problem, Integer> counts = new TreeMap<>(last.counts());
    counts.keySet().removeIf(k -> rels.contains(k.rel()));
    List<Path> parsed = new ArrayList<>(recheck);
    counts.putAll(count(project, parsed, null));
    project.session().lastParsed = parsed;
    return new Counted(project, hashes, headers, counts);
  }

  /** A SHA-256 of each file's bytes, to tell whether any changed since a count. */
  private static Map<Path, String> hashes(List<Path> files) throws IOException {
    Map<Path, String> out = new HashMap<>();
    for (Path file : files) {
      out.put(file, Hashes.sha256Hex(Files.readAllBytes(file)));
    }
    return out;
  }

  /** {@link #errors} counted from scratch, never from the last count: what a fresh load gives. */
  static Map<Problem, Integer> uncached(SourceProject project) throws IOException {
    return count(project, project.javaFiles(), null);
  }

  /**
   * The errors of {@code files}, parsed together; each file's {@link Headers} put in {@code
   * headers}, unless it is null.
   */
  private static Map<Problem, Integer> count(
      SourceProject project, List<Path> files, Map<Path, Map<String, Headers.Decl>> headers) {
    Map<Problem, Integer> counts = new TreeMap<>();
    boolean namedModule = project.namedModule();
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
            if (counts(problem, namedModule)) {
              counts.merge(new Problem(rel, problem.getMessage()), 1, Integer::sum);
            }
          }
        });
    return counts;
  }

  /**
   * The problems of {@code after} that occur more often than in {@code before}, and how many more.
   */
  static List<Added> introduced(Map<Problem, Integer> before, Map<Problem, Integer> after) {
    List<Added> out = new ArrayList<>();
    for (Map.Entry<Problem, Integer> e : after.entrySet()) {
      int extra = e.getValue() - before.getOrDefault(e.getKey(), 0);
      if (extra > 0) {
        out.add(new Added(e.getKey(), extra));
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
          last.headers()
              .getOrDefault(path, Map.of())
              .forEach((key, d) -> {
                ObjectNode decl = names.putObject(key);
                decl.put("hash", d.hash());
                d.types().forEach(decl.putArray("types")::add);
              });
        });
    ArrayNode counts = out.putArray("counts");
    last.counts()
        .forEach((p, n) ->
            counts.addObject().put("file", p.rel()).put("message", p.message()).put("count", n));
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
    Map<Path, Map<String, Headers.Decl>> headers = new HashMap<>();
    for (Map.Entry<String, JsonNode> f : in.path("files").properties()) {
      Path path = Path.of(f.getKey());
      hashes.put(path, f.getValue().path("hash").asString());
      Map<String, Headers.Decl> names = new TreeMap<>();
      for (Map.Entry<String, JsonNode> n : f.getValue().path("headers").properties()) {
        Set<String> types = new TreeSet<>();
        for (JsonNode t : n.getValue().path("types").values()) {
          types.add(t.asString());
        }
        names.put(n.getKey(), new Headers.Decl(n.getValue().path("hash").asString(), types));
      }
      headers.put(path, names);
    }
    Map<Problem, Integer> counts = new TreeMap<>();
    for (JsonNode c : in.path("counts").values()) {
      counts.put(
          new Problem(c.path("file").asString(), c.path("message").asString()),
          c.path("count").asInt());
    }
    project.session().lastCount = new Counted(project, hashes, headers, counts);
  }

  private static final ObjectMapper JSON = Json.MAPPER;

  /** The version of what {@link #save} writes; a file of another version is not loaded. */
  private static final int FORMAT = 5;

  /** What a count depends on besides the files: the source roots and the classpath. */
  private static String build(SourceProject project) {
    return project.sourceRoots() + " " + project.classpath();
  }

  /** The files the last {@link #errors} of {@code project} parsed; empty when it parsed none. */
  static List<Path> lastParsed(SourceProject project) {
    return project.session().lastParsed;
  }

  /**
   * Whether {@code problem} is an error the gate counts. JDT's headless parser puts every unit in
   * the unnamed module, so in a {@link SourceProject#namedModule named module} a sealed type that
   * permits a class of another package of the module reads as an error javac does not report
   * (#438); that one is not counted.
   */
  private static boolean counts(IProblem problem, boolean namedModule) {
    return problem.isError()
        && !(namedModule && problem.getID() == IProblem.SealedPermittedTypeOutsideOfPackage);
  }

  /**
   * Whether {@code files} hold a {@code module-info.java} of one of {@code project}'s source roots:
   * a count made with one and without one differ in files that did not change ({@link #counts}).
   */
  private static boolean namedModule(SourceProject project, Collection<Path> files) {
    return files.stream()
        .anyMatch(f ->
            f.getFileName().toString().equals("module-info.java")
                && project.sourceRoots().contains(f.getParent()));
  }

  /** A compile error as the gate counts it: the file, relative to the project, and the message. */
  record Problem(String rel, String message) implements Comparable<Problem> {
    @Override
    public int compareTo(Problem other) {
      int byFile = rel.compareTo(other.rel);
      return byFile != 0 ? byFile : message.compareTo(other.message);
    }
  }

  /** A problem an edit added, {@code extra} more times than it was there before. */
  record Added(Problem problem, int extra) {}
}
