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
 * and {@code classpath} holds the library jars and class directories. {@code classpathProblem} is
 * why the classpath is empty when Maven could not give one, or null.
 */
public record SourceProject(
    Path root,
    List<Path> sourceRoots,
    List<String> classpath,
    String classpathProblem,
    Session session) {

  public SourceProject {
    root = root.toAbsolutePath().normalize();
    sourceRoots = sourceRoots.stream().map(p -> p.toAbsolutePath().normalize()).toList();
    classpath = List.copyOf(classpath);
  }

  /** A project whose root is its one source root, with no classpath. */
  public static SourceProject of(Path root) {
    return new SourceProject(root, List.of(root), List.of(), null, new Session());
  }

  /**
   * The project at {@code root} as its build describes it: for Maven ({@code pom.xml}), {@code
   * src/main/java} and {@code src/test/java} and the test classpath. Anything else is {@link #of}.
   */
  public static SourceProject load(Path root) {
    return load(root, null);
  }

  /**
   * {@link #load(Path)}, with the classpath kept in {@code classpathCache} (none when null) while
   * {@code pom.xml} is unchanged, so a command-line run need not start Maven each time.
   */
  public static SourceProject load(Path root, Path classpathCache) {
    Path dir = root.toAbsolutePath().normalize();
    if (!MavenBuild.isMaven(dir)) {
      return of(dir);
    }
    List<Path> roots = MavenBuild.sourceRoots(dir);
    if (roots.isEmpty()) {
      return of(dir);
    }
    List<String> classpath;
    String problem = null;
    try {
      classpath =
          classpathCache == null
              ? MavenBuild.classpath(dir)
              : MavenBuild.classpath(dir, classpathCache);
    } catch (IOException e) {
      problem = e.getMessage();
      System.err.println(
          "jrefactor: no classpath from Maven, so library types will not resolve: " + problem);
      classpath = List.of();
    }
    return new SourceProject(dir, roots, classpath, problem, new Session());
  }

  /**
   * The path of {@code rel}, a {@code .java} file that exists; otherwise a refusal says how to fix
   * it.
   */
  public Path javaFile(String rel) throws IOException {
    if (!rel.endsWith(".java")) {
      throw new Refusal(rel + " is not a .java file.");
    }
    Path path = file(rel);
    if (!Files.isRegularFile(path)) {
      throw new Refusal(
          "No file " + rel + " in the project. Create it with create_file." + javaFilesHint(rel));
    }
    return path;
  }

  /** The path of {@code rel}, which must exist, of any kind; otherwise a refusal says so. */
  public Path existingFile(String rel) {
    Path path = file(rel);
    if (!Files.isRegularFile(path)) {
      throw new Refusal("No file " + rel + " in the project. Create it with create_file.");
    }
    return path;
  }

  /** The Java files in the folder of {@code rel}, for a refusal that names the missing file. */
  public String javaFilesHint(String rel) throws IOException {
    String dir = parent(rel);
    List<String> siblings = new ArrayList<>();
    for (Path p : javaFiles()) {
      String other = relative(p);
      if (parent(other).equals(dir)) {
        siblings.add(other.substring(dir.isEmpty() ? 0 : dir.length() + 1));
      }
    }
    String where = dir.isEmpty() ? "the root" : dir;
    return siblings.isEmpty()
        ? " No Java files are in " + where + "."
        : " Java files in "
            + where
            + ": "
            + String.join(", ", siblings.stream().sorted().toList())
            + ".";
  }

  private static String parent(String rel) {
    int slash = rel.lastIndexOf('/');
    return slash < 0 ? "" : rel.substring(0, slash);
  }

  /** All {@code .java} files under the source roots, sorted. */
  public List<Path> javaFiles() throws IOException {
    List<Path> all = new ArrayList<>();
    for (Path sourceRoot : sourceRoots) {
      if (!Files.isDirectory(sourceRoot)) {
        continue;
      }
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
