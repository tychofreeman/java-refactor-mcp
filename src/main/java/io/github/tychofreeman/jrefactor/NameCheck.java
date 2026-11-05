package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
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
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

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
              IBinding b = n.resolveBinding();
              String key = key(b);
              if (key != null) {
                out.computeIfAbsent(n.getIdentifier(), k -> new HashSet<>()).add(key);
              }
              // Keys of what an anonymous or local class declares hold source positions, which an
              // edit earlier in the file shifts; where the declaration stands does not change.
              ASTNode declared =
                  b != null && n.getRoot() instanceof CompilationUnit u
                      ? u.findDeclaringNode(b)
                      : null;
              if (declared != null) {
                out.computeIfAbsent(n.getIdentifier(), k -> new HashSet<>())
                    .add(at(declared.getStartPosition()));
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
      throws IOException {
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      if (!e.getKey().toString().endsWith(".java")
          || (!texts.containsKey(e.getKey()) && !Files.isRegularFile(e.getKey()))) {
        continue;
      }
      String text =
          texts.containsKey(e.getKey()) ? texts.get(e.getKey()) : Files.readString(e.getKey());
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
              // A qualified name (a.x, e.m()) resolves through its qualifier, not through
              // scope, so no closer declaration can capture it.
              if (!inside(written, n.getStartPosition()) || qualified(n)) {
                return true;
              }
              IBinding b = n.resolveBinding();
              String key = key(b);
              Set<String> was = before.get(n.getIdentifier());
              // A recovered binding is a name the edit's file has not imported yet (the import fix
              // runs after), and a type variable belongs to its class: the gate judges both.
              if (key == null
                  || was == null
                  || was.contains(key)
                  || b.isRecovered()
                  || (b instanceof ITypeBinding t && t.isTypeVariable())) {
                return true;
              }
              ASTNode declared = unit.findDeclaringNode(b);
              if (declared != null
                  && (inside(written, declared.getStartPosition())
                      || was.contains(at(before(sorted, declared.getStartPosition()))))) {
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

  /** The meaning of a name by where its declaration starts in its file. */
  private static String at(int offset) {
    return "@" + offset;
  }

  /**
   * Where {@code offset} after {@code sorted} (in order, not overlapping) was before them, for an
   * offset no edit wrote.
   */
  private static int before(List<TextEdit> sorted, int offset) {
    int shift = 0;
    for (TextEdit t : sorted) {
      int end = t.start() + shift + t.text().length();
      if (end > offset) {
        break;
      }
      shift += t.text().length() - (t.end() - t.start());
    }
    return offset - shift;
  }

  /** Whether {@code n} is the name part of a qualified name, field access, call or reference. */
  private static boolean qualified(SimpleName n) {
    ASTNode p = n.getParent();
    return (p instanceof QualifiedName q && q.getName() == n)
        || (p instanceof FieldAccess f && f.getName() == n)
        || (p instanceof MethodInvocation m && m.getName() == n && m.getExpression() != null)
        || p instanceof SuperMethodInvocation
        || p instanceof SuperFieldAccess
        || p instanceof MethodReference;
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
