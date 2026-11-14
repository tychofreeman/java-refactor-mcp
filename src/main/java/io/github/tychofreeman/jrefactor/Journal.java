package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What each top-level call wrote, per project: the text of each file before and after, so {@code
 * undo} can put the last change back. Kept in memory for the life of the server; the command line
 * ({@link Cli}) saves it between runs.
 */
final class Journal {

  private Journal() {}

  /** Undoes the last change in {@code project}; says what it undid and which files it restored. */
  static String undo(SourceProject project) throws Exception {
    Deque<Entry> done = project.session().done;
    if (done.isEmpty()) {
      throw new Refusal("Nothing to undo in this session.");
    }
    Entry last = done.peek();
    for (Map.Entry<Path, String> file : last.after().entrySet()) {
      if (!Objects.equals(read(file.getKey()), file.getValue())) {
        throw new Refusal(
            project.relative(file.getKey())
                + " changed after "
                + last.what()
                + ", and undoing would lose that change. Nothing was undone.");
      }
    }
    restore(project, last.before(), last.modes());
    done.pop();
    List<String> restored = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    for (Map.Entry<Path, String> file : last.before().entrySet()) {
      (file.getValue() == null ? removed : restored).add(project.relative(file.getKey()));
    }
    StringBuilder said = new StringBuilder("undid ").append(last.what()).append(":");
    if (!restored.isEmpty()) {
      said.append(" restored ").append(String.join(", ", restored));
    }
    if (!removed.isEmpty()) {
      said.append(restored.isEmpty() ? "" : ";")
          .append(" removed ")
          .append(String.join(", ", removed));
    }
    return said.toString();
  }

  /** How many changes {@link #save} keeps: the newest. */
  static final int KEPT = 20;

  private static final ObjectMapper JSON = Json.MAPPER;

  /**
   * Writes {@code project}'s undo history to {@code file}, the newest {@link #KEPT} changes, so a
   * later process can {@link #load} it: the command line runs one call per process.
   */
  static void save(SourceProject project, Path file) throws IOException {
    ArrayNode entries = JSON.createArrayNode();
    int n = 0;
    for (Entry e : project.session().done) {
      if (n++ == KEPT) {
        break;
      }
      ObjectNode entry = entries.addObject();
      entry.put("what", e.what());
      ArrayNode files = entry.putArray("files");
      for (Path p : e.after().keySet()) {
        ObjectNode f = files.addObject();
        f.put("path", p.toString());
        f.put("before", e.before().get(p));
        f.put("after", e.after().get(p));
        if (e.modes().containsKey(p)) {
          f.put("mode", e.modes().get(p));
        }
      }
    }
    Files.createDirectories(file.getParent());
    Files.writeString(file, JSON.writeValueAsString(entries));
  }

  /** Replaces {@code project}'s undo history with the one {@link #save} wrote to {@code file}. */
  static void load(SourceProject project, Path file) throws IOException {
    if (!Files.exists(file)) {
      return;
    }
    Deque<Entry> done = project.session().done;
    done.clear();
    for (JsonNode entry : JSON.readTree(Files.readString(file)).values()) {
      Map<Path, String> before = new LinkedHashMap<>();
      Map<Path, String> after = new LinkedHashMap<>();
      Map<Path, String> modes = new LinkedHashMap<>();
      for (JsonNode f : entry.path("files").values()) {
        Path p = Path.of(f.path("path").asString());
        before.put(p, text(f.get("before")));
        after.put(p, text(f.get("after")));
        if (text(f.get("mode")) != null) {
          modes.put(p, text(f.get("mode")));
        }
      }
      done.addLast(new Entry(entry.path("what").asString(), before, after, modes));
    }
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() ? null : node.asString();
  }

  /**
   * One change: what made it, each file's text before and after (null: no such file), and the mode
   * ("rwxr-xr-x") of each file that existed before it, where the file system has one.
   */
  record Entry(
      String what, Map<Path, String> before, Map<Path, String> after, Map<Path, String> modes) {}

  /** The change being made by the call that is running, and how deep calls are nested in it. */
  static final class Open {
    final String what;
    final Map<Path, String> before = new LinkedHashMap<>();
    final Map<Path, String> after = new LinkedHashMap<>();
    final Map<Path, String> modes = new LinkedHashMap<>();
    int depth;

    Open(String what) {
      this.what = what;
    }
  }

  /** Starts a call; a call inside another (a batch's step) joins the outer one's change. */
  static void begin(SourceProject project, String what) {
    Session session = project.session();
    if (session.open == null) {
      session.open = new Open(what);
    }
    session.open.depth++;
  }

  /** Records that the running call wrote {@code file}; nothing is recorded outside a call. */
  static void wrote(SourceProject project, Path file, String before, String after) {
    Open open = project.session().open;
    if (open != null) {
      if (!open.before.containsKey(file)) {
        open.before.put(file, before); // putIfAbsent would replace a null: a new file
      }
      open.after.put(file, after);
    }
  }

  /** The files the running call has written, with their text before it (null for a new file). */
  static Map<Path, String> written(SourceProject project) {
    Open open = project.session().open;
    return open == null ? new LinkedHashMap<>() : new LinkedHashMap<>(open.before);
  }

  /** Ends a call that succeeded; the outermost one becomes the change {@code undo} reverts. */
  static void commit(SourceProject project) {
    Session session = project.session();
    Open open = session.open;
    if (--open.depth > 0) {
      return;
    }
    session.open = null;
    if (!open.after.isEmpty()) {
      session.done.push(new Entry(open.what, open.before, open.after, open.modes));
    }
  }

  /** Ends a call that failed; the outermost one puts back every file the change wrote. */
  static void abort(SourceProject project) throws IOException {
    Session session = project.session();
    Open open = session.open;
    if (open == null || --open.depth > 0) {
      return;
    }
    session.open = null;
    restore(project, open.before, open.modes);
  }

  /** The only writer of project files: each path gets its text, or is deleted for null. */
  static void restore(SourceProject project, Map<Path, String> texts) throws IOException {
    restore(project, texts, Map.of());
  }

  /** {@link #restore(Map)}, and each file written gets back its mode in {@code modes}, if any. */
  static void restore(SourceProject project, Map<Path, String> texts, Map<Path, String> modes)
      throws IOException {
    for (Map.Entry<Path, String> file : texts.entrySet()) {
      put(project, file.getKey(), file.getValue());
      if (file.getValue() != null && modes.containsKey(file.getKey())) {
        setMode(file.getKey(), modes.get(file.getKey()));
      }
    }
  }

  /**
   * Writes {@code text} to {@code file}, creating its directories; null deletes the file, and the
   * directories its deletion leaves empty, up to the first that is not, a source root or the
   * project's root: a refused or undone call leaves no empty directories behind.
   */
  static void put(SourceProject project, Path file, String text) throws IOException {
    if (text == null) {
      Files.deleteIfExists(file);
      for (Path d = file.getParent(); d != null && removable(project, d); d = d.getParent()) {
        Files.delete(d);
      }
    } else {
      Files.createDirectories(file.getParent());
      Files.writeString(file, text);
    }
  }

  /** {@code file}'s mode, "rwxr-xr-x", or null when it does not exist or the system has none. */
  static String modeOf(Path file) throws IOException {
    try {
      return Files.exists(file)
          ? PosixFilePermissions.toString(Files.getPosixFilePermissions(file))
          : null;
    } catch (UnsupportedOperationException e) {
      return null;
    }
  }

  /** Records the mode {@code file} had before the running call first wrote it. */
  static void mode(SourceProject project, Path file, String mode) {
    Open open = project.session().open;
    if (open != null && mode != null) {
      open.modes.putIfAbsent(file, mode);
    }
  }

  /** Gives {@code to} the mode of {@code from}: a moved or copied file keeps it. */
  static void copyMode(Path from, Path to) throws IOException {
    String mode = modeOf(from);
    if (mode != null) {
      setMode(to, mode);
    }
  }

  private static void setMode(Path file, String mode) throws IOException {
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(mode));
    } catch (UnsupportedOperationException e) {
      // a file system without modes: nothing to keep
    }
  }

  private static boolean empty(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return false;
    }
    try (Stream<Path> inside = Files.list(dir)) {
      return inside.findAny().isEmpty();
    }
  }

  /** The text of {@code file}, or null when there is none. */
  static String read(Path file) throws IOException {
    return Files.exists(file) ? Files.readString(file) : null;
  }

  /** Whether {@code dir} is empty and inside the project, neither its root nor a source root. */
  private static boolean removable(SourceProject project, Path dir) throws IOException {
    Path d = dir.toAbsolutePath().normalize();
    return d.startsWith(project.root())
        && !d.equals(project.root())
        && !project.sourceRoots().contains(d)
        && empty(d);
  }
}
