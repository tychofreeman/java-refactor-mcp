package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    restore(last.before());
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

  private static final ObjectMapper JSON = new ObjectMapper();

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
      for (JsonNode f : entry.path("files").values()) {
        Path p = Path.of(f.path("path").asString());
        before.put(p, text(f.get("before")));
        after.put(p, text(f.get("after")));
      }
      done.addLast(new Entry(entry.path("what").asString(), before, after));
    }
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() ? null : node.asString();
  }

  /** One change: what made it, and each file's text before and after (null: no such file). */
  record Entry(String what, Map<Path, String> before, Map<Path, String> after) {}

  /** The change being made by the call that is running, and how deep calls are nested in it. */
  static final class Open {
    final String what;
    final Map<Path, String> before = new LinkedHashMap<>();
    final Map<Path, String> after = new LinkedHashMap<>();
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
      session.done.push(new Entry(open.what, open.before, open.after));
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
    restore(open.before);
  }

  private static void restore(Map<Path, String> texts) throws IOException {
    for (Map.Entry<Path, String> file : texts.entrySet()) {
      if (file.getValue() == null) {
        Files.deleteIfExists(file.getKey());
      } else {
        Files.createDirectories(file.getKey().getParent());
        Files.writeString(file.getKey(), file.getValue());
      }
    }
  }

  private static String read(Path file) throws IOException {
    return Files.exists(file) ? Files.readString(file) : null;
  }
}
