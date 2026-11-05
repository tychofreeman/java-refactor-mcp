package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * The check that code a tool moves keeps its meaning: each name in the text an edit writes must
 * resolve to a declaration it resolved to where the text came from. A name the moved text declares
 * (a local) is new, and one it did not have before (a qualifier the tool added) is the tool's own;
 * both are skipped. The gate cannot see this: a name captured by a closer declaration compiles.
 */
final class NameCheck {

  private NameCheck() {}

  /** What each name in {@code fragments} means: its identifier to its declarations' keys. */
  static Map<String, Set<String>> meanings(List<? extends ASTNode> fragments) {
    Map<String, Set<String>> out = new HashMap<>();
    for (ASTNode f : fragments) {
      f.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(SimpleName n) {
              String key = key(n.resolveBinding());
              if (key != null) {
                out.computeIfAbsent(n.getIdentifier(), k -> new HashSet<>()).add(key);
              }
              return true;
            }
          });
    }
    return out;
  }

  /**
   * Refuses when a name in the text {@code edits} write into a file of {@code texts} resolves,
   * after them, to a declaration it did not mean in {@code before}.
   */
  static void require(
      SourceProject project,
      Map<Path, String> texts,
      Map<Path, List<TextEdit>> edits,
      Map<String, Set<String>> before,
      String what)
      throws Exception {
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      String text = texts.get(e.getKey());
      if (text == null || e.getKey().toString().endsWith(".java") == false) {
        continue;
      }
      List<TextEdit> sorted = new ArrayList<>(e.getValue());
      sorted.sort(Comparator.comparingInt(TextEdit::start));
      List<int[]> written = new ArrayList<>();
      int shift = 0;
      for (TextEdit t : sorted) {
        int start = t.start() + shift;
        if (!t.text().isEmpty()) {
          written.add(new int[] {start, start + t.text().length()});
        }
        shift += t.text().length() - (t.end() - t.start());
      }
      if (written.isEmpty()) {
        continue;
      }
      String after = TextEdit.apply(text, e.getValue(), 0);
      CompilationUnit unit = Jdt.parse(project, e.getKey(), after);
      String rel = project.relative(e.getKey()).toString();
      unit.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(SimpleName n) {
              if (!inside(written, n.getStartPosition())) {
                return true;
              }
              IBinding b = n.resolveBinding();
              String key = key(b);
              Set<String> was = before.get(n.getIdentifier());
              if (key == null || was == null || was.contains(key)) {
                return true;
              }
              ASTNode declared = unit.findDeclaringNode(b);
              if (declared != null && inside(written, declared.getStartPosition())) {
                return true;
              }
              throw new Refusal(
                  what
                      + " would make "
                      + n.getIdentifier()
                      + " at line "
                      + unit.getLineNumber(n.getStartPosition())
                      + " of "
                      + rel
                      + " mean "
                      + describe(b)
                      + ", not what it meant where it was written: a closer declaration of that"
                      + " name hides it there. Rename one of them, or qualify it, first.");
            }
          });
    }
  }

  private static boolean inside(List<int[]> ranges, int at) {
    for (int[] r : ranges) {
      if (at >= r[0] && at < r[1]) {
        return true;
      }
    }
    return false;
  }

  private static String key(IBinding b) {
    return switch (b) {
      case null -> null;
      case ITypeBinding t -> t.getTypeDeclaration().getKey();
      case IMethodBinding m -> m.getMethodDeclaration().getKey();
      case IVariableBinding v -> v.getVariableDeclaration().getKey();
      default -> b.getKey();
    };
  }

  private static String describe(IBinding b) {
    return switch (b) {
      case IVariableBinding v when v.isField() && v.getDeclaringClass() != null ->
          "the field "
              + v.getName()
              + " of "
              + (v.getDeclaringClass().isAnonymous()
                  ? "an anonymous class"
                  : Names.typeLabel(v.getDeclaringClass()));
      case IVariableBinding v -> "the local " + v.getName();
      case IMethodBinding m -> Signatures.label(m);
      case ITypeBinding t -> "the type " + Names.typeLabel(t);
      default -> b.getName();
    };
  }
}
