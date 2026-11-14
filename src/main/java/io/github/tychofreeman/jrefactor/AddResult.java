package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;

final class AddResult {
  private AddResult() {}

  /**
   * add_result: the method {@code name} returns one more value, {@code result} of {@code type},
   * which its callers ignore: a record {@code <Method>Result(value, result)} when it returned a
   * value (calls that use it read {@code .value()}), else the value itself; each return gives the
   * type's zero.
   */
  static Outcome run(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String type = args.required("type").strip();
    String result = args.required("result");
    Qualified q = Qualified.of(args.required("name"), "Type.method, the class and the method");
    Precondition.require(
        Precondition.identifier(result, "the result"),
        Precondition.that(
            () -> !result.equals("value"),
            () ->
                "value names the method's own result in the record; choose another name for the"
                    + " new one."));
    Path file = Opened.of(project, rel).file();
    String member = q.member();
    String base = member.contains("(") ? member.substring(0, member.indexOf('(')).strip() : member;
    Holding holding = Holding.of(project, base, file);
    String text = holding.texts().get(file);
    TypeDeclaration owner = MemberMove.typeNamed(holding.units().get(file), q.type());
    Precondition.require(
        Precondition.that(
            () -> owner != null, () -> q.type() + " is not a top-level class of " + rel + "."));
    BodyDeclaration declared = MemberMove.member(owner, q.type(), member, rel, "add_result");
    Precondition.require(
        Precondition.that(
            () ->
                declared instanceof MethodDeclaration d
                    && !d.isConstructor()
                    && d.getBody() != null
                    && d.resolveBinding() != null,
            () -> q.type() + "." + member + " is not a method with a body that resolves."));
    MethodDeclaration m = (MethodDeclaration) declared;
    IMethodBinding method = m.resolveBinding();
    String key = method.getKey();
    if (Nodes.overrideAnnotation(m) != null) {
      throw new Refusal(
          q.type() + "." + member + " overrides a method, whose result its supertype fixes.");
    }
    boolean isVoid = m.getReturnType2().toString().equals("void");
    String recordName = Character.toUpperCase(base.charAt(0)) + base.substring(1) + "Result";
    for (TypeDeclaration t : owner.getTypes()) {
      if (!isVoid && t.getName().getIdentifier().equals(recordName)) {
        throw new Refusal(q.type() + " already has a type " + recordName + ".");
      }
    }
    String zero = JavaTypes.zero(type);

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    int calls = 0;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      List<TextEdit> list = new ArrayList<>();
      for (MethodDeclaration d : Nodes.all(e.getValue(), MethodDeclaration.class)) {
        IMethodBinding b = d.resolveBinding();
        if (b != null && !b.getKey().equals(key) && b.overrides(method)) {
          throw new Refusal(
              q.type()
                  + "."
                  + member
                  + " is overridden by "
                  + d.getName()
                  + " in "
                  + project.relative(e.getKey())
                  + ", whose result would have to change with it.");
        }
      }
      for (MethodReference r : Nodes.all(e.getValue(), MethodReference.class)) {
        IMethodBinding b = r.resolveMethodBinding();
        if (b != null && b.getMethodDeclaration().getKey().equals(key)) {
          throw new Refusal(
              q.type()
                  + "."
                  + member
                  + " is used as a method reference in "
                  + project.relative(e.getKey())
                  + "; write it as a lambda first.");
        }
      }
      for (MethodInvocation c : Nodes.all(e.getValue(), MethodInvocation.class)) {
        IMethodBinding b = c.resolveMethodBinding();
        if (b == null || !b.getMethodDeclaration().getKey().equals(key)) {
          continue;
        }
        if (e.getKey().equals(file) && Nodes.within(c, m)) {
          throw new Refusal(
              q.type() + "." + member + " calls itself, which add_result does not rewrite.");
        }
        if (!isVoid && !(c.getParent() instanceof ExpressionStatement)) {
          list.add(new TextEdit(Nodes.end(c), Nodes.end(c), ".value()"));
          calls++;
        }
      }
      if (!list.isEmpty()) {
        edits.put(e.getKey(), list);
      }
    }

    List<TextEdit> own = edits.computeIfAbsent(file, k -> new ArrayList<>());
    ASTNode returnType = m.getReturnType2();
    String oldType = Nodes.source(text, returnType);
    own.add(
        new TextEdit(
            returnType.getStartPosition(), Nodes.end(returnType), isVoid ? type : recordName));
    for (ReturnStatement r : Nodes.all(m.getBody(), ReturnStatement.class)) {
      if (!ownReturn(r, m)) {
        continue;
      }
      if (isVoid) {
        own.add(new TextEdit(r.getStartPosition(), Nodes.end(r), "return " + zero + ";"));
      } else {
        Expression x = r.getExpression();
        own.add(
            new TextEdit(
                x.getStartPosition(),
                Nodes.end(x),
                "new " + recordName + "(" + Nodes.source(text, x) + ", " + zero + ")"));
      }
    }
    String indent = Lines.indentOf(text, m.getStartPosition());
    List<?> statements = m.getBody().statements();
    if (isVoid
        && (statements.isEmpty()
            || !(statements.get(statements.size() - 1) instanceof ReturnStatement
                || statements.get(statements.size() - 1) instanceof ThrowStatement))) {
      String inner =
          statements.isEmpty()
              ? indent + " ".repeat(Indent.step(text, 2))
              : Lines.indentOf(text, ((ASTNode) statements.get(0)).getStartPosition());
      int close = Lines.start(text, Nodes.end(m.getBody()) - 1);
      own.add(new TextEdit(close, close, inner + "return " + zero + ";\n"));
    }
    if (!isVoid) {
      int after = Lines.end(text, Nodes.end(m) - 1);
      own.add(
          new TextEdit(
              after,
              after,
              "\n"
                  + indent
                  + "record "
                  + recordName
                  + "("
                  + oldType
                  + " value, "
                  + type
                  + " "
                  + result
                  + ") {}\n"));
    }
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            project,
            holding.texts(),
            edits,
            List.of(),
            "adding the result " + result + " to " + q.type() + "." + member,
            "Check that " + type + " resolves where the method is.");
    Stubs.Declared stubs = written.declared();
    return new Outcome(
        "added the result "
            + result
            + " ("
            + type
            + ") to "
            + q.type()
            + "."
            + member
            + ": it returns "
            + (isVoid ? type : recordName + "(value, " + result + ")")
            + ", each return giving "
            + zero
            + (isVoid
                ? ""
                : "; " + calls + (calls == 1 ? " call reads" : " calls read") + " .value()")
            + written.said(),
        stubs);
  }

  /** Whether {@code r} returns from {@code m} itself, not from a lambda or class inside it. */
  private static boolean ownReturn(ReturnStatement r, MethodDeclaration m) {
    for (ASTNode p = r.getParent(); p != m; p = p.getParent()) {
      if (p instanceof LambdaExpression
          || p instanceof AnonymousClassDeclaration
          || p instanceof TypeDeclarationStatement) {
        return false;
      }
    }
    return true;
  }
}
