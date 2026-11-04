package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The text of every Java file a stress run starts from, kept in a file in the project root while it
 * runs, so that a run killed mid-edit is put back by the next one before it starts.
 */
final class StressSnapshot {

  private StressSnapshot() {}

  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path file(Path root) {
    return root.resolve(".jrefactor-stress-snapshot.json");
  }

  /** Keeps the text of {@code files} in {@code root}'s snapshot. */
  static void take(Path root, List<Path> files) throws IOException {
    Map<String, String> texts = new LinkedHashMap<>();
    for (Path f : files) {
      texts.put(f.toAbsolutePath().toString(), Files.readString(f));
    }
    Files.writeString(file(root), JSON.writeValueAsString(texts));
  }

  /** Deletes the snapshot: the run finished and put everything back itself. */
  static void done(Path root) throws IOException {
    Files.deleteIfExists(file(root));
  }

  /**
   * Puts back the files a snapshot left by a killed run holds, removes the Java files under {@code
   * root} it does not, and deletes it; returns how many files it changed (0 with no snapshot).
   */
  static int recover(Path root) throws IOException {
    Path snapshot = file(root);
    if (!Files.exists(snapshot)) {
      return 0;
    }
    Map<String, String> texts =
        JSON.readValue(Files.readString(snapshot), new TypeReference<Map<String, String>>() {});
    int changed = 0;
    for (Map.Entry<String, String> e : texts.entrySet()) {
      Path f = Path.of(e.getKey());
      if (!Files.exists(f) || !Files.readString(f).equals(e.getValue())) {
        Files.createDirectories(f.getParent());
        Files.writeString(f, e.getValue());
        changed++;
      }
    }
    List<Path> java;
    try (Stream<Path> all = Files.walk(root)) {
      java = all.filter(p -> p.toString().endsWith(".java")).toList();
    }
    for (Path f : java) {
      if (!texts.containsKey(f.toAbsolutePath().toString())) {
        Files.delete(f);
        changed++;
      }
    }
    Files.delete(snapshot);
    if (changed > 0) {
      System.out.println("stress: put back " + changed + " files a killed run left changed");
    }
    return changed;
  }
}
