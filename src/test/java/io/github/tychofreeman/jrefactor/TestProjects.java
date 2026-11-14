package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Projects on disk for tests: files written, a Maven layout, a tree copied. */
final class TestProjects {

  private TestProjects() {}

  /** Writes {@code text} to {@code rel} under {@code root}, with its directories; the file. */
  static Path write(Path root, String rel, String text) throws IOException {
    Path file = root.resolve(rel);
    Files.createDirectories(file.getParent());
    return Files.writeString(file, text);
  }

  /**
   * A project of the Maven layout under {@code root}, both source roots made, whose session is
   * editing when {@code editing} is set (as an edit tool's call is).
   */
  static SourceProject maven(Path root, boolean editing) throws IOException {
    Path main = Files.createDirectories(root.resolve("src/main/java"));
    Path test = Files.createDirectories(root.resolve("src/test/java"));
    Session session = new Session();
    session.editing = editing;
    return new SourceProject(root, List.of(main, test), List.of(), null, session);
  }

  /** Copies every file under {@code from} to the same path under {@code to}. */
  static void copyTree(Path from, Path to) throws IOException {
    try (Stream<Path> files = Files.walk(from)) {
      for (Path src : files.filter(Files::isRegularFile).toList()) {
        Path dst = to.resolve(from.relativize(src).toString());
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst);
      }
    }
  }
}
