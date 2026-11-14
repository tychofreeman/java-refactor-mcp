package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * A Java file of the project, read and parsed with bindings: where a refactoring finds its target.
 * Each finder refuses, in one wording for every tool, when the target is not there.
 */
record Opened(SourceProject project, String rel, Path file, String text, CompilationUnit unit) {

  /** The Java file {@code rel} of {@code project}, read and parsed. */
  static Opened of(SourceProject project, String rel) throws IOException {
    Path file = project.javaFile(rel).toAbsolutePath().normalize();
    String text = Files.readString(file);
    return new Opened(project, rel, file, text, Jdt.parse(project, file, text));
  }

  /** The top-level class or interface named {@code name}, which must resolve. */
  TypeDeclaration topLevel(String name) {
    TypeDeclaration type = MemberMove.typeNamed(unit, name);
    if (type == null) {
      notAClass(name);
    }
    if (type == null || type.resolveBinding() == null) {
      throw new Refusal(name + " is not a top-level type of " + rel + " that resolves.");
    }
    return type;
  }

  /**
   * The one node of {@code type} that starts on {@code line} and passes {@code where}. {@code noun}
   * names it in a refusal ("if statement"), {@code place} says where it must be (", directly in a
   * block", or empty), and {@code start} what the model should pass the line of ("the if").
   */
  <T extends ASTNode> T startingOn(
      int line, Class<T> type, Predicate<T> where, String noun, String place, String start) {
    List<T> found = new ArrayList<>();
    for (T node : onLine(line, type)) {
      if (where.test(node)) {
        found.add(node);
      }
    }
    if (found.isEmpty()) {
      throw new Refusal(
          "no "
              + noun
              + " starts on line "
              + line
              + " of "
              + rel
              + place
              + ". Pass the line where "
              + start
              + " starts.");
    }
    if (found.size() > 1) {
      throw new Refusal(
          "several "
              + noun
              + "s start on line "
              + line
              + " of "
              + rel
              + ". Put each on a line of its own first.");
    }
    return found.get(0);
  }

  /**
   * Refuses {@code name} when the file declares it as a record, an enum or an annotation type,
   * saying which, and the classes and interfaces it does declare; returns otherwise.
   */
  private void notAClass(String name) {
    List<String> classes = new ArrayList<>();
    String kind = null;
    for (Object o : unit.types()) {
      AbstractTypeDeclaration t = (AbstractTypeDeclaration) o;
      if (t instanceof TypeDeclaration) {
        classes.add(t.getName().getIdentifier());
      } else if (t.getName().getIdentifier().equals(name)) {
        kind =
            t instanceof RecordDeclaration
                ? "a record"
                : t instanceof EnumDeclaration ? "an enum" : "an annotation type";
      }
    }
    if (kind != null) {
      String others =
          classes.isEmpty()
              ? " Its file " + rel + " declares no class or interface to send in its place."
              : " Send the name of one: " + rel + " declares " + String.join(", ", classes) + ".";
      throw new Refusal(
          name + " is " + kind + ", and this tool takes a class or interface here." + others);
    }
  }

  /** The top-level class, interface or record named {@code name}, which must resolve. */
  AbstractTypeDeclaration classOrRecord(String name) {
    for (Object o : unit.types()) {
      if (o instanceof RecordDeclaration r
          && r.getName().getIdentifier().equals(name)
          && r.resolveBinding() != null) {
        return r;
      }
    }
    return topLevel(name);
  }

  /**
   * The one node of {@code type} that starts on {@code line}, at {@code column} when it is not
   * null, and passes {@code where}. {@code noun} names one in a refusal ("lambda"), {@code nouns}
   * several ("lambdas").
   */
  <T extends ASTNode> T startingOn(
      int line, Integer column, Class<T> type, Predicate<T> where, String noun, String nouns) {
    List<T> found = new ArrayList<>();
    for (T node : onLine(line, type)) {
      if (where.test(node)) {
        found.add(node);
      }
    }
    if (found.isEmpty()) {
      throw new Refusal(
          "no "
              + noun
              + " starts on line "
              + line
              + " of "
              + rel
              + ". Pass the line where it starts.");
    }
    found = atColumn(found, line, column, "the " + noun);
    if (found.size() > 1) {
      throw several(found, line, nouns);
    }
    return found.get(0);
  }

  /** The nodes of {@code type} that start on {@code line}, in source order. */
  <T extends ASTNode> List<T> onLine(int line, Class<T> type) {
    List<T> out = new ArrayList<>();
    for (T node : Nodes.all(unit, type)) {
      if (unit.getLineNumber(node.getStartPosition()) == line) {
        out.add(node);
      }
    }
    return out;
  }

  /**
   * Those of {@code found}, nodes that start on {@code line}, that start at {@code column}: all of
   * them when it is null. Refuses when none does, naming the columns where they start; {@code one}
   * names them in the refusal ("subtotal").
   */
  <T extends ASTNode> List<T> atColumn(List<T> found, int line, Integer column, String one) {
    if (column == null) {
      return found;
    }
    int lineStart = unit.getPosition(line, 0);
    List<T> at = new ArrayList<>();
    for (T node : found) {
      if (node.getStartPosition() - lineStart + 1 == column) {
        at.add(node);
      }
    }
    if (at.isEmpty()) {
      throw new Refusal(
          one
              + (found.size() == 1 ? " starts at column " : " starts at columns ")
              + Nodes.columns(found, lineStart)
              + " of line "
              + line
              + ", not "
              + column
              + ". Pass one of them.");
    }
    return at;
  }

  /**
   * The refusal for {@code found}, several nodes on {@code line} where one is meant: it names the
   * columns where they start. {@code nouns} names them ("variables named x").
   */
  Refusal several(List<? extends ASTNode> found, int line, String nouns) {
    return new Refusal(
        found.size()
            + " "
            + nouns
            + " start on line "
            + line
            + ", at columns "
            + Nodes.columns(found, unit.getPosition(line, 0))
            + ". Pass column, the one where the one you mean starts.");
  }

  /**
   * The package declaration a new file beside this one starts with, or "" in the default package.
   */
  String packageHeader() {
    return packageHeader(
        unit.getPackage() == null ? null : unit.getPackage().getName().getFullyQualifiedName());
  }

  /** The package declaration of a new file in {@code pkg}, or "" for the default package. */
  static String packageHeader(String pkg) {
    return pkg == null || pkg.isEmpty() ? "" : "package " + pkg + ";\n\n";
  }
}
