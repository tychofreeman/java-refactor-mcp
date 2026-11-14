package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
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
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean preVisit2(ASTNode node) {
            if (type.isInstance(node)
                && where.test(type.cast(node))
                && Lines.number(text, node.getStartPosition()) == line) {
              found.add(type.cast(node));
            }
            return true;
          }
        });
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
}
