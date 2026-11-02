package io.github.tychofreeman.jrefactor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A Java project the server works on. Tool arguments name files relative to {@code root}; the
 * {@code .java} files under {@code sourceRoots} are what the gate checks and where types are found,
 * and {@code classpath} holds the library jars and class directories.
 */
public record SourceProject(Path root, List<Path> sourceRoots, List<String> classpath) {

  public SourceProject {
    root = root.toAbsolutePath().normalize();
    sourceRoots = sourceRoots.stream().map(p -> p.toAbsolutePath().normalize()).toList();
    classpath = List.copyOf(classpath);
  }

  /** A project whose root is its one source root, with no classpath. */
  public static SourceProject of(Path root) {
    return new SourceProject(root, List.of(root), List.of());
  }

  /**
   * The project at {@code root} as its build describes it: for Maven ({@code pom.xml}), {@code
   * src/main/java} and {@code src/test/java} and the test classpath. Anything else is {@link #of}.
   */
  public static SourceProject load(Path root) {
    Path dir = root.toAbsolutePath().normalize();
    if (!MavenBuild.isMaven(dir)) {
      return of(dir);
    }
    List<Path> roots = MavenBuild.sourceRoots(dir);
    if (roots.isEmpty()) {
      return of(dir);
    }
    List<String> classpath;
    try {
      classpath = MavenBuild.classpath(dir);
    } catch (IOException e) {
      System.err.println(
          "jrefactor: no classpath from Maven, so library types will not resolve: "
              + e.getMessage());
      classpath = List.of();
    }
    return new SourceProject(dir, roots, classpath);
  }

  /** All {@code .java} files under the source roots, sorted. */
  public List<Path> javaFiles() throws IOException {
    List<Path> all = new ArrayList<>();
    for (Path sourceRoot : sourceRoots) {
      try (Stream<Path> files = Files.walk(sourceRoot)) {
        files
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().endsWith(".java"))
            .forEach(all::add);
      }
    }
    return all.stream().sorted().toList();
  }

  /** The path of {@code rel} under the root; refused if it leaves the root. */
  public Path file(String rel) {
    Path path = root.resolve(rel).normalize();
    if (!path.startsWith(root) || path.equals(root)) {
      throw new Refusal(
          "file must be a path relative to the project root " + root + "; got " + rel + ".");
    }
    return path;
  }

  /** The package a {@code .java} file at {@code path} must declare, or null if no root holds it. */
  public String packageOf(Path path) {
    for (Path sourceRoot : sourceRoots) {
      if (path.startsWith(sourceRoot)) {
        Path dir = sourceRoot.relativize(path).getParent();
        return dir == null ? "" : dir.toString().replace(File.separatorChar, '.');
      }
    }
    return null;
  }

  /** Path relative to the root, with forward slashes. */
  public String relative(Path file) {
    return root.relativize(file.toAbsolutePath().normalize())
        .toString()
        .replace(File.separatorChar, '/');
  }
}
