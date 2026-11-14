package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * convert_loop: a loop rewritten as another statement form that runs the same code in the same
 * order. So far: an index for over a list or an array, and a for over an iterator, to an enhanced
 * for; an enhanced for to an iterator for, or to an index for over a list or an array.
 */
final class ConvertLoop {

  private ConvertLoop() {}

  /** An index for: its index, what it walks, and whether that is an array. */
  private record Indexed(ForStatement loop, IVariableBinding index, Expression of, boolean array) {}

  static Outcome run(SourceProject project, String rel, Integer line, String to) throws Exception {
    Precondition.require(
        Precondition.that(
            () -> line != null,
            () -> "convert_loop needs line, the 1-based line the loop starts on."),
        Precondition.that(
            () -> List.of("for_each", "iterator", "for", "while", "do_while").contains(to),
            () ->
                "convert_loop takes to=for_each, iterator, for, while or do_while; to="
                    + to
                    + " is not built yet."));
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    Statement loop =
        opened.startingOn(
            line,
            Statement.class,
            s ->
                s instanceof EnhancedForStatement
                    || s instanceof ForStatement
                    || s instanceof WhileStatement
                    || s instanceof DoStatement,
            "loop",
            "",
            "the loop");
    List<TextEdit> edits = new ArrayList<>();
    List<String> imports = new ArrayList<>();
    String said;
    if (loop instanceof EnhancedForStatement each) {
      said =
          switch (to) {
            case "iterator" -> toIterator(text, each, edits, imports);
            case "for" -> toIndex(text, each, edits);
            default -> throw new Refusal("the loop at line " + line + " is already a for_each.");
          };
    } else if (loop instanceof ForStatement f && to.equals("for_each")) {
      Indexed indexed = indexed(f);
      said =
          indexed != null
              ? indexToEach(text, indexed, edits)
              : iteratorToEach(text, f, line, edits);
    } else if (loop instanceof ForStatement f && to.equals("while")) {
      said = forToWhile(text, f, edits);
    } else if (loop instanceof WhileStatement w && to.equals("for")) {
      said = whileToFor(text, w, edits);
    } else if (loop instanceof WhileStatement w && to.equals("do_while")) {
      said = whileToDo(text, w, edits);
    } else if (loop instanceof DoStatement d && to.equals("while")) {
      said = doToWhile(text, d, edits);
    } else {
      String form =
          loop instanceof ForStatement
              ? "for"
              : loop instanceof WhileStatement ? "while" : "do_while";
      String takes =
          switch (form) {
            case "for" -> "for_each or while";
            case "while" -> "for or do_while";
            default -> "while";
          };
      throw new Refusal(
          form.equals(to)
              ? "the loop at line " + line + " is already a " + to + "."
              : "convert_loop takes a "
                  + form
                  + " to "
                  + takes
                  + ". Convert it in two steps, through "
                  + (form.equals("for")
                      ? "for_each or while"
                      : form.equals("while") ? "for" : "while")
                  + ".");
    }
    String updated = TextEdit.apply(text, edits, 0);
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            updated,
            imports,
            "rewriting the loop at line " + line,
            "Undo, and check the loop's types.");
    return new Outcome(
        "rewrote the loop at line " + line + " of " + rel + " as " + said + written.fixed().said(),
        written.declared());
  }

  /**
   * {@code f} as an index for, {@code for (int i = 0; i < x.size(); i++)} over a list or {@code i <
   * x.length} over an array ({@code ++i} and {@code i += 1} too); else null.
   */
  private static Indexed indexed(ForStatement f) {
    if (f.initializers().size() != 1
        || !(f.initializers().get(0) instanceof VariableDeclarationExpression init)
        || init.fragments().size() != 1
        || !(init.getType() instanceof PrimitiveType p)
        || p.getPrimitiveTypeCode() != PrimitiveType.INT) {
      return null;
    }
    VariableDeclarationFragment fragment = (VariableDeclarationFragment) init.fragments().get(0);
    IVariableBinding index = fragment.resolveBinding();
    if (index == null
        || !(fragment.getInitializer() instanceof NumberLiteral zero)
        || !zero.getToken().equals("0")
        || !(f.getExpression() instanceof InfixExpression cond)
        || cond.getOperator() != InfixExpression.Operator.LESS
        || cond.hasExtendedOperands()
        || !is(cond.getLeftOperand(), index)
        || f.updaters().size() != 1
        || !increments((Expression) f.updaters().get(0), index)) {
      return null;
    }
    Expression bound = cond.getRightOperand();
    if (bound instanceof MethodInvocation size
        && size.getName().getIdentifier().equals("size")
        && size.arguments().isEmpty()
        && size.getExpression() != null
        && Hierarchy.isA(size.getExpression().resolveTypeBinding(), "java.util.List")) {
      return new Indexed(f, index, size.getExpression(), false);
    }
    if (bound instanceof QualifiedName q
        && q.getName().getIdentifier().equals("length")
        && arrayType(q.getQualifier())) {
      return new Indexed(f, index, q.getQualifier(), true);
    }
    if (bound instanceof FieldAccess a
        && a.getName().getIdentifier().equals("length")
        && arrayType(a.getExpression())) {
      return new Indexed(f, index, a.getExpression(), true);
    }
    return null;
  }

  private static boolean arrayType(Expression e) {
    ITypeBinding t = e.resolveTypeBinding();
    return t != null && t.isArray();
  }

  /** Whether {@code e} is {@code i++}, {@code ++i} or {@code i += 1}. */
  private static boolean increments(Expression e, IVariableBinding i) {
    return (e instanceof PostfixExpression post
            && post.getOperator() == PostfixExpression.Operator.INCREMENT
            && is(post.getOperand(), i))
        || (e instanceof PrefixExpression pre
            && pre.getOperator() == PrefixExpression.Operator.INCREMENT
            && is(pre.getOperand(), i))
        || (e instanceof Assignment a
            && a.getOperator() == Assignment.Operator.PLUS_ASSIGN
            && is(a.getLeftHandSide(), i)
            && a.getRightHandSide() instanceof NumberLiteral one
            && one.getToken().equals("1"));
  }

  private static boolean is(Expression e, IBinding v) {
    return e instanceof SimpleName n && same(n.resolveBinding(), v);
  }

  private static boolean same(IBinding a, IBinding b) {
    return a != null && b != null && a.getKey().equals(b.getKey());
  }

  /** An index for to an enhanced for: each {@code x.get(i)} or {@code x[i]} becomes the element. */
  private static String indexToEach(String text, Indexed indexed, List<TextEdit> edits) {
    ForStatement loop = indexed.loop();
    int line = Lines.number(text, loop.getStartPosition());
    String of = source(text, indexed.of());
    Statement body = loop.getBody();
    stable(text, indexed.of(), body, line);
    if (!indexed.array()) {
      unchanged(text, indexed.of(), body, line);
    }
    List<Expression> reads = new ArrayList<>();
    for (SimpleName n : Nodes.all(body, SimpleName.class)) {
      if (!same(n.resolveBinding(), indexed.index())) {
        continue;
      }
      Expression read = elementRead(text, n, indexed.of(), indexed.array());
      if (read == null) {
        throw new Refusal(
            "the loop at line "
                + line
                + " reads "
                + n.getIdentifier()
                + " other than as "
                + (indexed.array() ? of + "[" + n + "]" : of + ".get(" + n + ")")
                + " ("
                + source(text, n.getParent())
                + "), and an enhanced for has no index.");
      }
      reads.add(read);
    }
    ITypeBinding element =
        indexed.array()
            ? indexed.of().resolveTypeBinding().getComponentType()
            : LoopToStream.elementOf(indexed.of().resolveTypeBinding());
    if (element == null) {
      throw new Refusal(of + " has no type argument, so its elements' type is not known.");
    }
    String type = Extract.typeName(element);
    String name = null;
    if (body instanceof Block block
        && !block.statements().isEmpty()
        && block.statements().get(0) instanceof VariableDeclarationStatement first
        && first.fragments().size() == 1) {
      VariableDeclarationFragment f = (VariableDeclarationFragment) first.fragments().get(0);
      if (f.getExtraDimensions() == 0
          && reads.contains(f.getInitializer())
          && !written(body, f.resolveBinding())) {
        name = f.getName().getIdentifier();
        type = text.substring(first.getStartPosition(), f.getStartPosition()).strip();
        reads.remove(f.getInitializer());
        edits.add(
            new TextEdit(
                Lines.start(text, first.getStartPosition()),
                Lines.end(text, Nodes.end(first) - 1),
                ""));
      }
    }
    if (name == null) {
      name = Names.fresh(Names.elementName(indexed.of()), loop);
    }
    for (Expression read : reads) {
      edits.add(new TextEdit(read.getStartPosition(), Nodes.end(read), name));
    }
    edits.add(
        new TextEdit(
            loop.getStartPosition(),
            body.getStartPosition(),
            "for (" + type + " " + name + " : " + of + ") "));
    return "a for (" + type + " " + name + " : " + of + ").";
  }

  /** The {@code x.get(i)} or {@code x[i]} that {@code i} is the index of, or null. */
  private static Expression elementRead(String text, SimpleName i, Expression of, boolean array) {
    ASTNode parent = i.getParent();
    if (array) {
      if (parent instanceof ArrayAccess a
          && a.getIndex() == i
          && LoopToStream.sameVariable(text, a.getArray(), of)
          && !writes(a)) {
        return a;
      }
      return null;
    }
    if (parent instanceof MethodInvocation m
        && m.getName().getIdentifier().equals("get")
        && m.arguments().size() == 1
        && m.arguments().get(0) == i
        && m.getExpression() != null
        && LoopToStream.sameVariable(text, m.getExpression(), of)) {
      return m;
    }
    return null;
  }

  /** Whether {@code a} is assigned or incremented. */
  private static boolean writes(ArrayAccess a) {
    ASTNode p = a.getParent();
    return (p instanceof Assignment s && s.getLeftHandSide() == a)
        || p instanceof PostfixExpression
        || (p instanceof PrefixExpression pre
            && (pre.getOperator() == PrefixExpression.Operator.INCREMENT
                || pre.getOperator() == PrefixExpression.Operator.DECREMENT));
  }

  /**
   * Refuses when {@code of} can be another list or array on a later pass: an index loop reads it on
   * each, an enhanced for once. A local or parameter the body does not assign, a final field, or a
   * field neither the body nor a method of the project it calls assigns.
   */
  private static void stable(String text, Expression of, Statement body, int line) {
    IBinding b =
        of instanceof SimpleName s
            ? s.resolveBinding()
            : of instanceof FieldAccess f && f.getExpression() instanceof ThisExpression
                ? f.resolveFieldBinding()
                : null;
    String hint =
        " Put it in a local before the loop first (extract_variable), so each pass reads the same"
            + " one.";
    if (!(b instanceof IVariableBinding v)) {
      throw new Refusal(
          "the loop at line "
              + line
              + " reads "
              + source(text, of)
              + " on each pass, and an enhanced for once."
              + hint);
    }
    if (written(body, v)) {
      throw new Refusal(source(text, of) + " is assigned in the loop at line " + line + "." + hint);
    }
    if (v.isField() && !Modifier.isFinal(v.getModifiers())) {
      for (MethodInvocation m : Nodes.all(body, MethodInvocation.class)) {
        IMethodBinding called = m.resolveMethodBinding();
        if (called == null || called.getDeclaringClass().isFromSource()) {
          throw new Refusal(
              "the loop at line "
                  + line
                  + " calls "
                  + m.getName()
                  + ", which may assign the field "
                  + v.getName()
                  + "."
                  + hint);
        }
      }
    }
  }

  /**
   * Refuses a body that adds to or removes from the list {@code of}, which an iterator fails on.
   */
  private static void unchanged(String text, Expression of, Statement body, int line) {
    for (MethodInvocation m : Nodes.all(body, MethodInvocation.class)) {
      if (m.getExpression() != null
          && LoopToStream.CHANGES.contains(m.getName().getIdentifier())
          && LoopToStream.sameVariable(text, m.getExpression(), of)) {
        throw new Refusal(
            "the loop at line "
                + line
                + " changes "
                + source(text, m.getExpression())
                + " ("
                + source(text, m)
                + "), which an enhanced for over it would fail on.");
      }
    }
  }

  private static boolean written(Statement body, IBinding v) {
    for (Effects.Effect e : Effects.of(body).all()) {
      if (e.kind() == Effects.Kind.WRITE && same(e.variable(), v)) {
        return true;
      }
    }
    return false;
  }

  /**
   * A for over an iterator, {@code for (Iterator<T> it = x.iterator(); it.hasNext(); ) { T e =
   * it.next(); ... }}, to {@code for (T e : x)}.
   */
  private static String iteratorToEach(
      String text, ForStatement loop, int line, List<TextEdit> edits) {
    String shape =
        " is not an index for (for (int i = 0; i < x.size(); i++)) or a for over an iterator (for"
            + " (Iterator<T> it = x.iterator(); it.hasNext(); ) { T e = it.next(); ... }).";
    if (loop.initializers().size() != 1
        || !(loop.initializers().get(0) instanceof VariableDeclarationExpression init)
        || init.fragments().size() != 1
        || !Hierarchy.isA(init.getType().resolveBinding(), "java.util.Iterator")
        || !loop.updaters().isEmpty()) {
      throw new Refusal("the loop at line " + line + shape);
    }
    VariableDeclarationFragment it = (VariableDeclarationFragment) init.fragments().get(0);
    IVariableBinding iterator = it.resolveBinding();
    if (!(it.getInitializer() instanceof MethodInvocation start)
        || !start.getName().getIdentifier().equals("iterator")
        || !start.arguments().isEmpty()
        || start.getExpression() == null
        || !(loop.getExpression() instanceof MethodInvocation hasNext)
        || !hasNext.getName().getIdentifier().equals("hasNext")
        || !is(hasNext.getExpression(), iterator)
        || !(loop.getBody() instanceof Block body)
        || body.statements().isEmpty()
        || !(body.statements().get(0) instanceof VariableDeclarationStatement first)
        || first.fragments().size() != 1
        || !(((VariableDeclarationFragment) first.fragments().get(0)).getInitializer()
            instanceof MethodInvocation next)
        || !next.getName().getIdentifier().equals("next")
        || !is(next.getExpression(), iterator)) {
      throw new Refusal("the loop at line " + line + shape);
    }
    for (SimpleName n : Nodes.all(body, SimpleName.class)) {
      if (same(n.resolveBinding(), iterator) && n != next.getExpression()) {
        throw new Refusal(
            "the loop at line "
                + line
                + " uses "
                + n
                + " other than in its first next() ("
                + source(text, n.getParent())
                + "), and an enhanced for has no iterator to call.");
      }
    }
    VariableDeclarationFragment element = (VariableDeclarationFragment) first.fragments().get(0);
    String type = text.substring(first.getStartPosition(), element.getStartPosition()).strip();
    String of = source(text, start.getExpression());
    String header = "for (" + type + " " + element.getName() + " : " + of + ")";
    edits.add(
        new TextEdit(
            Lines.start(text, first.getStartPosition()),
            Lines.end(text, Nodes.end(first) - 1),
            ""));
    edits.add(new TextEdit(loop.getStartPosition(), body.getStartPosition(), header + " "));
    return "a " + header + ".";
  }

  /** An enhanced for to {@code for (Iterator<T> it = x.iterator(); it.hasNext(); )}. */
  private static String toIterator(
      String text, EnhancedForStatement loop, List<TextEdit> edits, List<String> imports) {
    int line = Lines.number(text, loop.getStartPosition());
    ITypeBinding sourceType = loop.getExpression().resolveTypeBinding();
    if (sourceType == null || sourceType.isArray()) {
      throw new Refusal(
          "the loop at line "
              + line
              + " walks "
              + (sourceType == null ? "a type that does not resolve" : "an array")
              + ", which has no iterator.");
    }
    ITypeBinding element = LoopToStream.elementOf(sourceType);
    if (element == null) {
      throw new Refusal(
          source(text, loop.getExpression())
              + " is not an Iterable with a type argument, so the iterator's type is not known.");
    }
    String it = Names.fresh("it", loop);
    String header =
        "for (Iterator<"
            + Extract.typeName(element)
            + "> "
            + it
            + " = "
            + LoopToStream.primary(text, loop.getExpression())
            + ".iterator(); "
            + it
            + ".hasNext(); )";
    imports.add("java.util.Iterator");
    first(
        text,
        loop,
        loop.getBody(),
        source(text, loop.getParameter()) + " = " + it + ".next();",
        edits);
    edits.add(
        new TextEdit(loop.getStartPosition(), loop.getBody().getStartPosition(), header + " "));
    return "a " + header + ".";
  }

  /** An enhanced for over a list or an array to {@code for (int i = 0; i < x.size(); i++)}. */
  private static String toIndex(String text, EnhancedForStatement loop, List<TextEdit> edits) {
    int line = Lines.number(text, loop.getStartPosition());
    Expression of = loop.getExpression();
    ITypeBinding t = of.resolveTypeBinding();
    boolean array = t != null && t.isArray();
    if (!array && !Hierarchy.isA(t, "java.util.List")) {
      throw new Refusal(
          "the loop at line "
              + line
              + " walks "
              + source(text, of)
              + ", which is not a List or an array, so it has no index; to=iterator takes it.");
    }
    Statement body = loop.getBody();
    stable(text, of, body, line);
    String from = source(text, of);
    if (!array) {
      unchanged(text, of, body, line);
    }
    String i = Names.fresh("i", loop);
    String primary = LoopToStream.primary(text, of);
    String header =
        "for (int "
            + i
            + " = 0; "
            + i
            + " < "
            + primary
            + (array ? ".length" : ".size()")
            + "; "
            + i
            + "++)";
    String read = array ? primary + "[" + i + "]" : primary + ".get(" + i + ")";
    first(text, loop, body, source(text, loop.getParameter()) + " = " + read + ";", edits);
    edits.add(new TextEdit(loop.getStartPosition(), body.getStartPosition(), header + " "));
    String note =
        !array && !Hierarchy.isA(t, "java.util.RandomAccess")
            ? " " + from + " is not a RandomAccess list, so get(i) may walk it (a LinkedList)."
            : "";
    return "a " + header + "." + note;
  }

  /**
   * Edits that put {@code statement} first in {@code body}: on a line of its own after the brace of
   * a block, or with a one-statement body in a new block.
   */
  private static void first(
      String text, Statement loop, Statement body, String statement, List<TextEdit> edits) {
    String indent = Lines.indentOf(text, loop.getStartPosition());
    int step = Declarations.step(text) > 0 ? Declarations.step(text) : 4;
    String inner = indent + " ".repeat(step);
    if (body instanceof Block block
        && Lines.number(text, block.getStartPosition()) != Lines.number(text, Nodes.end(block) - 1)
        && (block.statements().isEmpty()
            || Lines.number(text, ((Statement) block.statements().get(0)).getStartPosition())
                != Lines.number(text, block.getStartPosition()))) {
      String at =
          block.statements().isEmpty()
              ? inner
              : Lines.indentOf(text, ((Statement) block.statements().get(0)).getStartPosition());
      int after = Lines.end(text, block.getStartPosition());
      edits.add(new TextEdit(after, after, at + statement + "\n"));
      return;
    }
    List<String> lines = new ArrayList<>();
    for (Object s : body instanceof Block block ? block.statements() : List.of(body)) {
      lines.add(LoopToStream.reindent(source(text, (ASTNode) s), inner));
    }
    String rest = String.join("\n", lines);
    edits.add(
        new TextEdit(
            body.getStartPosition(),
            Nodes.end(body),
            "{\n" + inner + statement + (rest.isEmpty() ? "" : "\n" + rest) + "\n" + indent + "}"));
  }

  private static String source(String text, ASTNode node) {
    return text.substring(node.getStartPosition(), Nodes.end(node));
  }

  /**
   * A for to a while: {@code for (init; cond; update) body} becomes {@code init; while (cond) {
   * body; update; }}, the update also written before each {@code continue} of the loop (roadmap
   * #133). The two go in a block when a statement after the loop names a variable the init
   * declares, or the loop is not a statement of a block.
   */
  private static String forToWhile(String text, ForStatement loop, List<TextEdit> edits) {
    Statement whole = loop.getParent() instanceof LabeledStatement l ? l : loop;
    String indent = Lines.indentOf(text, whole.getStartPosition());
    int step = step(text);
    List<String> updates = new ArrayList<>();
    for (Object u : loop.updaters()) {
      updates.add(source(text, (ASTNode) u) + ";");
    }
    List<String> inits = new ArrayList<>();
    List<String> declared = new ArrayList<>();
    for (Object i : loop.initializers()) {
      inits.add(source(text, (ASTNode) i) + ";");
      if (i instanceof VariableDeclarationExpression d) {
        for (Object f : d.fragments()) {
          declared.add(((VariableDeclarationFragment) f).getName().getIdentifier());
        }
      }
    }
    boolean wrap =
        !(whole.getParent() instanceof Block)
            || (!declared.isEmpty() && namedAfter(whole, declared));
    Statement body = loop.getBody();
    if (wrap && !Nodes.all(body, TextBlock.class).isEmpty()) {
      throw new Refusal(
          "the loop at line "
              + Lines.number(text, loop.getStartPosition())
              + " must go in a block, and it holds a text block, which indenting would change.");
    }
    List<TextEdit> inBody = new ArrayList<>();
    if (!updates.isEmpty()) {
      for (ContinueStatement c : continues(body, loop)) {
        before(text, c, updates, step, inBody);
      }
    }
    String cond = loop.getExpression() == null ? "true" : source(text, loop.getExpression());
    String bodyText =
        ends(body)
            ? TextEdit.apply(source(text, body), inBody, body.getStartPosition())
            : last(text, whole, body, updates, step, inBody);
    String label =
        whole instanceof LabeledStatement l
            ? text.substring(l.getStartPosition(), loop.getStartPosition())
            : "";
    String written = label + "while (" + cond + ") " + bodyText;
    for (int k = inits.size() - 1; k >= 0; k--) {
      written = inits.get(k) + "\n" + indent + written;
    }
    if (wrap) {
      written = braced(step, indent, written);
    }
    edits.add(new TextEdit(whole.getStartPosition(), Nodes.end(whole), written));
    return "a while (" + cond + ")" + (wrap ? ", in a block." : ".");
  }

  private static String braced(int step, String indent, String written) {
    String pad = " ".repeat(step);
    StringBuilder b = new StringBuilder("{\n").append(indent).append(pad);
    String[] lines = written.split("\n", -1);
    b.append(lines[0]);
    for (int k = 1; k < lines.length; k++) {
      b.append('\n').append(lines[k].isBlank() ? "" : pad + lines[k]);
    }
    written = b.append('\n').append(indent).append('}').toString();
    return written;
  }

  /**
   * A while to a for: the local declared just before it, which the condition reads and nothing
   * after the loop names, becomes the init; the last statement of the body, when it is an
   * assignment or an increment and no {@code continue} of the loop skips it, the update.
   */
  private static String whileToFor(String text, WhileStatement loop, List<TextEdit> edits) {
    Statement whole = loop.getParent() instanceof LabeledStatement l ? l : loop;
    String cond = source(text, loop.getExpression());
    String init = "";
    if (whole.getParent() instanceof Block block) {
      int at = block.statements().indexOf(whole);
      if (at > 0
          && block.statements().get(at - 1) instanceof VariableDeclarationStatement before
          && before.fragments().size() == 1) {
        VariableDeclarationFragment f = (VariableDeclarationFragment) before.fragments().get(0);
        String name = f.getName().getIdentifier();
        boolean read = false;
        for (SimpleName n : Nodes.all(loop.getExpression(), SimpleName.class)) {
          read |= same(n.resolveBinding(), f.resolveBinding());
        }
        if (read && f.getInitializer() != null && !namedAfter(whole, List.of(name))) {
          String s = source(text, before);
          init = s.substring(0, s.length() - 1).strip();
          edits.add(
              new TextEdit(
                  Lines.start(text, before.getStartPosition()),
                  Lines.start(text, whole.getStartPosition()),
                  ""));
        }
      }
    }
    String update = "";
    String kept = "";
    if (loop.getBody() instanceof Block body && !body.statements().isEmpty()) {
      Statement lastOne = (Statement) body.statements().get(body.statements().size() - 1);
      if (lastOne instanceof ExpressionStatement e
          && (e.getExpression() instanceof Assignment
              || e.getExpression() instanceof PostfixExpression
              || (e.getExpression() instanceof PrefixExpression p
                  && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                      || p.getOperator() == PrefixExpression.Operator.DECREMENT)))) {
        if (continues(body, loop).isEmpty()) {
          update = source(text, e.getExpression());
          edits.add(
              new TextEdit(
                  Lines.start(text, e.getStartPosition()), Lines.end(text, Nodes.end(e) - 1), ""));
        } else {
          kept =
              " The last statement stays in the body, since a continue in the loop skips it and"
                  + " would run the update.";
        }
      }
    }
    String header =
        "for (" + init + "; " + cond + ";" + (update.isEmpty() ? " " : " " + update) + ")";
    edits.add(
        new TextEdit(loop.getStartPosition(), loop.getBody().getStartPosition(), header + " "));
    return "a " + header + "." + kept;
  }

  /** Whether a statement after {@code s} in its block names one of {@code names}. */
  private static boolean namedAfter(Statement s, List<String> names) {
    if (!(s.getParent() instanceof Block block)) {
      return false;
    }
    List<?> all = block.statements();
    for (Object later : all.subList(all.indexOf(s) + 1, all.size())) {
      for (SimpleName n : Nodes.all((ASTNode) later, SimpleName.class)) {
        if (names.contains(n.getIdentifier())) {
          return true;
        }
      }
    }
    return false;
  }

  /** The continue statements in {@code body} that go to the next pass of {@code loop}. */
  private static List<ContinueStatement> continues(Statement body, Statement loop) {
    String label =
        loop.getParent() instanceof LabeledStatement l ? l.getLabel().getIdentifier() : null;
    List<ContinueStatement> out = new ArrayList<>();
    for (ContinueStatement c : Nodes.all(body, ContinueStatement.class)) {
      if (c.getLabel() != null
          ? c.getLabel().getIdentifier().equals(label)
          : innermostLoop(c) == loop) {
        out.add(c);
      }
    }
    return out;
  }

  /** The loop an unlabeled continue in {@code n} goes to, or null past a lambda or a class. */
  private static ASTNode innermostLoop(ASTNode n) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (p instanceof ForStatement
          || p instanceof EnhancedForStatement
          || p instanceof WhileStatement
          || p instanceof DoStatement) {
        return p;
      }
      if (p instanceof LambdaExpression || p instanceof AbstractTypeDeclaration) {
        return null;
      }
    }
    return null;
  }

  /** Edits that run {@code statements} just before {@code c}, in braces when it has none. */
  private static void before(
      String text, ContinueStatement c, List<String> statements, int step, List<TextEdit> edits) {
    if (c.getParent() instanceof Block) {
      String indent = Lines.indentOf(text, c.getStartPosition());
      StringBuilder b = new StringBuilder();
      for (String s : statements) {
        b.append(s).append('\n').append(indent);
      }
      edits.add(new TextEdit(c.getStartPosition(), c.getStartPosition(), b.toString()));
      return;
    }
    String outer = Lines.indentOf(text, c.getParent().getStartPosition());
    String inner = outer + " ".repeat(step);
    StringBuilder b = new StringBuilder("{\n");
    for (String s : statements) {
      b.append(inner).append(s).append('\n');
    }
    b.append(inner).append(source(text, c)).append('\n').append(outer).append('}');
    edits.add(new TextEdit(c.getStartPosition(), Nodes.end(c), b.toString()));
  }

  /**
   * Whether the last statement of {@code body} cannot be followed: a return, throw, break or
   * continue.
   */
  private static boolean ends(Statement body) {
    Statement last = body;
    if (body instanceof Block block) {
      if (block.statements().isEmpty()) {
        return false;
      }
      last = (Statement) block.statements().get(block.statements().size() - 1);
    }
    return last instanceof ReturnStatement
        || last instanceof ThrowStatement
        || last instanceof BreakStatement
        || last instanceof ContinueStatement;
  }

  /**
   * {@code body}'s source with {@code edits} applied and {@code statements} last: before the
   * closing brace of a block that has it on a line of its own, else in a new block.
   */
  private static String last(
      String text,
      Statement whole,
      Statement body,
      List<String> statements,
      int step,
      List<TextEdit> edits) {
    String indent = Lines.indentOf(text, whole.getStartPosition());
    String inner = indent + " ".repeat(step);
    String applied = TextEdit.apply(source(text, body), edits, body.getStartPosition());
    StringBuilder add = new StringBuilder();
    if (body instanceof Block block
        && !block.statements().isEmpty()
        && Lines.number(text, Nodes.end(block) - 1)
            != Lines.number(
                text, Nodes.end((ASTNode) block.statements().get(block.statements().size() - 1)))) {
      String at =
          Lines.indentOf(
              text,
              ((Statement) block.statements().get(block.statements().size() - 1))
                  .getStartPosition());
      for (String s : statements) {
        add.append(at).append(s).append('\n');
      }
      int close = applied.lastIndexOf('}');
      int lineStart = applied.lastIndexOf('\n', close) + 1;
      return applied.substring(0, lineStart) + add + applied.substring(lineStart);
    }
    String rest =
        body instanceof Block
            ? applied.substring(1, applied.length() - 1).strip()
            : applied.strip();
    add.append("{\n");
    if (!rest.isEmpty()) {
      add.append(LoopToStream.reindent(rest, inner)).append('\n');
    }
    for (String s : statements) {
      add.append(inner).append(s).append('\n');
    }
    return add.append(indent).append('}').toString();
  }

  private static int step(String text) {
    return Declarations.step(text) > 0 ? Declarations.step(text) : 4;
  }

  /**
   * A do-while to {@code while (true) { body; if (!cond) break; }} (roadmap #134, the user's choice
   * of 2026-11-11 over copying the body before a while). A {@code continue} goes to a do-while's
   * condition and would skip it in the while, so it is refused.
   */
  private static String doToWhile(String text, DoStatement loop, List<TextEdit> edits) {
    Statement whole = loop.getParent() instanceof LabeledStatement l ? l : loop;
    int line = Lines.number(text, loop.getStartPosition());
    Statement body = loop.getBody();
    List<ContinueStatement> skips = continues(body, loop);
    if (!skips.isEmpty()) {
      throw new Refusal(
          "the do-while at line "
              + line
              + " has a continue (line "
              + Lines.number(text, skips.get(0).getStartPosition())
              + "), which goes to its condition; in a while (true) it would skip the condition."
              + " Rewrite the body without it first (invert_if, remove_else).");
    }
    String negated;
    try {
      negated = InvertIf.negate(text, loop.getExpression(), line);
    } catch (Refusal nan) {
      negated = "!(" + source(text, loop.getExpression()) + ")";
    }
    String exit = "if (" + negated + ") break;";
    String bodyText =
        ends(body)
            ? source(text, body)
            : last(text, whole, body, List.of(exit), step(text), List.of());
    edits.add(new TextEdit(loop.getStartPosition(), Nodes.end(loop), "while (true) " + bodyText));
    return "a while (true) that ends in " + exit;
  }

  /**
   * A while to {@code if (cond) { do body while (cond); }}: the condition is read twice on the
   * first pass, so it may only read locals, with literals and operators.
   */
  private static String whileToDo(String text, WhileStatement loop, List<TextEdit> edits) {
    Statement whole = loop.getParent() instanceof LabeledStatement l ? l : loop;
    int line = Lines.number(text, loop.getStartPosition());
    String cond = source(text, loop.getExpression());
    ASTNode other = notALocalRead(loop.getExpression());
    if (other != null) {
      throw new Refusal(
          "the condition of the while at line "
              + line
              + " would be read twice on the first pass, by an if and by the do-while, and "
              + source(text, other)
              + " does more than read a local. Put the condition in a local first.");
    }
    if (!Nodes.all(loop.getBody(), TextBlock.class).isEmpty()) {
      throw new Refusal(
          "the loop at line "
              + line
              + " must go in an if, and it holds a text block, which indenting would change.");
    }
    String indent = Lines.indentOf(text, whole.getStartPosition());
    String label =
        whole instanceof LabeledStatement l
            ? text.substring(l.getStartPosition(), loop.getStartPosition())
            : "";
    String bodyText = last(text, whole, loop.getBody(), List.of(), step(text), List.of());
    String written =
        "if ("
            + cond
            + ") "
            + braced(step(text), indent, label + "do " + bodyText + " while (" + cond + ");");
    edits.add(new TextEdit(whole.getStartPosition(), Nodes.end(whole), written));
    return "a do-while under if (" + cond + ").";
  }

  /**
   * The first part of {@code e} that is not a read of a local, a literal or an operator on them, or
   * null.
   */
  private static ASTNode notALocalRead(Expression e) {
    for (ASTNode n : Nodes.all(e, ASTNode.class)) {
      boolean read =
          n instanceof SimpleName s
              ? s.resolveBinding() instanceof IVariableBinding v && !v.isField()
              : n instanceof NumberLiteral
                  || n instanceof BooleanLiteral
                  || n instanceof CharacterLiteral
                  || n instanceof StringLiteral
                  || n instanceof NullLiteral
                  || n instanceof InfixExpression
                  || n instanceof ParenthesizedExpression
                  || (n instanceof PrefixExpression p
                      && p.getOperator() != PrefixExpression.Operator.INCREMENT
                      && p.getOperator() != PrefixExpression.Operator.DECREMENT);
      if (!read) {
        return n;
      }
    }
    return null;
  }
}
