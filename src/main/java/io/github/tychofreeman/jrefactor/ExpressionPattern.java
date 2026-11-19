package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

/**
 * replace_expression with $name holes, or everywhere (gorefactor's #676): {@code expression} is a
 * pattern whose holes stand for any expression. The match at {@code line} anchors it: it fixes what
 * each other name means (by binding key, or for a local or parameter its type) and each hole's
 * type. That match, or with {@code everywhere} every expression of the project that matches the
 * same way, becomes {@code new}, each hole's source in its place.
 */
final class ExpressionPattern {

  private ExpressionPattern() {}

  private static final Pattern HOLE = Pattern.compile("\\$[A-Za-z_][A-Za-z0-9_]*");

  /** Whether {@code expression} has a $name hole. */
  static boolean hasHoles(String expression) {
    return HOLE.matcher(expression).find();
  }

  /** An expression that matched, and the expression each hole stands for there. */
  private record Match(Expression node, Map<String, Expression> holes) {}

  /** What the anchor fixed: each hole's type, and each other name's meaning by its place. */
  private record Anchor(Map<String, String> holeTypes, Map<Integer, String> meanings) {}

  static Outcome run(SourceProject project, Args args, Opened opened) throws Exception {
    String rel = args.required("file");
    int line = args.integer("line");
    String expression = args.required("expression").strip();
    if (args.has("build")) {
      throw new Refusal(
          "build writes one expression from names; with everywhere or a $name hole, give new.");
    }
    String replacement = args.required("new");
    Expression pattern = parse(expression, "expression");
    Expression shape = parse(replacement, "new");
    List<String> holes = holes(expression);
    checkNew(replacement, holes);

    List<Match> found = new ArrayList<>();
    for (Expression x : opened.onLine(line, Expression.class)) {
      Holes h = new Holes(null);
      if (pattern.getClass() == x.getClass() && pattern.subtreeMatch(h, x)) {
        found.add(new Match(x, h.holes));
      }
    }
    found.removeIf(a -> found.stream().anyMatch(b -> b != a && Nodes.within(a.node(), b.node())));
    if (found.isEmpty()) {
      throw new Refusal(
          "no expression on line "
              + line
              + " of "
              + rel
              + " matches "
              + expression
              + ". Give the line where one starts, with its names as written there.");
    }
    List<Expression> nodes =
        opened.atColumn(
            found.stream().map(Match::node).toList(), line, args.integer("column"), expression);
    if (nodes.size() > 1) {
      throw opened.several(nodes, line, "matches of " + expression);
    }
    Match anchor = found.stream().filter(m -> m.node() == nodes.get(0)).findFirst().orElseThrow();

    Map<Path, String> texts = new LinkedHashMap<>();
    Map<Path, List<Match>> matches = new LinkedHashMap<>();
    if (args.flag("everywhere")) {
      Anchor fixed = fix(pattern, anchor.node());
      String name = firstName(pattern);
      Holding holding =
          name == null
              ? Holding.where(project, (f, t) -> true)
              : Holding.of(project, name, opened.file());
      for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
        List<Match> inFile = everywhere(pattern, fixed, e.getValue());
        if (!inFile.isEmpty()) {
          texts.put(e.getKey(), holding.texts().get(e.getKey()));
          matches.put(e.getKey(), inFile);
        }
      }
    } else {
      texts.put(opened.file(), opened.text());
      matches.put(opened.file(), List.of(anchor));
    }

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    List<String> where = new ArrayList<>();
    int count = 0;
    for (Map.Entry<Path, List<Match>> e : matches.entrySet()) {
      String text = texts.get(e.getKey());
      CompilationUnit unit = (CompilationUnit) e.getValue().get(0).node().getRoot();
      List<TextEdit> fileEdits = new ArrayList<>();
      List<String> lines = new ArrayList<>();
      for (Match m : e.getValue()) {
        fileEdits.add(
            new TextEdit(
                m.node().getStartPosition(),
                Nodes.end(m.node()),
                written(replacement, shape, m, text)));
        lines.add(String.valueOf(unit.getLineNumber(m.node().getStartPosition())));
      }
      edits.put(e.getKey(), fileEdits);
      count += fileEdits.size();
      where.add(
          project.relative(e.getKey())
              + (lines.size() == 1 ? " line " : " lines ")
              + String.join(", ", lines));
    }
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            project,
            texts,
            edits,
            args.list("imports"),
            "replacing " + expression,
            "Replace one match at a time (without everywhere), to see which one breaks.");
    return new Outcome(
        "replaced "
            + Names.plural(count, "occurrence")
            + " of "
            + expression
            + ": "
            + String.join("; ", where)
            + written.said(),
        written.declared());
  }

  /** {@code text} as one expression, or a refusal naming the argument. */
  private static Expression parse(String text, String arg) {
    Expression e = Jdt.parseExpression(text);
    if (e == null) {
      throw new Refusal(
          arg
              + " "
              + text
              + " is not one Java expression. Send an expression, its holes as $name.");
    }
    return e;
  }

  /** The holes of {@code text}, in the order of their first use. */
  private static List<String> holes(String text) {
    List<String> holes = new ArrayList<>();
    Matcher m = HOLE.matcher(text);
    while (m.find()) {
      if (!holes.contains(m.group())) {
        holes.add(m.group());
      }
    }
    return holes;
  }

  /**
   * Refuses {@code replacement} unless it uses each hole once, in the order the pattern does, and
   * no other: what a hole stands for then runs as often, and in the order, it did.
   */
  private static void checkNew(String replacement, List<String> holes) {
    List<String> used = new ArrayList<>();
    Matcher m = HOLE.matcher(replacement);
    while (m.find()) {
      used.add(m.group());
    }
    for (String h : used) {
      if (!holes.contains(h)) {
        throw new Refusal("new uses " + h + ", which expression has no hole for.");
      }
    }
    for (String h : holes) {
      long times = used.stream().filter(h::equals).count();
      if (times != 1) {
        throw new Refusal(
            "new uses "
                + h
                + " "
                + times
                + " times: each hole goes in new once, so what it stands for runs as often as"
                + " before. Name it first (extract_variable) to use it twice.");
      }
    }
    if (!used.equals(holes)) {
      throw new Refusal(
          "new uses the holes in the order "
              + String.join(", ", used)
              + ", and expression in the order "
              + String.join(", ", holes)
              + ", so what they stand for would run in another order.");
    }
  }

  /** The first name of {@code pattern} that is not a hole, or null. */
  private static String firstName(Expression pattern) {
    for (SimpleName n : Nodes.all(pattern, SimpleName.class)) {
      if (!HOLE.matcher(n.getIdentifier()).matches()) {
        return n.getIdentifier();
      }
    }
    return null;
  }

  /** What matching {@code pattern} at {@code anchor} fixes, or a refusal for a hole of no type. */
  private static Anchor fix(Expression pattern, Expression anchor) {
    Holes h = new Holes(null);
    pattern.subtreeMatch(h, anchor);
    Map<String, String> types = new HashMap<>();
    for (Map.Entry<String, Expression> e : h.holes.entrySet()) {
      String type = typeKey(e.getValue().resolveTypeBinding());
      if (type == null) {
        throw new Refusal(
            e.getKey()
                + " stands for "
                + Nodes.show(e.getValue())
                + " there, which has no type; everywhere needs one to match others by.");
      }
      types.put(e.getKey(), type);
    }
    Map<Integer, String> meanings = new HashMap<>();
    for (Map.Entry<Integer, SimpleName> e : h.names.entrySet()) {
      meanings.put(e.getKey(), meaning(e.getValue()));
    }
    return new Anchor(types, meanings);
  }

  /** The matches of {@code pattern} in {@code unit}, outermost only, in source order. */
  private static List<Match> everywhere(Expression pattern, Anchor fixed, CompilationUnit unit) {
    List<Match> found = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean preVisit2(ASTNode n) {
            if (n.getClass() == pattern.getClass()) {
              Holes h = new Holes(fixed);
              if (pattern.subtreeMatch(h, n)) {
                found.add(new Match((Expression) n, h.holes));
                return false;
              }
            }
            return true;
          }
        });
    return found;
  }

  /** The type a hole is matched by: its erasure's qualified name, or null. */
  private static String typeKey(ITypeBinding t) {
    return t == null || t.isNullType() ? null : t.getErasure().getQualifiedName();
  }

  /** What a name means: a local or parameter by its type, anything else by its binding key. */
  private static String meaning(SimpleName n) {
    IBinding b = n.resolveBinding();
    if (b == null) {
      return "unresolved " + n.getIdentifier();
    }
    if (b instanceof IVariableBinding v && !v.isField()) {
      return "local " + typeKey(v.getType());
    }
    return Bindings.declarationKey(b);
  }

  /**
   * Matches by shape: a hole matches any expression (the same text each time it is used), and with
   * an anchor, one of the type the anchor gave it; another name matches the same identifier, and
   * with an anchor, of the same meaning.
   */
  private static final class Holes extends ASTMatcher {
    final Anchor anchor;
    final Map<String, Expression> holes = new LinkedHashMap<>();
    final Map<Integer, SimpleName> names = new HashMap<>();

    Holes(Anchor anchor) {
      super(false);
      this.anchor = anchor;
    }

    @Override
    public boolean match(SimpleName p, Object other) {
      String id = p.getIdentifier();
      if (HOLE.matcher(id).matches()) {
        if (!(other instanceof Expression x)) {
          return false;
        }
        Expression was = holes.get(id);
        if (was != null) {
          return was.subtreeMatch(new ASTMatcher(), x);
        }
        if (anchor != null && !anchor.holeTypes().get(id).equals(typeKey(x.resolveTypeBinding()))) {
          return false;
        }
        holes.put(id, x);
        return true;
      }
      if (!(other instanceof SimpleName o) || !id.equals(o.getIdentifier())) {
        return false;
      }
      if (anchor != null && !anchor.meanings().get(p.getStartPosition()).equals(meaning(o))) {
        return false;
      }
      names.put(p.getStartPosition(), o);
      return true;
    }
  }

  /**
   * {@code replacement} for {@code m}: each hole's source in its place, unwrapped where nothing
   * around it needs parentheses and parenthesized where it is not primary and something does; the
   * whole parenthesized likewise for where {@code m} stands.
   */
  private static String written(String replacement, Expression shape, Match m, String text) {
    List<TextEdit> edits = new ArrayList<>();
    for (SimpleName n : Nodes.all(shape, SimpleName.class)) {
      Expression x = m.holes().get(n.getIdentifier());
      if (x == null || !HOLE.matcher(n.getIdentifier()).matches()) {
        continue;
      }
      if (bare(n)) {
        while (x instanceof ParenthesizedExpression p) {
          x = p.getExpression();
        }
      }
      String source = Nodes.source(text, x);
      edits.add(
          new TextEdit(
              n.getStartPosition(),
              Nodes.end(n),
              bare(n) || primary(x) ? source : "(" + source + ")"));
    }
    String out = TextEdit.apply(replacement, edits, 0);
    return bare(m.node()) || primary(shape) ? out : "(" + out + ")";
  }

  /** Whether {@code x} is a primary expression, which needs no parentheses anywhere. */
  private static boolean primary(Expression x) {
    return x instanceof Name
        || x instanceof NumberLiteral
        || x instanceof StringLiteral
        || x instanceof CharacterLiteral
        || x instanceof BooleanLiteral
        || x instanceof NullLiteral
        || x instanceof TextBlock
        || x instanceof TypeLiteral
        || x instanceof ThisExpression
        || x instanceof MethodInvocation
        || x instanceof SuperMethodInvocation
        || x instanceof FieldAccess
        || x instanceof SuperFieldAccess
        || x instanceof ArrayAccess
        || x instanceof ParenthesizedExpression
        || x instanceof ClassInstanceCreation;
  }

  /** Whether {@code n} stands where any expression may without parentheses. */
  private static boolean bare(ASTNode n) {
    if (n.getParent() == null || n.getParent() instanceof ParenthesizedExpression) {
      return true;
    }
    StructuralPropertyDescriptor at = n.getLocationInParent();
    return at == MethodInvocation.ARGUMENTS_PROPERTY
        || at == ClassInstanceCreation.ARGUMENTS_PROPERTY
        || at == SuperMethodInvocation.ARGUMENTS_PROPERTY
        || at == ConstructorInvocation.ARGUMENTS_PROPERTY
        || at == SuperConstructorInvocation.ARGUMENTS_PROPERTY
        || at == VariableDeclarationFragment.INITIALIZER_PROPERTY
        || at == Assignment.RIGHT_HAND_SIDE_PROPERTY
        || at == ReturnStatement.EXPRESSION_PROPERTY
        || at == ExpressionStatement.EXPRESSION_PROPERTY
        || at == ArrayInitializer.EXPRESSIONS_PROPERTY
        || at == ArrayAccess.INDEX_PROPERTY
        || at == IfStatement.EXPRESSION_PROPERTY
        || at == WhileStatement.EXPRESSION_PROPERTY
        || at == DoStatement.EXPRESSION_PROPERTY
        || at == ThrowStatement.EXPRESSION_PROPERTY
        || at == YieldStatement.EXPRESSION_PROPERTY
        || at == EnhancedForStatement.EXPRESSION_PROPERTY
        || at == SwitchStatement.EXPRESSION_PROPERTY
        || at == SwitchExpression.EXPRESSION_PROPERTY
        || at == LambdaExpression.BODY_PROPERTY;
  }
}
