package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jdt.core.compiler.IProblem;

/**
 * The gate every refactoring passes: count the compile errors in the whole project before and after
 * an edit, and refuse when the edit adds any.
 */
final class TypeCheck {

  private TypeCheck() {}

  /** A count of errors, with the project and the text of every file it was made from. */
  record Counted(SourceProject project, Map<Path, String> texts, Map<String, Integer> counts) {}

  /**
   * Compile errors in the project as it is on disk, counted per file and message. Keys are {@code
   * "<file> | <message>"}; line numbers are left out on purpose, so an edit that only moves code
   * does not count as a new error. When every file's text is what the last count saw, that count is
   * returned without parsing: an edit's count after is the next edit's count before.
   */
  static Map<String, Integer> errors(SourceProject project) throws IOException {
    List<Path> files = project.javaFiles();
    Map<Path, String> texts = new HashMap<>();
    for (Path file : files) {
      texts.put(file, Files.readString(file));
    }
    Counted last = project.session().lastCount;
    if (last != null && last.project().equals(project) && last.texts().equals(texts)) {
      return new TreeMap<>(last.counts());
    }
    Map<String, Integer> counts = count(project, files);
    project.session().lastCount = new Counted(project, texts, new TreeMap<>(counts));
    return counts;
  }

  /** {@link #errors} counted from scratch, never from the last count: what a fresh load gives. */
  static Map<String, Integer> uncached(SourceProject project) throws IOException {
    return count(project, project.javaFiles());
  }

  private static Map<String, Integer> count(SourceProject project, List<Path> files) {
    Map<String, Integer> counts = new TreeMap<>();
    project.session().parses++;
    Jdt.parseAll(
        project,
        files,
        (file, cu) -> {
          String rel = project.relative(file);
          for (IProblem problem : cu.getProblems()) {
            if (problem.isError()) {
              counts.merge(rel + " | " + problem.getMessage(), 1, Integer::sum);
            }
          }
        });
    return counts;
  }

  /** Entries of {@code after} that have more occurrences than in {@code before}. */
  static List<String> introduced(Map<String, Integer> before, Map<String, Integer> after) {
    List<String> out = new ArrayList<>();
    for (Map.Entry<String, Integer> e : after.entrySet()) {
      int extra = e.getValue() - before.getOrDefault(e.getKey(), 0);
      if (extra > 0) {
        out.add(e.getKey() + (extra > 1 ? " (x" + extra + ")" : ""));
      }
    }
    return out;
  }

  /** How many times {@link #errors} has parsed {@code project}, for tests of the reuse. */
  static int parses(SourceProject project) {
    return project.session().parses;
  }
}
