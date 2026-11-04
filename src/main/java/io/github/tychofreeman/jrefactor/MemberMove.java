package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * The text edits shared by the tools that move a method between classes: {@code pull_up}, {@code
 * push_down}.
 */
final class MemberMove {

  private MemberMove() {}

  /**
   * The edit that removes {@code m}, with its Javadoc, and the line it ends on, from {@code text}.
   */
  static TextEdit removal(String text, MethodDeclaration m) {
    return new TextEdit(Lines.start(text, m.getStartPosition()), Lines.end(text, end(m) - 1), "");
  }

  /**
   * The edit that adds {@code moved} as the last member of {@code type}, a blank line before it.
   */
  static TextEdit appendTo(String text, TypeDeclaration type, String moved) {
    int at = Lines.start(text, end(type) - 1);
    return new TextEdit(at, at, "\n  " + moved + "\n");
  }

  /** The text of {@code m} as written in {@code text}. */
  static String textOf(String text, MethodDeclaration m) {
    return text.substring(m.getStartPosition(), end(m));
  }

  /**
   * Applies {@code edits} to each file's text in {@code texts} (the file's imports fixed first),
   * and runs the result through the gate. Each file's edits are applied back to front.
   */
  static Stubs.Declared gate(
      SourceProject project,
      Map<Path, String> texts,
      Map<Path, List<TextEdit>> edits,
      String what,
      String fix)
      throws IOException {
    Map<Path, String> changed = new LinkedHashMap<>();
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      String before = texts.getOrDefault(e.getKey(), Files.readString(e.getKey()));
      List<TextEdit> list = new ArrayList<>(e.getValue());
      list.sort((a, b) -> b.start() - a.start());
      String after = TextEdit.apply(before, list, 0);
      changed.put(e.getKey(), Imports.fix(project, e.getKey(), before, after, List.of()).text());
    }
    return Gate.applyOrRefuse(project, changed, what, fix);
  }

  /** The top-level type of {@code unit} named {@code name}, or null. */
  static TypeDeclaration typeNamed(CompilationUnit unit, String name) {
    for (Object o : unit.types()) {
      if (o instanceof TypeDeclaration t && t.getName().getIdentifier().equals(name)) {
        return t;
      }
    }
    return null;
  }

  /** The offset just past {@code n}. */
  static int end(ASTNode n) {
    return n.getStartPosition() + n.getLength();
  }
}
