package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;

/**
 * Extract method: whole statements of one block moved into a new private method, declared after the
 * member they are in, and a call of it in their place.
 */
final class Extract {

  private Extract() {}

  static Tools.Outcome method(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String name = args.required("name");
    Integer startArg = args.integer("start_line");
    Integer endArg = args.integer("end_line");
    if (startArg == null || endArg == null) {
      throw new Refusal("extract_method needs start_line and end_line.");
    }
    if (!SourceVersion.isName(name)) {
      throw new Refusal("name " + name + " is not a valid Java name.");
    }
    Path path = project.javaFile(rel);
    String text = Files.readString(path);
    String[] lines = text.split("\n", -1);
    int count = text.endsWith("\n") ? lines.length - 1 : lines.length;
    int start = startArg;
    int end = endArg;
    if (start < 1 || end < start || end > count) {
      throw new Refusal(
          "lines " + start + "-" + end + " are not in " + rel + ", which has " + count + " lines.");
    }
    while (start < end && lines[start - 1].isBlank()) {
      start++;
    }
    while (end > start && lines[end - 1].isBlank()) {
      end--;
    }
    CompilationUnit unit = Jdt.parse(project, path, text);
    Selection sel = Selection.of(unit, text, start, end);
    return new Extraction(project, path, rel, text, unit, sel, name, args.string("doc")).apply();
  }

  /** The statements chosen: the list they are in, and the first and last of them in it. */
  record Selection(
      CompilationUnit unit,
      List<Statement> list,
      int from,
      int to,
      int startLine,
      int endLine,
      int startOffset,
      int endOffset) {

    List<Statement> statements() {
      return list.subList(from, to + 1);
    }

    boolean contains(ASTNode node) {
      return node.getStartPosition() >= startOffset
          && node.getStartPosition() + node.getLength() <= endOffset;
    }

    String lines() {
      return Lines.range(startLine, endLine);
    }

    /**
     * The statements on lines {@code start}-{@code end}: the outermost statement list in a body
     * that they fall in, refused when the lines cut a statement.
     */
    static Selection of(CompilationUnit unit, String text, int start, int end) {
      int startOffset = unit.getPosition(start, 0);
      int endOffset = lineEndOffset(unit, text, end);
      String range = Lines.range(start, end);
      List<Statement> list = body(unit, startOffset, endOffset, range);
      Statement through = null;
      List<Statement> throughList = null;
      while (true) {
        List<Integer> over = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
          Statement s = list.get(i);
          if (s.getStartPosition() < endOffset && endOf(s) > startOffset) {
            over.add(i);
          }
        }
        if (over.isEmpty()) {
          if (through != null) {
            throw cut(unit, range, through, throughList);
          }
          throw new Refusal(range + " hold no statements; select whole statements of one block.");
        }
        Statement only = list.get(over.get(0));
        if (over.size() == 1 && !inside(only, startOffset, endOffset)) {
          List<Statement> inner = innerList(only, startOffset, endOffset);
          if (inner == null) {
            throw cut(unit, range, only, list);
          }
          through = only;
          throughList = list;
          list = inner;
          continue;
        }
        int from = over.get(0);
        int to = over.get(over.size() - 1);
        for (int i : over) {
          Statement s = list.get(i);
          if (!inside(s, startOffset, endOffset) || s instanceof SwitchCase) {
            throw cut(unit, range, s, list);
          }
        }
        int first = list.get(from).getStartPosition();
        int last = endOf(list.get(to));
        if (!onlyComments(unit, text, startOffset, first)
            || !onlyComments(unit, text, last, endOffset)) {
          throw through != null
              ? cut(unit, range, through, throughList)
              : cut(unit, range, list.get(from), list);
        }
        return new Selection(unit, list, from, to, start, end, startOffset, endOffset);
      }
    }

    /** The statements of the outermost body (method, initializer, lambda) holding the range. */
    private static List<Statement> body(
        CompilationUnit unit, int startOffset, int endOffset, String range) {
      List<Block> bodies = new ArrayList<>();
      unit.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration m) {
              add(m.getBody());
              return true;
            }

            @Override
            public boolean visit(Initializer i) {
              add(i.getBody());
              return true;
            }

            @Override
            public boolean visit(LambdaExpression l) {
              if (l.getBody() instanceof Block b) {
                add(b);
              }
              return true;
            }

            private void add(Block b) {
              if (b != null && b.getStartPosition() < startOffset && endOf(b) >= endOffset - 1) {
                bodies.add(b);
              }
            }
          });
      if (bodies.isEmpty()) {
        throw new Refusal(
            range + " are not inside a method body; select statements of a method or initializer.");
      }
      return asStatements(bodies.get(0).statements());
    }

    /** The outermost statement list inside {@code s} that the range overlaps, or null. */
    private static List<Statement> innerList(Statement s, int startOffset, int endOffset) {
      List<List<Statement>> found = new ArrayList<>();
      s.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(Block b) {
              return take(b, b.statements());
            }

            @Override
            public boolean visit(SwitchStatement sw) {
              return take(sw, sw.statements());
            }

            @Override
            public boolean visit(SwitchExpression sw) {
              return take(sw, sw.statements());
            }

            private boolean take(ASTNode node, List<?> list) {
              if (!found.isEmpty()) {
                return false;
              }
              if (node.getStartPosition() < endOffset && endOf(node) > startOffset) {
                for (Object o : list) {
                  Statement st = (Statement) o;
                  if (st.getStartPosition() < endOffset && endOf(st) > startOffset) {
                    found.add(asStatements(list));
                    return false;
                  }
                }
              }
              return true;
            }
          });
      return found.isEmpty() ? null : found.get(0);
    }

    private static Refusal cut(
        CompilationUnit unit, String range, Statement s, List<Statement> list) {
      List<String> there = new ArrayList<>();
      for (Statement st : list) {
        there.add(span(unit, st));
      }
      return new Refusal(
          range
              + " cut the statement at "
              + span(unit, s)
              + "; select whole statements of one block. There: "
              + String.join(", ", there)
              + ".");
    }

    private static boolean inside(ASTNode n, int startOffset, int endOffset) {
      return n.getStartPosition() >= startOffset && endOf(n) <= endOffset;
    }

    /** Whether {@code text} from {@code from} to {@code to} is only white space and comments. */
    private static boolean onlyComments(CompilationUnit unit, String text, int from, int to) {
      int i = from;
      while (i < to) {
        if (Character.isWhitespace(text.charAt(i))) {
          i++;
          continue;
        }
        int at = i;
        Comment in = null;
        for (Object o : unit.getCommentList()) {
          Comment c = (Comment) o;
          if (c.getStartPosition() == at) {
            in = c;
          }
        }
        if (in == null) {
          return false;
        }
        i = endOf(in);
      }
      return true;
    }
  }

  /** {@code b} as source names it, by simple names (imports are fixed after). */
  static String typeName(ITypeBinding b) {
    if (b == null || b.isNullType() || b.isRecovered()) {
      return "Object";
    }
    if (b.isPrimitive() || b.isTypeVariable()) {
      return b.getName();
    }
    if (b.isCapture()) {
      return typeName(b.getErasure());
    }
    if (b.isWildcardType()) {
      ITypeBinding bound = b.getBound();
      return bound == null ? "?" : (b.isUpperbound() ? "? extends " : "? super ") + typeName(bound);
    }
    if (b.isAnonymous()) {
      return typeName(b.getInterfaces().length > 0 ? b.getInterfaces()[0] : b.getSuperclass());
    }
    if (b.isArray()) {
      return typeName(b.getElementType()) + "[]".repeat(b.getDimensions());
    }
    if (b.isIntersectionType()) {
      return typeName(b.getTypeBounds()[0]);
    }
    ITypeBinding erasure = b.getErasure();
    String name = erasure.getName();
    for (ITypeBinding outer = erasure.getDeclaringClass();
        outer != null && !erasure.isLocal();
        outer = outer.getDeclaringClass()) {
      name = outer.getName() + "." + name;
      erasure = outer;
    }
    List<String> arguments = new ArrayList<>();
    for (ITypeBinding argument : b.getTypeArguments()) {
      arguments.add(typeName(argument));
    }
    return arguments.isEmpty() ? name : name + "<" + String.join(", ", arguments) + ">";
  }

  static String and(List<String> names) {
    if (names.size() == 1) {
      return names.get(0);
    }
    return String.join(", ", names.subList(0, names.size() - 1))
        + " and "
        + names.get(names.size() - 1);
  }

  private static List<Statement> asStatements(List<?> list) {
    List<Statement> out = new ArrayList<>();
    for (Object o : list) {
      out.add((Statement) o);
    }
    return out;
  }

  static <T extends ASTNode> T ancestor(ASTNode n, Class<T> kind) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (kind.isInstance(p)) {
        return kind.cast(p);
      }
    }
    return null;
  }

  /** The offset just past {@code node}. */
  static int endOf(ASTNode node) {
    return node.getStartPosition() + node.getLength();
  }

  /** The offset just past the line ending of 1-based line {@code line}. */
  static int lineEndOffset(CompilationUnit unit, String text, int line) {
    int at = unit.getPosition(line, 0);
    int nl = text.indexOf('\n', at);
    return nl < 0 ? text.length() : nl + 1;
  }

  private static String span(CompilationUnit unit, ASTNode n) {
    int a = unit.getLineNumber(n.getStartPosition());
    int b = unit.getLineNumber(endOf(n) - 1);
    return Lines.range(a, b);
  }
}
