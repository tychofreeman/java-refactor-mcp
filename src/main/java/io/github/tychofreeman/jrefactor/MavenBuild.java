package io.github.tychofreeman.jrefactor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What a Maven project's build says about its sources: its roots and its test classpath. */
final class MavenBuild {

  /**
   * The plugin that prints the classpath, pinned so a project's own plugin setup cannot vary it.
   */
  private static final String BUILD_CLASSPATH =
      "org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath";

  private static final long TIMEOUT_SECONDS = 300;

  private MavenBuild() {}

  static boolean isMaven(Path root) {
    return Files.isRegularFile(root.resolve("pom.xml"));
  }

  /** {@code src/main/java} and {@code src/test/java}, those that exist. */
  static List<Path> sourceRoots(Path root) {
    List<Path> roots = new ArrayList<>();
    for (String dir : List.of("src/main/java", "src/test/java")) {
      if (Files.isDirectory(root.resolve(dir))) {
        roots.add(root.resolve(dir));
      }
    }
    return roots;
  }

  /**
   * The test-scope classpath, from Maven run in {@code root} (its {@code ./mvnw}, else {@code mvn}
   * on the PATH). Maven's output never reaches this process's stdout, which carries the protocol.
   *
   * @throws IOException with Maven's last lines of output when it cannot be run or fails
   */
  static List<String> classpath(Path root) throws IOException {
    Path out = Files.createTempFile("jrefactor-classpath", ".txt");
    Path log = Files.createTempFile("jrefactor-maven", ".log");
    try {
      List<String> command = new ArrayList<>(maven(root));
      command.addAll(
          List.of(
              "-q", "-B", BUILD_CLASSPATH, "-Dmdep.includeScope=test", "-Dmdep.outputFile=" + out));
      Process process =
          new ProcessBuilder(command)
              .directory(root.toFile())
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
              .start();
      boolean finished;
      try {
        finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
        throw new IOException("interrupted while Maven printed the classpath", e);
      }
      if (!finished) {
        process.destroyForcibly();
        throw new IOException("Maven did not print the classpath in " + TIMEOUT_SECONDS + " s");
      }
      if (process.exitValue() != 0) {
        throw new IOException(
            String.join(" ", command)
                + " failed (exit "
                + process.exitValue()
                + "):\n"
                + tail(log));
      }
      String text = Files.readString(out).strip();
      return text.isEmpty() ? List.of() : Arrays.asList(text.split(File.pathSeparator));
    } finally {
      Files.deleteIfExists(out);
      Files.deleteIfExists(log);
    }
  }

  /**
   * {@link #classpath(Path)}, kept in {@code cache} with a hash of the {@code pom.xml} it came
   * from: used again while the pom is the same and every entry exists, so Maven runs only when the
   * build may have changed.
   */
  static List<String> classpath(Path root, Path cache) throws IOException {
    String pom = pomHash(root);
    if (Files.isRegularFile(cache)) {
      List<String> lines = Files.readAllLines(cache);
      if (!lines.isEmpty() && lines.get(0).equals(pom)) {
        List<String> entries = lines.subList(1, lines.size());
        if (entries.stream().allMatch(e -> Files.exists(Path.of(e)))) {
          return List.copyOf(entries);
        }
      }
    }
    List<String> entries = classpath(root);
    remember(root, cache, entries);
    return entries;
  }

  /**
   * A hash of the {@code pom.xml} of {@code root} and of each parent pom it names, since a parent's
   * dependency versions are part of the build; a parent outside the tree is covered by its version.
   */
  private static String pomHash(Path root) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      Path pom = root.resolve("pom.xml");
      for (int depth = 0; pom != null && Files.isRegularFile(pom) && depth < PARENTS; depth++) {
        digest.update(Files.readAllBytes(pom));
        pom = parentPom(pom);
      }
      return "pom " + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** How many parent poms a build may name before the hash stops following them. */
  private static final int PARENTS = 20;

  /**
   * The parent pom that {@code pom} names with its {@code relativePath} (default {@code
   * ../pom.xml}), or null when it names no parent. A regular expression reads the pom, not a full
   * XML parse.
   */
  private static Path parentPom(Path pom) throws IOException {
    Matcher parent =
        Pattern.compile("<parent>(.*?)</parent>", Pattern.DOTALL).matcher(Files.readString(pom));
    if (!parent.find()) {
      return null;
    }
    Matcher relative =
        Pattern.compile("<relativePath>\\s*(.*?)\\s*</relativePath>", Pattern.DOTALL)
            .matcher(parent.group(1));
    Path path =
        pom.getParent().resolve(relative.find() ? relative.group(1) : "../pom.xml").normalize();
    return Files.isDirectory(path) ? path.resolve("pom.xml") : path;
  }

  private static List<String> maven(Path root) throws IOException {
    Path wrapper = root.resolve("mvnw");
    if (Files.isExecutable(wrapper)) {
      return List.of(wrapper.toString());
    }
    for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
      if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, "mvn"))) {
        return List.of(Path.of(dir, "mvn").toString());
      }
    }
    throw new IOException("no ./mvnw in the project and no mvn on the PATH");
  }

  private static String tail(Path log) throws IOException {
    List<String> lines = Files.readAllLines(log);
    return String.join("\n", lines.subList(Math.max(0, lines.size() - 20), lines.size()));
  }

  /**
   * Writes {@code entries} to {@code cache} as the classpath of {@code root}'s pom as it is now.
   */
  static void remember(Path root, Path cache, List<String> entries) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add(pomHash(root));
    lines.addAll(entries);
    Files.createDirectories(cache.getParent());
    Files.write(cache, lines);
  }
}
