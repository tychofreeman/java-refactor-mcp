package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * move_file: a file or directory that is not Java under a source root moves, is copied, or is
 * deleted. Each file goes through the journal as text, so undo puts it back.
 */
final class MoveFile {

  private MoveFile() {}

  static Outcome run(SourceProject project, String rel, String to, boolean copy, boolean delete)
      throws IOException {
    Path from = project.file(rel);
    Precondition.require(
        Precondition.that(() -> Files.exists(from), () -> rel + " does not exist."),
        Precondition.that(
            () -> delete || to != null,
            () -> "move needs to, the file's new path, or delete=true to remove " + rel + "."),
        Precondition.that(
            () -> !delete || (to == null && !copy),
            () -> "delete removes " + rel + "; pass to and copy only to move or copy it."));
    List<Path> files = new ArrayList<>();
    if (Files.isDirectory(from)) {
      try (Stream<Path> walk = Files.walk(from)) {
        walk.filter(Files::isRegularFile).sorted().forEach(files::add);
      }
    } else {
      files.add(from);
    }
    for (Path f : files) {
      if (f.toString().endsWith(".java") && project.packageOf(f) != null) {
        throw new Refusal(
            project.relative(f)
                + " is Java under a source root, and moving it would break its package. Use"
                + " move with name and package to move a type.");
      }
    }
    Path dest = to == null ? null : project.file(to);
    Precondition.require(
        Precondition.that(
            () -> dest == null || !Files.exists(dest),
            () ->
                project.relative(dest)
                    + " already exists. Move it away first, or choose another path."),
        Precondition.that(
            () -> dest == null || !dest.startsWith(from) || dest.equals(from),
            () -> project.relative(dest) + " is inside " + rel + ". Choose a path outside it."));

    List<String> texts = new ArrayList<>();
    for (Path f : files) {
      texts.add(text(project, f));
    }
    for (int i = 0; i < files.size(); i++) {
      Path f = files.get(i);
      if (dest != null) {
        Path target = f.equals(from) ? dest : dest.resolve(from.relativize(f));
        Gate.write(project, target, texts.get(i));
        Journal.copyMode(f, target);
      }
      if (!copy) {
        Gate.write(project, f, null);
      }
    }
    if (!copy && Files.isDirectory(from)) {
      removeEmpty(from);
    }

    String verb = delete ? "deleted" : copy ? "copied" : "moved";
    String result = verb + " " + rel + (dest == null ? "" : " to " + project.relative(dest));
    if (files.isEmpty() || !from.equals(files.get(0)) || files.size() > 1) {
      result += " (" + files.size() + (files.size() == 1 ? " file)" : " files)");
    }
    return Outcome.of(result);
  }

  /** {@code f} as UTF-8 text, or a refusal for a file that is not. */
  private static String text(SourceProject project, Path f) throws IOException {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(Files.readAllBytes(f)))
          .toString();
    } catch (CharacterCodingException e) {
      throw new Refusal(
          project.relative(f)
              + " is not UTF-8 text, and move moves text files so that undo can put them"
              + " back. Move this one with git mv.");
    }
  }

  /** Removes the directories under {@code dir}, and {@code dir}, that are empty. */
  private static void removeEmpty(Path dir) throws IOException {
    List<Path> dirs;
    try (Stream<Path> walk = Files.walk(dir)) {
      dirs = walk.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).toList();
    }
    for (Path d : dirs) {
      try (Stream<Path> inside = Files.list(d)) {
        if (inside.findAny().isEmpty()) {
          Files.delete(d);
        }
      }
    }
  }
}
