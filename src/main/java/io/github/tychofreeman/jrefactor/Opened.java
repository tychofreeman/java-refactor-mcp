package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
}
