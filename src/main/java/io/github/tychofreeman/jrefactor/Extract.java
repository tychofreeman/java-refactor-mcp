package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

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
  private record Selection(
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
      return startLine == endLine ? "line " + startLine : "lines " + startLine + "-" + endLine;
    }

    /**
     * The statements on lines {@code start}-{@code end}: the outermost statement list in a body
     * that they fall in, refused when the lines cut a statement.
     */
    static Selection of(CompilationUnit unit, String text, int start, int end) {
      int startOffset = unit.getPosition(start, 0);
      int endOffset = lineEndOffset(unit, text, end);
      String range = start == end ? "line " + start : "lines " + start + "-" + end;
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

    /** The statements of the innermost body (method, initializer, lambda) holding the range. */
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

  /** The new method worked out from a selection, and the edit that makes it. */
  private static final class Extraction {
    final SourceProject project;
    final Path path;
    final String rel;
    final String text;
    final CompilationUnit unit;
    final Selection sel;
    final String name;
    final String doc;
    final BodyDeclaration member;

    Extraction(
        SourceProject project,
        Path path,
        String rel,
        String text,
        CompilationUnit unit,
        Selection sel,
        String name,
        String doc) {
      this.project = project;
      this.path = path;
      this.rel = rel;
      this.text = text;
      this.unit = unit;
      this.sel = sel;
      this.name = name;
      this.doc = doc;
      this.member = ancestor(sel.list().get(0), BodyDeclaration.class);
    }

    Tools.Outcome apply() throws Exception {
      refuseClash();
      refuseConstructorCalls();
      refuseJumps();
      Map<IVariableBinding, Boolean> used = variables();
      List<IVariableBinding> params = new ArrayList<>();
      List<IVariableBinding> results = new ArrayList<>();
      for (Map.Entry<IVariableBinding, Boolean> e : used.entrySet()) {
        IVariableBinding v = e.getKey();
        boolean declaredInside = e.getValue();
        if (!declaredInside) {
          params.add(v);
        }
        if (declaredInside
            ? declaredAtTop(v) && usedAfter(v)
            : assigned(v) && (usedAfter(v) || inLoop(v))) {
          results.add(v);
        }
      }
      if (results.size() > 1) {
        throw new Refusal(
            sel.lines()
                + " set "
                + and(names(results))
                + ", which the code after them uses, and a method returns one value. Extract"
                + " fewer lines, so that at most one of them is set and used later.");
      }
      List<ReturnStatement> returns = returns();
      ITypeBinding enclosingResult = enclosingResult();
      boolean voidResult = enclosingResult == null || "void".equals(enclosingResult.getName());
      if (!returns.isEmpty()) {
        if (!results.isEmpty()) {
          throw new Refusal(
              sel.lines()
                  + " both return and set "
                  + results.get(0).getName()
                  + ", which later code uses; a method returns one value. Extract fewer lines.");
        }
        if (voidResult && !endsBody()) {
          List<Statement> list = sel.list();
          throw new Refusal(
              "the return at line "
                  + line(returns.get(0))
                  + " would return from "
                  + name
                  + " only, and the code after "
                  + sel.lines()
                  + " would still run. Extract statements up to the end of the body (lines "
                  + sel.startLine()
                  + "-"
                  + line(endOf(list.get(list.size() - 1)) - 1)
                  + "), or lines without a return.");
        }
        List<Statement> chosen = sel.statements();
        if (!voidResult && !terminates(chosen.get(chosen.size() - 1))) {
          throw new Refusal(
              "the return at line "
                  + line(returns.get(0))
                  + " is on some paths only: after line "
                  + sel.endLine()
                  + " the code goes on, and "
                  + name
                  + " would have no value to return. Include the statements up to a return on"
                  + " every path, or lines without a return.");
        }
      }

      boolean isStatic = Modifier.isStatic(member.getModifiers());
      String resultType;
      String returnLine = null;
      String call;
      String invocation = name + "(" + String.join(", ", names(params)) + ")";
      if (!results.isEmpty()) {
        IVariableBinding r = results.get(0);
        resultType = typeName(r.getType());
        returnLine = "return " + r.getName() + ";";
        call =
            used.get(r)
                ? (Modifier.isFinal(r.getModifiers()) ? "final " : "")
                    + resultType
                    + " "
                    + r.getName()
                    + " = "
                    + invocation
                    + ";"
                : r.getName() + " = " + invocation + ";";
      } else if (!returns.isEmpty() && !voidResult) {
        resultType = typeName(enclosingResult);
        call = "return " + invocation + ";";
      } else {
        resultType = "void";
        call = invocation + ";";
      }

      List<String> declared = new ArrayList<>();
      for (IVariableBinding v : params) {
        declared.add(typeName(v.getType()) + " " + v.getName());
      }
      String signature = name + "(" + String.join(", ", declared) + ")";
      List<String> thrown = thrown();
      String header =
          "private "
              + (isStatic ? "static " : "")
              + typeParameters()
              + resultType
              + " "
              + signature
              + (thrown.isEmpty() ? "" : " throws " + String.join(", ", thrown))
              + " {";

      String memberIndent = indentOf(lineStartOffset(member.getStartPosition()));
      int firstStatement = sel.statements().get(0).getStartPosition();
      String statementIndent = indentOf(lineStartOffset(firstStatement));
      int step = Declarations.step(text);
      String bodyIndent = memberIndent + " ".repeat(step > 0 ? step : 4);

      StringBuilder method = new StringBuilder("\n");
      if (doc != null && !doc.isBlank()) {
        method.append(javadoc(doc.strip(), memberIndent));
      }
      method.append(memberIndent).append(header).append("\n");
      method.append(reindent(statementIndent, bodyIndent));
      if (returnLine != null) {
        method.append(bodyIndent).append(returnLine).append("\n");
      }
      method.append(memberIndent).append("}\n");

      int insertAt = lineEndOffset(unit, text, line(endOf(member) - 1));
      String callLine = statementIndent + call + "\n";
      String edited =
          text.substring(0, sel.startOffset())
              + callLine
              + text.substring(sel.endOffset(), insertAt)
              + method
              + text.substring(insertAt);
      int methodAt = sel.startOffset() + callLine.length() + (insertAt - sel.endOffset()) + 1;
      Imports.Fixed fixed = Imports.fix(project, path, text, edited, List.of());
      Stubs.Declared stubs =
          Gate.applyOrRefuse(
              project,
              Map.of(path, fixed.text()),
              "extracting " + sel.lines() + " into " + name,
              "Nothing was changed. Choose lines whose variables and exceptions the new method can"
                  + " take, or adjust them first.");
      int shift = fixed.lineShift();
      int first = Edits.lineOf(edited, methodAt) + shift;
      int last = Edits.lineOf(edited, methodAt + method.length() - 2) + shift;
      return new Tools.Outcome(
          "extracted "
              + sel.lines()
              + " of "
              + rel
              + " into "
              + signature
              + (results.isEmpty() ? "" : ", returning " + results.get(0).getName())
              + ", at lines "
              + first
              + "-"
              + last
              + "; the call is line "
              + (sel.startLine() + shift)
              + (params.size() > 5
                  ? "; it takes " + params.size() + " parameters, and fewer lines may read better"
                  : "")
              + fixed.said(),
          stubs);
    }

    /** Refuses a name some method of the type, or of its supertypes, already has. */
    void refuseClash() {
      ASTNode owner = member.getParent();
      ITypeBinding type =
          owner instanceof AbstractTypeDeclaration t
              ? t.resolveBinding()
              : owner instanceof AnonymousClassDeclaration a ? a.resolveBinding() : null;
      String label =
          owner instanceof AbstractTypeDeclaration t
              ? t.getName().getIdentifier()
              : "the anonymous class";
      if (type == null) {
        return;
      }
      List<ITypeBinding> todo = new ArrayList<>(List.of(type));
      Set<String> seen = new LinkedHashSet<>();
      while (!todo.isEmpty()) {
        ITypeBinding t = todo.remove(0);
        if (!seen.add(t.getErasure().getKey())) {
          continue;
        }
        for (IMethodBinding m : t.getDeclaredMethods()) {
          if (m.getName().equals(name)) {
            throw new Refusal(
                label
                    + (t == type ? "" : " (through " + t.getErasure().getName() + ")")
                    + " already has a method "
                    + name
                    + ", and another of that name could change which one calls pick. Choose"
                    + " another name.");
          }
        }
        if (t.getSuperclass() != null) {
          todo.add(t.getSuperclass());
        }
        todo.addAll(List.of(t.getInterfaces()));
      }
    }

    void refuseConstructorCalls() {
      for (Statement s : sel.statements()) {
        if (s instanceof ConstructorInvocation || s instanceof SuperConstructorInvocation) {
          throw new Refusal(
              "line "
                  + line(s)
                  + " calls a constructor (this(...) or super(...)), which must stay first in"
                  + " the constructor. Start the range after it.");
        }
      }
    }

    /** Refuses a break, continue or yield whose target is outside the statements. */
    void refuseJumps() {
      for (ASTNode n : own()) {
        ASTNode target = null;
        String what = null;
        if (n instanceof BreakStatement b) {
          what = "break";
          target =
              b.getLabel() != null ? labeled(b, b.getLabel().getIdentifier()) : nearest(b, true);
        } else if (n instanceof ContinueStatement c) {
          what = "continue";
          target =
              c.getLabel() != null ? labeled(c, c.getLabel().getIdentifier()) : nearest(c, false);
        } else if (n instanceof YieldStatement y) {
          what = "yield";
          target = ancestor(y, SwitchExpression.class);
        }
        if (target != null && !sel.contains(target)) {
          throw new Refusal(
              "the "
                  + what
                  + " at line "
                  + line(n)
                  + " jumps to line "
                  + line(target)
                  + ", outside "
                  + sel.lines()
                  + ", and would not compile in a method of its own. Include the statement it"
                  + " leaves (it starts at line "
                  + line(target)
                  + "), or lines without it.");
        }
      }
    }

    /** The loop (or, for a break, switch statement) a jump without a label leaves. */
    private static ASTNode nearest(ASTNode n, boolean orSwitch) {
      for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
        if (loop(p) || (orSwitch && p instanceof SwitchStatement)) {
          return p;
        }
        if (p instanceof LambdaExpression || p instanceof BodyDeclaration) {
          return null;
        }
      }
      return null;
    }

    private static ASTNode labeled(ASTNode n, String label) {
      for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
        if (p instanceof LabeledStatement l && l.getLabel().getIdentifier().equals(label)) {
          return l;
        }
      }
      return null;
    }

    private static boolean loop(ASTNode p) {
      return p instanceof ForStatement
          || p instanceof EnhancedForStatement
          || p instanceof WhileStatement
          || p instanceof DoStatement;
    }

    /**
     * The nodes of the statements that run as part of them, leaving out the bodies of lambdas and
     * of anonymous and local classes.
     */
    List<ASTNode> own() {
      List<ASTNode> out = new ArrayList<>();
      for (Statement s : sel.statements()) {
        s.accept(
            new ASTVisitor() {
              @Override
              public void preVisit(ASTNode node) {
                out.add(node);
              }

              @Override
              public boolean visit(LambdaExpression l) {
                return false;
              }

              @Override
              public boolean visit(AnonymousClassDeclaration a) {
                return false;
              }

              @Override
              public boolean visit(TypeDeclarationStatement t) {
                return false;
              }
            });
      }
      return out;
    }

    /** Every node of the statements. */
    private List<ASTNode> allNodes() {
      List<ASTNode> out = new ArrayList<>();
      for (Statement s : sel.statements()) {
        s.accept(
            new ASTVisitor() {
              @Override
              public void preVisit(ASTNode node) {
                out.add(node);
              }
            });
      }
      return out;
    }

    /**
     * The locals the statements use, in order of first use, each with whether the statements
     * declare it.
     */
    Map<IVariableBinding, Boolean> variables() {
      Map<IVariableBinding, Boolean> out = new LinkedHashMap<>();
      for (ASTNode n : allNodes()) {
        if (n instanceof SimpleName s
            && s.resolveBinding() instanceof IVariableBinding v
            && !v.isField()) {
          IVariableBinding key = v.getVariableDeclaration();
          ASTNode decl = unit.findDeclaringNode(key);
          if (!out.containsKey(key) && decl != null) {
            out.put(key, sel.contains(decl));
          }
        }
      }
      return out;
    }

    /** Whether {@code v}, declared in the statements, is declared by one of them directly. */
    boolean declaredAtTop(IVariableBinding v) {
      ASTNode decl = unit.findDeclaringNode(v);
      return decl != null
          && decl.getParent() instanceof VariableDeclarationStatement s
          && sel.statements().contains(s);
    }

    /** Whether code of the enclosing member after the statements uses {@code v}. */
    boolean usedAfter(IVariableBinding v) {
      boolean[] found = {false};
      member.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(SimpleName n) {
              if (n.getStartPosition() >= sel.endOffset()
                  && n.resolveBinding() instanceof IVariableBinding b
                  && b.getVariableDeclaration().isEqualTo(v)) {
                found[0] = true;
              }
              return true;
            }
          });
      return found[0];
    }

    /** Whether the statements assign {@code v}. */
    boolean assigned(IVariableBinding v) {
      for (ASTNode n : allNodes()) {
        ASTNode target = null;
        if (n instanceof Assignment a) {
          target = a.getLeftHandSide();
        } else if (n instanceof PostfixExpression p) {
          target = p.getOperand();
        } else if (n instanceof PrefixExpression p
            && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                || p.getOperator() == PrefixExpression.Operator.DECREMENT)) {
          target = p.getOperand();
        }
        if (target instanceof SimpleName s
            && s.resolveBinding() instanceof IVariableBinding b
            && b.getVariableDeclaration().isEqualTo(v)) {
          return true;
        }
      }
      return false;
    }

    /** Whether the statements are in a loop that {@code v}, declared outside them, outlives. */
    boolean inLoop(IVariableBinding v) {
      ASTNode decl = unit.findDeclaringNode(v);
      for (ASTNode p = sel.list().get(0).getParent(); p != null; p = p.getParent()) {
        if (p instanceof LambdaExpression || p instanceof BodyDeclaration) {
          return false;
        }
        if (loop(p)
            && decl != null
            && !(decl.getStartPosition() >= p.getStartPosition()
                && decl.getStartPosition() < endOf(p))) {
          return true;
        }
      }
      return false;
    }

    List<ReturnStatement> returns() {
      List<ReturnStatement> out = new ArrayList<>();
      for (ASTNode n : own()) {
        if (n instanceof ReturnStatement r) {
          out.add(r);
        }
      }
      return out;
    }

    /** The result type of the method or lambda the statements are in; null in an initializer. */
    ITypeBinding enclosingResult() {
      for (ASTNode p = sel.list().get(0).getParent(); p != null; p = p.getParent()) {
        if (p instanceof LambdaExpression l) {
          IMethodBinding m = l.resolveMethodBinding();
          return m == null ? null : m.getReturnType();
        }
        if (p instanceof MethodDeclaration m) {
          IMethodBinding b = m.resolveBinding();
          return b == null || m.isConstructor() ? null : b.getReturnType();
        }
        if (p instanceof BodyDeclaration) {
          return null;
        }
      }
      return null;
    }

    /** Whether the statements are the last ones of the enclosing method's or lambda's body. */
    boolean endsBody() {
      List<Statement> list = sel.list();
      ASTNode block = list.get(0).getParent();
      return sel.to() == list.size() - 1
          && block instanceof Block
          && (block.getParent() instanceof MethodDeclaration
              || block.getParent() instanceof LambdaExpression);
    }

    /** Whether {@code s} never completes normally: it returns or throws on every path. */
    private static boolean terminates(Statement s) {
      if (s instanceof ReturnStatement || s instanceof ThrowStatement) {
        return true;
      }
      if (s instanceof Block b) {
        List<?> list = b.statements();
        return !list.isEmpty() && terminates((Statement) list.get(list.size() - 1));
      }
      if (s instanceof IfStatement i) {
        return i.getElseStatement() != null
            && terminates(i.getThenStatement())
            && terminates(i.getElseStatement());
      }
      return false;
    }

    /** The checked exceptions the statements throw and do not catch. */
    List<String> thrown() {
      List<ITypeBinding> types = new ArrayList<>();
      for (ASTNode n : own()) {
        List<ITypeBinding> from = new ArrayList<>();
        IMethodBinding m = null;
        if (n instanceof MethodInvocation i) {
          m = i.resolveMethodBinding();
        } else if (n instanceof SuperMethodInvocation i) {
          m = i.resolveMethodBinding();
        } else if (n instanceof ClassInstanceCreation c) {
          m = c.resolveConstructorBinding();
        }
        if (m != null) {
          from.addAll(List.of(m.getExceptionTypes()));
        }
        if (n instanceof ThrowStatement t && t.getExpression().resolveTypeBinding() != null) {
          from.add(t.getExpression().resolveTypeBinding());
        }
        for (ITypeBinding t : from) {
          if (checked(t) && !caught(n, t) && types.stream().noneMatch(t::isSubTypeCompatible)) {
            types.removeIf(other -> other.isSubTypeCompatible(t));
            types.add(t);
          }
        }
      }
      List<String> out = new ArrayList<>();
      for (ITypeBinding t : types) {
        out.add(typeName(t));
      }
      return out;
    }

    /** Whether a try among the statements, around {@code n}, catches {@code t}. */
    private boolean caught(ASTNode n, ITypeBinding t) {
      ASTNode child = n;
      for (ASTNode p = n.getParent(); p != null && sel.contains(p); p = p.getParent()) {
        if (p instanceof TryStatement ts && ts.getBody() == child) {
          for (Object o : ts.catchClauses()) {
            Type caught = ((CatchClause) o).getException().getType();
            List<Type> alternatives = new ArrayList<>();
            if (caught instanceof UnionType u) {
              for (Object a : u.types()) {
                alternatives.add((Type) a);
              }
            } else {
              alternatives.add(caught);
            }
            for (Type a : alternatives) {
              ITypeBinding b = a.resolveBinding();
              if (b != null && t.isSubTypeCompatible(b)) {
                return true;
              }
            }
          }
        }
        child = p;
      }
      return false;
    }

    private static boolean checked(ITypeBinding t) {
      for (ITypeBinding s = t; s != null; s = s.getSuperclass()) {
        String q = s.getQualifiedName();
        if (q.equals("java.lang.RuntimeException") || q.equals("java.lang.Error")) {
          return false;
        }
      }
      return true;
    }

    /** The enclosing method's type parameters, as written, or nothing. */
    String typeParameters() {
      MethodDeclaration m = ancestor(sel.list().get(0), MethodDeclaration.class);
      if (m == null || m.typeParameters().isEmpty()) {
        return "";
      }
      List<String> out = new ArrayList<>();
      for (Object o : m.typeParameters()) {
        TypeParameter p = (TypeParameter) o;
        out.add(text.substring(p.getStartPosition(), endOf(p)));
      }
      return "<" + String.join(", ", out) + "> ";
    }

    /** The moved lines, from {@code from} indentation to {@code to}; text blocks as they are. */
    String reindent(String from, String to) {
      List<int[]> blocks = new ArrayList<>();
      for (ASTNode n : allNodes()) {
        if (n instanceof TextBlock b) {
          blocks.add(new int[] {b.getStartPosition(), endOf(b)});
        }
      }
      StringBuilder out = new StringBuilder();
      int at = sel.startOffset();
      while (at < sel.endOffset()) {
        int next = text.indexOf('\n', at);
        int stop = next < 0 || next >= sel.endOffset() ? sel.endOffset() : next + 1;
        String line = text.substring(at, stop);
        int lineAt = at;
        if (blocks.stream().anyMatch(b -> lineAt > b[0] && lineAt < b[1])) {
          out.append(line);
        } else if (line.isBlank()) {
          out.append("\n");
        } else if (line.startsWith(from)) {
          out.append(to).append(line.substring(from.length()));
        } else {
          out.append(line);
        }
        if (!line.endsWith("\n")) {
          out.append("\n");
        }
        at = stop;
      }
      return out.toString();
    }

    private static String javadoc(String doc, String indent) {
      String[] docLines = doc.split("\n");
      if (docLines.length == 1) {
        return indent + "/** " + docLines[0].strip() + " */\n";
      }
      StringBuilder out = new StringBuilder(indent).append("/**\n");
      for (String l : docLines) {
        out.append(indent).append(l.isBlank() ? " *" : " * " + l.strip()).append("\n");
      }
      return out.append(indent).append(" */\n").toString();
    }

    private String indentOf(int lineStart) {
      int i = lineStart;
      while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
        i++;
      }
      return text.substring(lineStart, i);
    }

    private int lineStartOffset(int offset) {
      return text.lastIndexOf('\n', offset - 1) + 1;
    }

    private int line(ASTNode n) {
      return unit.getLineNumber(n.getStartPosition());
    }

    private int line(int offset) {
      return unit.getLineNumber(offset);
    }

    private static List<String> names(List<IVariableBinding> vars) {
      List<String> out = new ArrayList<>();
      for (IVariableBinding v : vars) {
        out.add(v.getName());
      }
      return out;
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

  private static String and(List<String> names) {
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

  private static <T extends ASTNode> T ancestor(ASTNode n, Class<T> kind) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (kind.isInstance(p)) {
        return kind.cast(p);
      }
    }
    return null;
  }

  /** The offset just past {@code node}. */
  private static int endOf(ASTNode node) {
    return node.getStartPosition() + node.getLength();
  }

  /** The offset just past the line ending of 1-based line {@code line}. */
  private static int lineEndOffset(CompilationUnit unit, String text, int line) {
    int at = unit.getPosition(line, 0);
    int nl = text.indexOf('\n', at);
    return nl < 0 ? text.length() : nl + 1;
  }

  private static String span(CompilationUnit unit, ASTNode n) {
    int a = unit.getLineNumber(n.getStartPosition());
    int b = unit.getLineNumber(endOf(n) - 1);
    return a == b ? "line " + a : "lines " + a + "-" + b;
  }
}
