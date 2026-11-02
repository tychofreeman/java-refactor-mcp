package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each top-level call wrote, per project: the text of each file before and after, so {@code
 * undo} can put the last change back. Kept in memory for the life of the server.
 */
final class Journal {

  private Journal() {}

  /** Undoes the last change in {@code project}; says what it undid and which files it restored. */
  static String undo(SourceProject project) throws Exception {
    Deque<Entry> done = DONE.get(project.root());
    if (done == null || done.isEmpty()) {
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

  /** One change: what made it, and each file's text before and after (null: no such file). */
  record Entry(String what, Map<Path, String> before, Map<Path, String> after) {}

  /** The change being made by the call that is running, and how deep calls are nested in it. */
  private static final class Open {
    final String what;
    final Map<Path, String> before = new LinkedHashMap<>();
    final Map<Path, String> after = new LinkedHashMap<>();
    int depth;

    Open(String what) {
      this.what = what;
    }
  }

  private static final Map<Path, Deque<Entry>> DONE = new ConcurrentHashMap<>();

  private static final ThreadLocal<Open> OPEN = new ThreadLocal<>();

  /** Starts a call; a call inside another (a batch's step) joins the outer one's change. */
  static void begin(String what) {
    Open open = OPEN.get();
    if (open == null) {
      open = new Open(what);
      OPEN.set(open);
    }
    open.depth++;
  }

  /** Records that the running call wrote {@code file}; nothing is recorded outside a call. */
  static void wrote(Path file, String before, String after) {
    Open open = OPEN.get();
    if (open != null) {
      open.before.putIfAbsent(file, before);
      open.after.put(file, after);
    }
  }

  /** Ends a call that succeeded; the outermost one becomes the change {@code undo} reverts. */
  static void commit(SourceProject project) {
    Open open = OPEN.get();
    if (--open.depth > 0) {
      return;
    }
    OPEN.remove();
    if (!open.after.isEmpty()) {
      DONE.computeIfAbsent(project.root(), k -> new ArrayDeque<>())
          .push(new Entry(open.what, open.before, open.after));
    }
  }

  /** Ends a call that failed; the outermost one puts back every file the change wrote. */
  static void abort() throws IOException {
    Open open = OPEN.get();
    if (open == null || --open.depth > 0) {
      return;
    }
    OPEN.remove();
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
