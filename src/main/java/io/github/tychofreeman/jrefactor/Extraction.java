package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

/** The new method worked out from a selection, and the edit that makes it. */
final class Extraction {
  final SourceProject project;
  final Path path;
  final String rel;
  final String text;
  final CompilationUnit unit;
  final Extract.Selection sel;
  final String name;
  final String doc;
  final BodyDeclaration member;
  final String visibility;

  /** The method of a supertype the new one overrides, once {@link #checkOverride} finds it. */
  private IMethodBinding overridden;

  Extraction(
      SourceProject project,
      Path path,
      String rel,
      String text,
      CompilationUnit unit,
      Extract.Selection sel,
      String name,
      String doc,
      String visibility) {
    this.project = project;
    this.path = path;
    this.rel = rel;
    this.text = text;
    this.unit = unit;
    this.sel = sel;
    this.name = name;
    this.doc = doc;
    this.visibility = visibility == null ? "private" : visibility;
    this.member = Nodes.ancestor(sel.list().get(0), BodyDeclaration.class);
  }

  Outcome apply() throws Exception {
    Plan p = plan();
    String invocation = name + "(" + String.join(", ", names(p.params())) + ")";
    Call c = callOf(p, invocation, recordName());
    String method = methodText(p, c);

    int insertAt = Extract.lineEndOffset(unit, text, line(Nodes.end(member) - 1));
    String callIndent = indentOf(lineStartOffset(sel.statements().get(0).getStartPosition()));
    String callLine = callIndent + c.call().replace("\n", "\n" + callIndent) + "\n";
    List<TextEdit> edits =
        List.of(
            new TextEdit(sel.startOffset(), sel.endOffset(), callLine),
            new TextEdit(insertAt, insertAt, method));
    String edited = TextEdit.apply(text, edits, 0);
    NameCheck.require(
        project,
        Map.of(path, text),
        Map.of(path, edits),
        NameCheck.meanings(sel.statements()),
        "extracting " + sel.lines() + " into " + name);
    int methodAt = sel.startOffset() + callLine.length() + (insertAt - sel.endOffset()) + 1;
    Edits.Written written =
        Edits.gated(
            project,
            path,
            text,
            edited,
            List.of(),
            "extracting " + sel.lines() + " into " + name,
            "Nothing was changed. Choose lines whose variables and exceptions the new method can"
                + " take, or adjust them first.");
    Imports.Fixed fixed = written.fixed();
    Stubs.Declared stubs = written.declared();
    int shift = fixed.lineShift();
    int first = Lines.number(edited, methodAt) + shift;
    int last = Lines.number(edited, methodAt + method.length() - 2) + shift;
    return new Outcome(
        "extracted "
            + sel.lines()
            + " of "
            + rel
            + " into "
            + signature(p)
            + (p.results().isEmpty()
                ? ""
                : p.results().size() == 1
                    ? ", returning " + p.results().get(0).getName()
                    : ", returning " + Extract.and(names(p.results())) + " as " + recordName())
            + ", at lines "
            + first
            + "-"
            + last
            + "; the call is line "
            + (sel.startLine() + shift)
            + (p.params().size() > 5
                ? "; it takes " + p.params().size() + " parameters, and fewer lines may read better"
                : "")
            + fixed.said(),
        stubs);
  }

  /** The record a method of several results returns: its name with Result, {@code BothResult}. */
  private String recordName() {
    return Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Result";
  }

  /** What the extraction works out before it writes: the parameters, the value and the returns. */
  private record Plan(
      Set<IVariableBinding> declaredInside,
      List<IVariableBinding> params,
      List<IVariableBinding> results,
      List<ReturnStatement> returns,
      ITypeBinding enclosingResult,
      boolean voidResult) {}

  /** Refuses what the new method cannot be, and works out what it takes and returns. */
  private Plan plan() throws Exception {
    if (visibility.equals("private")) {
      refuseClash();
    }
    refuseConstructorCalls();
    refuseFinalFields();
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
      refuseRecordClash();
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
                + line(Nodes.end(list.get(list.size() - 1)) - 1)
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
    Set<IVariableBinding> declared = new LinkedHashSet<>();
    for (Map.Entry<IVariableBinding, Boolean> e : used.entrySet()) {
      if (e.getValue()) {
        declared.add(e.getKey());
      }
    }
    if (!visibility.equals("private")) {
      checkOverride(params);
    }
    return new Plan(declared, params, results, returns, enclosingResult, voidResult);
  }

  /** The call that replaces the statements, with the new method's result type and return line. */
  private record Call(String resultType, String returnLine, String call) {}

  /** The call line for {@code invocation} (the new method's call), by what the method returns. */
  private Call callOf(Plan p, String invocation, String record) {
    if (p.results().size() > 1) {
      // Several results: a record holds them, and each is taken out of it after the call.
      String holder = Character.toLowerCase(record.charAt(0)) + record.substring(1);
      StringBuilder call = new StringBuilder(record + " " + holder + " = " + invocation + ";");
      for (IVariableBinding r : p.results()) {
        call.append("\n");
        if (p.declaredInside().contains(r)) {
          call.append(Modifier.isFinal(r.getModifiers()) ? "final " : "")
              .append(typeName(r.getType()))
              .append(" ");
        }
        call.append(r.getName()).append(" = ").append(holder).append(".").append(r.getName());
        call.append("();");
      }
      return new Call(
          record,
          "return new " + record + "(" + String.join(", ", names(p.results())) + ");",
          call.toString());
    }
    if (!p.results().isEmpty()) {
      IVariableBinding r = p.results().get(0);
      String resultType = typeName(r.getType());
      String call =
          p.declaredInside().contains(r)
              ? (Modifier.isFinal(r.getModifiers()) ? "final " : "")
                  + resultType
                  + " "
                  + r.getName()
                  + " = "
                  + invocation
                  + ";"
              : r.getName() + " = " + invocation + ";";
      return new Call(resultType, "return " + r.getName() + ";", call);
    }
    if (!p.returns().isEmpty() && !p.voidResult()) {
      return new Call(typeName(p.enclosingResult()), null, "return " + invocation + ";");
    }
    return new Call("void", null, invocation + ";");
  }

  /** The new method's signature, {@code name(T a, U b)}, from its parameters. */
  private String signature(Plan p) {
    List<String> declared = new ArrayList<>();
    for (IVariableBinding v : p.params()) {
      declared.add(typeName(v.getType()) + " " + v.getName());
    }
    return name + "(" + String.join(", ", declared) + ")";
  }

  /**
   * The new method's text, from its Javadoc through its closing brace, indented as the member
   * around it, with the selected statements moved into it.
   */
  private String methodText(Plan p, Call c) throws Exception {
    boolean isStatic = Modifier.isStatic(member.getModifiers());
    List<String> thrown = thrown();
    String header =
        modifier()
            + (isStatic ? "static " : "")
            + typeParameters()
            + c.resultType()
            + " "
            + signature(p)
            + (thrown.isEmpty() ? "" : " throws " + String.join(", ", thrown))
            + " {";

    String memberIndent = indentOf(lineStartOffset(member.getStartPosition()));
    int firstStatement = sel.statements().get(0).getStartPosition();
    String statementIndent = indentOf(lineStartOffset(firstStatement));
    int step = Indent.step(text);
    String bodyIndent = memberIndent + " ".repeat(step > 0 ? step : 4);

    StringBuilder method = new StringBuilder("\n");
    if (doc != null && !doc.isBlank()) {
      method.append(javadoc(doc.strip(), memberIndent));
    }
    if (overridden != null) {
      method.append(memberIndent).append("@Override\n");
    }
    method.append(memberIndent).append(header).append("\n");
    method.append(reindent(statementIndent, bodyIndent));
    if (c.returnLine() != null) {
      method.append(bodyIndent).append(c.returnLine()).append("\n");
    }
    method.append(memberIndent).append("}\n");
    if (p.results().size() > 1) {
      List<String> components = new ArrayList<>();
      for (IVariableBinding r : p.results()) {
        components.add(typeName(r.getType()) + " " + r.getName());
      }
      method
          .append("\n")
          .append(memberIndent)
          .append("private record ")
          .append(c.resultType())
          .append("(")
          .append(String.join(", ", components))
          .append(") {}\n");
    }
    return method.toString();
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
    for (ITypeBinding t : Hierarchy.supertypes(type, true)) {
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
    }
    refuseEnclosing(type, label);
  }

  /**
   * Refuses a record name the enclosing type already has as a member type, and a holder name the
   * member around the lines already uses: the call of a method of several results declares both.
   */
  void refuseRecordClash() {
    String record = recordName();
    String holder = Character.toLowerCase(record.charAt(0)) + record.substring(1);
    if (member.getParent() instanceof AbstractTypeDeclaration t && t.resolveBinding() != null) {
      for (ITypeBinding inner : t.resolveBinding().getDeclaredTypes()) {
        if (inner.getName().equals(record)) {
          throw new Refusal(
              t.getName().getIdentifier()
                  + " already has a type "
                  + record
                  + ", which the method's result would be. Choose another method name.");
        }
      }
    }
    if (Nodes.all(member, SimpleName.class).stream()
        .anyMatch(n -> n.getIdentifier().equals(holder))) {
      throw new Refusal(
          holder
              + " is a name the code around "
              + sel.lines()
              + " uses, and the call would declare it for the method's result. Choose another"
              + " method name.");
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

  /**
   * Refuses statements that assign a final field: in code that compiles, a blank final in a
   * constructor or initializer, which a method may not assign.
   */
  void refuseFinalFields() {
    for (Statement s : sel.statements()) {
      for (Expression target : Nodes.all(s, Expression.class)) {
        if (!(target instanceof Name || target instanceof FieldAccess) || !Nodes.written(target)) {
          continue;
        }
        IVariableBinding field =
            target instanceof Name n && n.resolveBinding() instanceof IVariableBinding v
                ? v
                : target instanceof FieldAccess f ? f.resolveFieldBinding() : null;
        if (field != null && field.isField() && Modifier.isFinal(field.getModifiers())) {
          throw new Refusal(
              "line "
                  + line(target)
                  + " assigns the final field "
                  + field.getName()
                  + ", which only a constructor or initializer may do. Leave that line out"
                  + " of the range.");
        }
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
        target = b.getLabel() != null ? labeled(b, b.getLabel().getIdentifier()) : nearest(b, true);
      } else if (n instanceof ContinueStatement c) {
        what = "continue";
        target =
            c.getLabel() != null ? labeled(c, c.getLabel().getIdentifier()) : nearest(c, false);
      } else if (n instanceof YieldStatement y) {
        what = "yield";
        target = Nodes.ancestor(y, SwitchExpression.class);
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
   * The nodes of the statements that run as part of them, leaving out the bodies of lambdas and of
   * anonymous and local classes.
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
   * The locals the statements use, in order of first use, each with whether the statements declare
   * it.
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
      if (n instanceof SimpleName s
          && Nodes.written(s)
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
              && decl.getStartPosition() < Nodes.end(p))) {
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
    List<String> out = new ArrayList<>();
    for (ITypeBinding t : Exceptions.uncaught(sel.statements())) {
      out.add(typeName(t));
    }
    return out;
  }

  /** The enclosing method's type parameters, as written, or nothing. */
  String typeParameters() {
    MethodDeclaration m = Nodes.ancestor(sel.list().get(0), MethodDeclaration.class);
    if (m == null || m.typeParameters().isEmpty()) {
      return "";
    }
    List<String> out = new ArrayList<>();
    for (Object o : m.typeParameters()) {
      TypeParameter p = (TypeParameter) o;
      out.add(Nodes.source(text, p));
    }
    return "<" + String.join(", ", out) + "> ";
  }

  /** The moved lines, from {@code from} indentation to {@code to}; text blocks as they are. */
  String reindent(String from, String to) {
    List<int[]> blocks = new ArrayList<>();
    for (ASTNode n : allNodes()) {
      if (n instanceof TextBlock b) {
        blocks.add(new int[] {b.getStartPosition(), Nodes.end(b)});
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

  /** {@code b} as code in the type around {@link #member} names it ({@link Extract#typeName}). */
  private String typeName(ITypeBinding b) {
    ITypeBinding at = null;
    for (ASTNode p = member.getParent(); p != null && at == null; p = p.getParent()) {
      if (p instanceof AbstractTypeDeclaration t) {
        at = t.resolveBinding();
      } else if (p instanceof AnonymousClassDeclaration a) {
        at = a.resolveBinding();
      }
    }
    return Extract.typeName(b, at);
  }

  /** The new method's modifier for {@link #visibility}, with the space after it. */
  private String modifier() {
    return visibility.equals("package") ? "" : visibility + " ";
  }

  /**
   * For a method that is not private: refuses another method of that name in a subclass (it would
   * override the new one) and one of a supertype that the new one would not override (an overload
   * could change which one calls pick). One it overrides is kept in {@link #overridden} when every
   * call that could now run the new one in its place is in a method this class overrides.
   */
  private void checkOverride(List<IVariableBinding> params) throws IOException {
    ITypeBinding here =
        member.getParent() instanceof AbstractTypeDeclaration t ? t.resolveBinding() : null;
    if (here == null) {
      throw new Refusal(
          "visibility applies to a method of a named class; extract in the anonymous class with"
              + " the default, private.");
    }
    refuseEnclosing(here, here.getName());
    List<String> mine = new ArrayList<>();
    for (IVariableBinding p : params) {
      mine.add(p.getType().getErasure().getQualifiedName());
    }
    for (ITypeBinding t : Hierarchy.supertypes(here, true)) {
      if (t.getErasure().getKey().equals(here.getErasure().getKey())) {
        for (IMethodBinding m : t.getDeclaredMethods()) {
          if (m.getName().equals(name)) {
            throw new Refusal(
                here.getName()
                    + " already has a method "
                    + name
                    + ", and another of that name could change which one calls pick. Choose"
                    + " another name.");
          }
        }
        continue;
      }
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (!m.getName().equals(name)) {
          continue;
        }
        int mods = m.getModifiers();
        if (overridden != null
            || !MemberMove.erasedParams(m).equals(mine)
            || Modifier.isPrivate(mods)
            || Modifier.isStatic(mods)
            || Modifier.isFinal(mods)
            || Modifier.isStatic(member.getModifiers())) {
          throw new Refusal(
              here.getName()
                  + " (through "
                  + t.getErasure().getName()
                  + ") already has a method "
                  + name
                  + " that the new one would not override, and another of that name could change"
                  + " which one calls pick. Choose another name.");
        }
        overridden = m;
      }
    }
    Holding holding = Holding.of(project, name, path);
    String hereKey = here.getErasure().getKey();
    String key = overridden == null ? null : overridden.getMethodDeclaration().getKey();
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      for (AbstractTypeDeclaration d : Nodes.all(e.getValue(), AbstractTypeDeclaration.class)) {
        ITypeBinding b = d.resolveBinding();
        if (b != null
            && !b.getErasure().getKey().equals(hereKey)
            && keys(Hierarchy.supertypes(b, true)).contains(hereKey)) {
          for (IMethodBinding m : b.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
              throw new Refusal(
                  b.getName()
                      + " already declares "
                      + name
                      + ", which would override the new method and change what calls of it run."
                      + " Choose another name.");
            }
          }
        }
      }
      if (key == null) {
        continue;
      }
      for (SimpleName n : Nodes.all(e.getValue(), SimpleName.class)) {
        if (n.resolveBinding() instanceof IMethodBinding m
            && m.getMethodDeclaration().getKey().equals(key)
            && !(n.getParent() instanceof SuperMethodInvocation)
            && !(n.getParent() instanceof MethodDeclaration)) {
          String why = takenOver(n, here);
          if (why != null) {
            throw new Refusal(
                here.getName()
                    + "."
                    + name
                    + " would override "
                    + overridden.getDeclaringClass().getErasure().getName()
                    + "."
                    + name
                    + ", and the call at line "
                    + e.getValue().getLineNumber(n.getStartPosition())
                    + " of "
                    + project.relative(e.getKey())
                    + " would then run the new one for a "
                    + here.getName()
                    + ": "
                    + why
                    + ". Choose another name, or keep the method private.");
          }
        }
      }
    }
  }

  /**
   * Why the call naming {@code n} could run a method of {@code here} that overrides it, or null
   * when it cannot: a call on this in a method of a supertype that {@code here} overrides, with no
   * call of that method through super.
   */
  private static String takenOver(SimpleName n, ITypeBinding here) {
    if (!(n.getParent() instanceof MethodInvocation call && call.getName() == n)) {
      return "it is not a plain call";
    }
    if (call.getExpression() != null && !(call.getExpression() instanceof ThisExpression)) {
      return "its receiver may be one";
    }
    MethodDeclaration around = Nodes.ancestor(n, MethodDeclaration.class);
    if (around == null
        || !(around.getParent() instanceof AbstractTypeDeclaration type)
        || type.resolveBinding() == null
        || around.resolveBinding() == null) {
      return "it is not in a method of a named class";
    }
    ITypeBinding owner = type.resolveBinding();
    String ownerKey = owner.getErasure().getKey();
    if (!keys(Hierarchy.supertypes(here, true)).contains(ownerKey)
        || ownerKey.equals(here.getErasure().getKey())) {
      return keys(Hierarchy.supertypes(owner, true)).contains(here.getErasure().getKey())
          ? "it is in " + owner.getName() + " itself"
          : null;
    }
    IMethodBinding caller = around.resolveBinding();
    List<String> params = MemberMove.erasedParams(caller);
    for (ITypeBinding t = here;
        t != null && !t.getErasure().getKey().equals(ownerKey);
        t = t.getSuperclass()) {
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (m.getName().equals(caller.getName()) && MemberMove.erasedParams(m).equals(params)) {
          return callsSuper(n.getRoot(), caller)
              ? caller.getName() + " is also called through super"
              : null;
        }
      }
    }
    return "it is in "
        + owner.getName()
        + "."
        + caller.getName()
        + ", which "
        + here.getName()
        + " does not override";
  }

  /** Whether {@code root} calls {@code m} through super anywhere. */
  private static boolean callsSuper(ASTNode root, IMethodBinding m) {
    return Nodes.all(root, SuperMethodInvocation.class).stream()
        .anyMatch(s ->
            s.resolveMethodBinding() != null
                && s.resolveMethodBinding()
                    .getMethodDeclaration()
                    .getKey()
                    .equals(m.getMethodDeclaration().getKey()));
  }

  private static Set<String> keys(List<ITypeBinding> types) {
    Set<String> out = new LinkedHashSet<>();
    for (ITypeBinding t : types) {
      out.add(t.getErasure().getKey());
    }
    return out;
  }

  /**
   * Refuses a name that a method of a type around {@code type}, or of one of its supertypes, has: a
   * method of that name in {@code type} would hide it from every unqualified call inside {@code
   * type} (JLS 15.12.1).
   */
  private void refuseEnclosing(ITypeBinding type, String label) {
    for (ITypeBinding outer = type.getDeclaringClass();
        outer != null;
        outer = outer.getDeclaringClass()) {
      for (ITypeBinding t : Hierarchy.supertypes(outer, true)) {
        for (IMethodBinding m : t.getDeclaredMethods()) {
          if (m.getName().equals(name)) {
            throw new Refusal(
                label
                    + " is inside "
                    + (outer.isAnonymous() ? "an anonymous class" : outer.getName())
                    + (t == outer ? "" : " (through " + t.getErasure().getName() + ")")
                    + ", which has a method "
                    + name
                    + "; a method of that name in "
                    + label
                    + " would hide it from the calls written there. Choose another name.");
          }
        }
      }
    }
  }
}
