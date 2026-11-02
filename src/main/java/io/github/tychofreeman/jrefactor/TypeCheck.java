package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * The gate every refactoring passes: count the compile errors in the whole project before and after
 * an edit, and refuse when the edit adds any.
 */
final class TypeCheck {

  private TypeCheck() {}

  /**
   * Compile errors in the project as it is on disk, counted per file and message. Keys are {@code
   * "<file> | <message>"}; line numbers are left out on purpose, so an edit that only moves code
   * does not count as a new error.
   */
  static Map<String, Integer> errors(SourceProject project) throws IOException {
    Map<String, Integer> counts = new TreeMap<>();
    for (Path file : project.javaFiles()) {
      CompilationUnit cu = Jdt.parse(project, file, Files.readString(file));
      String rel = project.relative(file);
      for (IProblem problem : cu.getProblems()) {
        if (problem.isError()) {
          counts.merge(rel + " | " + problem.getMessage(), 1, Integer::sum);
        }
      }
    }
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
}
