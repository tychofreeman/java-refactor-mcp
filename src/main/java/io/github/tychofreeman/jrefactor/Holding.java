package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * The project's files whose text holds a name, with their texts and their units parsed together, in
 * file order: what a rename or a move walks. A file without the name cannot refer to it.
 */
record Holding(Map<Path, String> texts, Map<Path, CompilationUnit> units) {

  /** The files that hold {@code name}, and {@code also} (none when null) whatever it holds. */
  static Holding of(SourceProject project, String name, Path also) throws IOException {
    Map<Path, String> texts = new LinkedHashMap<>();
    for (Path f : project.javaFiles()) {
      String text = Files.readString(f);
      if (f.equals(also) || text.contains(name)) {
        texts.put(f, text);
      }
    }
    return new Holding(texts, Jdt.parseAll(project, new ArrayList<>(texts.keySet())));
  }
}
