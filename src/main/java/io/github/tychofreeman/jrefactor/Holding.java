package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * The project's files whose text holds a name, with their texts and their units parsed together, in
 * file order: what a rename or a move walks. A file without the name cannot refer to it.
 */
record Holding(Map<Path, String> texts, Map<Path, CompilationUnit> units) {

  /** The files that hold {@code name}, and each of {@code also} whatever it holds. */
  static Holding of(SourceProject project, String name, Path... also) throws IOException {
    Set<Path> given =
        Stream.of(also).map(p -> p.toAbsolutePath().normalize()).collect(Collectors.toSet());
    return where(project, (file, text) -> given.contains(file) || text.contains(name));
  }

  /**
   * The files whose path and text {@code holds} accepts, in file order. Their units are parsed from
   * disk, so the texts are the files' own.
   */
  static Holding where(SourceProject project, BiPredicate<Path, String> holds) throws IOException {
    Map<Path, String> texts = new LinkedHashMap<>();
    for (Path f : project.javaFiles()) {
      String text = Files.readString(f);
      if (holds.test(f, text)) {
        texts.put(f, text);
      }
    }
    return new Holding(texts, Jdt.parseAll(project, new ArrayList<>(texts.keySet())));
  }
}
