package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * The text edits and member lookup shared by the tools that move a method or a field between
 * classes: {@code pull_up} and {@code push_down}.
 */
final class MemberMove {

  private MemberMove() {}

  /**
   * The method or field {@code name} that {@code t} declares, for {@code tool} to move. A method
   * when there is one of that name, else a field; refused when there is neither, when both exist,
   * when there are overloads, or when the field shares its declaration with other variables.
   */
  static BodyDeclaration member(
      TypeDeclaration t, String subName, String name, String rel, String tool) {
    List<MethodDeclaration> methods = new ArrayList<>();
    for (MethodDeclaration m : t.getMethods()) {
      if (m.getName().getIdentifier().equals(name)) {
        methods.add(m);
      }
    }
    List<FieldDeclaration> fields = new ArrayList<>();
    for (FieldDeclaration f : t.getFields()) {
      if (declaresField(f, name)) {
        fields.add(f);
      }
    }
    if (methods.isEmpty() && fields.isEmpty()) {
      throw new Refusal(subName + " declares no method or field " + name + " in " + rel + ".");
    }
    if (!methods.isEmpty() && !fields.isEmpty()) {
      throw new Refusal(
          subName
              + " declares both a field and a method named "
              + name
              + "; "
              + tool
              + " cannot tell which to move.");
    }
    if (methods.size() > 1) {
      throw new Refusal(
          subName
              + " has several methods named "
              + name
              + "; "
              + tool
              + " moves one method, and overloads are not supported yet.");
    }
    if (!methods.isEmpty()) {
      return methods.get(0);
    }
    FieldDeclaration field = fields.get(0);
    if (field.fragments().size() > 1) {
      throw new Refusal(
          subName
              + "."
              + name
              + " shares its declaration with other variables; "
              + tool
              + " moves one variable per declaration.");
    }
    return field;
  }

  /** Whether {@code t} declares a method or a field named {@code name}. */
  static boolean declares(TypeDeclaration t, String name) {
    for (MethodDeclaration m : t.getMethods()) {
      if (m.getName().getIdentifier().equals(name)) {
        return true;
      }
    }
    for (FieldDeclaration f : t.getFields()) {
      if (declaresField(f, name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean declaresField(FieldDeclaration f, String name) {
    for (Object o : f.fragments()) {
      if (((VariableDeclarationFragment) o).getName().getIdentifier().equals(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The edit that removes {@code m}, with its Javadoc, and the line it ends on, from {@code text}.
   */
  static TextEdit removal(String text, BodyDeclaration m) {
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
  static String textOf(String text, BodyDeclaration m) {
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
