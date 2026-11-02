package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * Renames a local variable or parameter, and every use of it in the method it is declared in.
 * Refuses fields, and names that would clash with a variable the method already uses. The edit is
 * applied, then the project is type-checked; if the edit adds compile errors it is rolled back and
 * refused.
 */
public final class RenameLocal {

  private RenameLocal() {}

  /** What changed: the file, how many names were renamed, and the lines they are on. */
  public record Result(String file, int occurrences, List<Integer> lines) {}

  /**
   * @param relFile path relative to the project root
   * @param line 1-based line of any use of the variable, or its declaration
   * @param column 1-based column where the name starts
   */
  public static Result run(
      SourceProject project, String relFile, int line, int column, String newName)
      throws IOException {
    Path file = project.root().resolve(relFile).normalize();
    if (!file.startsWith(project.root())
        || !Files.isRegularFile(file)
        || !relFile.endsWith(".java")) {
      throw new Refusal(
          "'"
              + relFile
              + "' is not a Java file in this project. Pass a path relative to the project root.");
    }
    if (!SourceVersion.isIdentifier(newName) || SourceVersion.isKeyword(newName)) {
      throw new Refusal(
          "'"
              + newName
              + "' is not a valid Java name. Use letters, digits, '_' or '$', "
              + "starting with a letter, '_' or '$', and not a keyword.");
    }

    String source = Files.readString(file);
    int offset = Positions.offset(source, line, column);
    CompilationUnit cu = Jdt.parse(project, file, source);

    ASTNode covering = NodeFinder.perform(cu, offset, 0);
    if (!(covering instanceof SimpleName name)) {
      throw new Refusal(
          "line "
              + line
              + ", column "
              + column
              + " is not on a name. "
              + "Put the position on the variable's name, at a use or at its declaration.");
    }
    String oldName = name.getIdentifier();

    if (!(name.resolveBinding() instanceof IVariableBinding variable)) {
      throw new Refusal(
          "'"
              + oldName
              + "' is not a variable. rename_local only renames local variables "
              + "and parameters.");
    }
    if (variable.isField()) {
      throw new Refusal(
          "'"
              + oldName
              + "' is a field. rename_local only renames local variables and "
              + "parameters; renaming fields is not built yet.");
    }

    ASTNode scope = scopeOf(cu, variable, oldName);
    Collected found = collect(scope, variable, newName);
    if (found.clash() != null) {
      throw new Refusal(
          "renaming '"
              + oldName
              + "' to '"
              + newName
              + "' would clash: '"
              + newName
              + "' is already a variable used in this method (line "
              + cu.getLineNumber(found.clash().getStartPosition())
              + "). Choose another name.");
    }

    Map<String, Integer> before = TypeCheck.errors(project);
    String rewritten = apply(source, found.occurrences(), newName);
    Files.writeString(file, rewritten);
    List<String> introduced;
    try {
      introduced = TypeCheck.introduced(before, TypeCheck.errors(project));
    } catch (IOException | RuntimeException e) {
      Files.writeString(file, source);
      throw e;
    }
    if (!introduced.isEmpty()) {
      Files.writeString(file, source);
      throw new Refusal(
          "renaming '"
              + oldName
              + "' to '"
              + newName
              + "' would introduce compile errors, "
              + "so nothing was changed:\n  "
              + String.join("\n  ", introduced)
              + "\nChoose a name that does not clash with a name the code already uses.");
    }

    TreeSet<Integer> lines = new TreeSet<>();
    for (SimpleName occurrence : found.occurrences()) {
      lines.add(cu.getLineNumber(occurrence.getStartPosition()));
    }
    return new Result(project.relative(file), found.occurrences().size(), new ArrayList<>(lines));
  }

  /** The method, initializer or field the variable is declared in; the compilation unit if none. */
  private static ASTNode scopeOf(CompilationUnit cu, IVariableBinding variable, String name) {
    ASTNode declaration = cu.findDeclaringNode(variable);
    if (declaration == null) {
      throw new Refusal(
          "cannot find where '"
              + name
              + "' is declared. The file may not compile; "
              + "fix the compile errors first.");
    }
    ASTNode scope = declaration;
    while (scope != null && !(scope instanceof BodyDeclaration)) {
      scope = scope.getParent();
    }
    return scope != null ? scope : cu;
  }

  private record Collected(List<SimpleName> occurrences, SimpleName clash) {}

  /**
   * Uses of {@code variable} in {@code scope}, and the first use of {@code newName} as another
   * variable.
   */
  private static Collected collect(ASTNode scope, IVariableBinding variable, String newName) {
    List<SimpleName> occurrences = new ArrayList<>();
    SimpleName[] clash = new SimpleName[1];
    scope.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName node) {
            IBinding binding = node.resolveBinding();
            if (binding instanceof IVariableBinding other) {
              if (other.isEqualTo(variable)) {
                occurrences.add(node);
              } else if (clash[0] == null && node.getIdentifier().equals(newName)) {
                clash[0] = node;
              }
            }
            return true;
          }
        });
    occurrences.sort(Comparator.comparingInt(ASTNode::getStartPosition));
    return new Collected(occurrences, clash[0]);
  }

  /** Replaces each occurrence, working from the end so earlier offsets stay valid. */
  private static String apply(String source, List<SimpleName> occurrences, String newName) {
    StringBuilder out = new StringBuilder(source);
    for (int i = occurrences.size() - 1; i >= 0; i--) {
      SimpleName node = occurrences.get(i);
      int start = node.getStartPosition();
      out.replace(start, start + node.getLength(), newName);
    }
    return out.toString();
  }
}
