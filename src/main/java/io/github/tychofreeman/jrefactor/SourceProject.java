package io.github.tychofreeman.jrefactor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * A Java source tree the server works on: every {@code .java} file under {@code root} is a source
 * file, and {@code root} is also the source path for resolving types.
 *
 * <p>{@code classpath} holds library jars given by the caller. Maven and Gradle loading is not
 * built yet (see docs/design.md).
 */
public record SourceProject(Path root, List<String> classpath) {

  public SourceProject {
    root = root.toAbsolutePath().normalize();
    classpath = List.copyOf(classpath);
  }

  public static SourceProject of(Path root) {
    return new SourceProject(root, List.of());
  }

  /** All {@code .java} files under the root, sorted. */
  public List<Path> javaFiles() throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".java"))
          .sorted()
          .toList();
    }
  }

  /** Path relative to the root, with forward slashes. */
  public String relative(Path file) {
    return root.relativize(file.toAbsolutePath().normalize())
        .toString()
        .replace(File.separatorChar, '/');
  }
}
