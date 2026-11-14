package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The case directories of a test that runs one dynamic test per directory ({@code GoldenTest},
 * {@code ProbeTest}), all of them or those a system property names.
 */
final class Cases {

  private Cases() {}

  /**
   * The directories under {@code root} whose names match {@code patterns}, sorted: comma-separated
   * names or globs ({@code *}, {@code ?}). Null or blank selects every directory. Fails when a
   * pattern matches none, naming {@code property}, the system property the patterns came from.
   */
  static List<Path> select(Path root, String property, String patterns) throws IOException {
    List<Path> dirs;
    try (Stream<Path> listed = Files.list(root)) {
      dirs = listed.filter(Files::isDirectory).sorted().toList();
    }
    if (patterns == null || patterns.isBlank()) {
      return dirs;
    }
    List<String> unmatched = new ArrayList<>();
    Set<Path> selected = new TreeSet<>();
    for (String pattern : patterns.split(",")) {
      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern.strip());
      List<Path> matched = dirs.stream().filter(d -> matcher.matches(d.getFileName())).toList();
      if (matched.isEmpty()) {
        unmatched.add(pattern.strip());
      }
      selected.addAll(matched);
    }
    if (!unmatched.isEmpty()) {
      throw new IllegalArgumentException(
          "no case under "
              + root
              + " matches "
              + String.join(", ", unmatched)
              + " (-D"
              + property
              + "="
              + patterns
              + "); a pattern is a directory name or a glob with * and ?");
    }
    return List.copyOf(selected);
  }
}
