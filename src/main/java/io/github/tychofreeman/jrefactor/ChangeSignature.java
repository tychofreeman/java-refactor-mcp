package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;

/**
 * change_signature: a method's return type and the exceptions it declares, changed in it, in every
 * override and implementation, and in each method a method reference implements it with (#342). A
 * change to void rewrites the returns of those bodies and of the lambdas that implement it. Calls
 * are not edited: one the change breaks is refused by the gate, or changed by a later step of the
 * same batch.
 */
final class ChangeSignature {

  private ChangeSignature() {}

  static Outcome run(SourceProject project, Args args) throws Exception {
    Request request = request(args);
    Precondition.require(
        Precondition.that(
            () ->
                request.returnType() != null
                    || !request.added().isEmpty()
                    || !request.removed().isEmpty(),
            () ->
                "change_signature needs return_type (the new type), add_throws or remove_throws"
                    + " (exception types)."),
        Precondition.that(
            () -> List.of("propagate", "catch", "refuse").contains(request.callers()),
            () -> "callers is propagate, catch or refuse, not " + request.callers() + "."),
        Precondition.that(
            () ->
                request.stops().isEmpty()
                    || request.callers().equals("propagate") && !request.added().isEmpty(),
            () ->
                "stop_at names where propagating an added exception ends, so it needs"
                    + " add_throws and callers=propagate."));
    checkTypes(request);
    MethodFamily family =
        MethodFamily.of(project, args, true, "so its signature cannot change.", false);
    SignatureChange change = new SignatureChange(family);
    List<Signatures.Decl> decls = new ArrayList<>(family.decls);
    decls.addAll(referenced(project, family));
    List<String> said = new ArrayList<>();
    List<String> chosen = new ArrayList<>(args.list("imports"));

    if (request.returnType() != null) {
      returnTypeEdits(request, family, args, decls, change, said);
    }

    int declarations = decls.size();
    if (!request.added().isEmpty() || !request.removed().isEmpty()) {
      throwsEdits(request, decls, change, family, said);
      if (!request.added().isEmpty() && !request.callers().equals("refuse")) {
        declarations =
            callerEdits(project, family, change, decls, request, chosen, declarations, said);
      }
    }
    return change.apply(
        project,
        "changed the signature of " + family.label + " (" + String.join("; ", said) + ")",
        chosen,
        "Nothing was changed. "
            + (request.removed().isEmpty()
                ? ""
                : "A body that still throws a removed exception must stop first, and a catch of"
                    + " one that can no longer be thrown must go. ")
            + "Calls are not edited"
            + (request.added().isEmpty() ? "" : ", but for a new exception (callers)")
            + ": one that uses the result"
            + (request.callers().equals("refuse") && !request.added().isEmpty()
                ? ", or does not handle a new exception,"
                : "")
            + " changes in the same batch as this step, with check \"end\".",
        declarations);
  }

  /** Refuses a return type or an added exception that does not parse as a type. */
  private static void checkTypes(Request request) {
    if (request.returnType() != null) {
      Signatures.parsesAs(
          "return_type",
          request.returnType(),
          "abstract class X { abstract " + request.returnType() + " x(); }");
    }
    for (String name : request.added()) {
      Signatures.parsesAs("add_throws", name, "class X { void x() throws " + name + " {} }");
    }
  }

  /**
   * Writes the new return type in each declaration, and for void, the returns of bodies and
   * lambdas; says so in said.
   */
  private static void returnTypeEdits(
      Request request,
      MethodFamily family,
      Args args,
      List<Signatures.Decl> decls,
      SignatureChange change,
      List<String> said) {
    boolean toVoid = request.returnType().equals("void");
    if (!toVoid) {
      Signatures.checkTypeDecidedUses(family, args, request.returnType(), true);
    }
    for (Signatures.Decl d : decls) {
      Signatures.requireReturnType(d, family);
      Type old = d.m().getReturnType2();
      if (Nodes.source(d.file().text(), old).equals(request.returnType())) {
        continue;
      }
      change.replace(d.file(), old, request.returnType());
      if (toVoid && d.m().getBody() != null) {
        voidReturns(change, d.file(), d.m(), d.m().getBody());
      }
    }
    if (toVoid) {
      for (Signatures.Lambda l : family.lambdas) {
        ASTNode body = l.node().getBody();
        if (body instanceof Block block) {
          voidReturns(change, l.file(), l.node(), block);
        } else if (!statement((Expression) body)) {
          if (!quietAndSafe((Expression) body)) {
            throw new Refusal(unkept(l.file(), body));
          }
          change.replace(l.file(), body, "{}");
        }
      }
    }
    said.add("return type " + request.returnType());
  }

  /**
   * Adds and removes the exceptions in each declaration's throws clause, refused when one to remove
   * is declared nowhere, and says so in said.
   */
  private static void throwsEdits(
      Request request,
      List<Signatures.Decl> decls,
      SignatureChange change,
      MethodFamily family,
      List<String> said) {
    List<String> missing = new ArrayList<>(request.removed());
    for (Signatures.Decl d : decls) {
      throwsClause(change, d, request.added(), request.removed(), missing);
    }
    if (!missing.isEmpty()) {
      throw new Refusal(
          family.label
              + " does not declare throws "
              + String.join(", ", missing)
              + ", in it or in an override.");
    }
    if (!request.added().isEmpty()) {
      said.add("throws " + String.join(", ", request.added()) + " added");
    }
    if (!request.removed().isEmpty()) {
      said.add("throws " + String.join(", ", request.removed()) + " removed");
    }
  }

  /**
   * Carries an added exception to the callers as callers asks: each caller that now declares it
   * gets the throws clause; says so in said, adds the imports chosen, and returns declarations with
   * the callers counted.
   */
  private static int callerEdits(
      SourceProject project,
      MethodFamily family,
      SignatureChange change,
      List<Signatures.Decl> decls,
      Request request,
      List<String> chosen,
      int declarations,
      List<String> said)
      throws Exception {
    ThrowsCallers handled =
        ThrowsCallers.handle(
            project,
            family,
            change,
            decls,
            request.added(),
            request.callers().equals("propagate"),
            request.stops(),
            chosen);
    List<String> gained = new ArrayList<>();
    for (Signatures.Decl d : handled.decls) {
      throwsClause(change, d, handled.names.get(d.m()), List.of(), new ArrayList<>());
      gained.add(Names.methodLabel(d.binding()));
    }
    declarations += handled.decls.size();
    chosen.addAll(handled.imports);
    if (!gained.isEmpty()) {
      said.add("its callers now declare it: " + listed(gained));
    }
    if (!handled.caught.isEmpty()) {
      said.add("caught and rethrown unchecked at " + listed(handled.caught));
    }
    return declarations;
  }

  /**
   * What the call asks for: the new return type, the exceptions to add and remove, what callers do
   * with an added one, and where propagating it stops.
   */
  private static Request request(Args args) {
    String returnType = args.string("return_type");
    List<String> added = Args.commaSeparated(args.string("add_throws"));
    List<String> removed = Args.commaSeparated(args.string("remove_throws"));
    String callers = args.string("callers") == null ? "propagate" : args.string("callers");
    List<String> stops = Args.commaSeparated(args.string("stop_at"));
    return new Request(returnType, added, removed, callers, stops);
  }

  /** A change_signature call's arguments, read once: {@link #request}. */
  private record Request(
      String returnType,
      List<String> added,
      List<String> removed,
      String callers,
      List<String> stops) {}

  /** At most eight of {@code items}, then how many more. */
  private static String listed(List<String> items) {
    return items.size() <= 8
        ? String.join(", ", items)
        : String.join(", ", items.subList(0, 8)) + " and " + (items.size() - 8) + " more";
  }

  /**
   * Edits the throws clause of {@code d}: without each of {@code removed} it names (taken from
   * {@code missing} when found), with each of {@code added} it lacks.
   */
  private static void throwsClause(
      SignatureChange change,
      Signatures.Decl d,
      List<String> added,
      List<String> removed,
      List<String> missing) {
    List<?> thrown = d.m().thrownExceptionTypes();
    List<String> now = new ArrayList<>();
    List<String> kept = new ArrayList<>();
    for (Object o : thrown) {
      String text = Nodes.source(d.file().text(), (ASTNode) o);
      now.add(text);
      String match = matching(removed, (Type) o, text);
      if (match == null) {
        kept.add(text);
      } else {
        missing.remove(match);
      }
    }
    for (String name : added) {
      boolean has = false;
      for (Object o : thrown) {
        has |=
            matching(List.of(name), (Type) o, Nodes.source(d.file().text(), (ASTNode) o)) != null;
      }
      if (!has) {
        kept.add(name);
      }
    }
    if (kept.equals(now)) {
      return;
    }
    String text = d.file().text();
    if (thrown.isEmpty()) {
      List<?> ps = d.m().parameters();
      int close =
          text.indexOf(')', ps.isEmpty() ? d.open() : Nodes.end((ASTNode) ps.get(ps.size() - 1)));
      List<?> dims = d.m().extraDimensions();
      int at = dims.isEmpty() ? close + 1 : Nodes.end((ASTNode) dims.get(dims.size() - 1));
      change.replaceRange(d.file(), at, at, " throws " + String.join(", ", kept));
      return;
    }
    int first = ((ASTNode) thrown.get(0)).getStartPosition();
    int last = Nodes.end((ASTNode) thrown.get(thrown.size() - 1));
    if (kept.isEmpty()) {
      int from = text.lastIndexOf("throws", first);
      while (from > 0 && Character.isWhitespace(text.charAt(from - 1))) {
        from--;
      }
      change.replaceRange(d.file(), from, last, "");
    } else {
      change.replaceRange(d.file(), first, last, String.join(", ", kept));
    }
  }

  /**
   * The name of {@code names} that the thrown type {@code t}, written {@code text}, is, or null.
   */
  private static String matching(List<String> names, Type t, String text) {
    ITypeBinding b = t.resolveBinding();
    for (String name : names) {
      String simple = Names.simpleName(name);
      if (text.equals(name)
          || Names.simpleName(text).equals(simple)
              && (!name.contains(".") || b == null || b.getQualifiedName().equals(name))) {
        return name;
      }
    }
    return null;
  }

  /**
   * The project's methods that a method reference implements the family with: {@code
   * Renamer::moveFile} where a {@code Move} is wanted makes {@code moveFile} one more declaration.
   */
  private static List<Signatures.Decl> referenced(SourceProject project, MethodFamily family)
      throws Exception {
    List<Signatures.Decl> out = new ArrayList<>();
    for (Signatures.Reference r : family.references) {
      IMethodBinding target = r.target();
      if (target == null || target.isConstructor() || !target.getDeclaringClass().isFromSource()) {
        continue;
      }
      Path file = project.fileOf(target.getDeclaringClass());
      if (file == null) {
        continue;
      }
      Signatures.Source s = family.sources.get(file);
      if (s == null) {
        String text = Files.readString(file);
        s =
            new Signatures.Source(
                file, project.relative(file), text, Jdt.parse(project, file, text));
        family.sources.put(file, s);
      }
      String key = Signatures.key(target);
      for (MethodDeclaration m : Nodes.all(s.unit(), MethodDeclaration.class)) {
        IMethodBinding b = m.resolveBinding();
        boolean known = false;
        for (Signatures.Decl d : family.decls) {
          known |= d.m() == m;
        }
        for (Signatures.Decl d : out) {
          known |= d.m() == m;
        }
        if (b != null && Signatures.key(b).equals(key) && !known) {
          out.add(new Signatures.Decl(s, m, b, Signatures.open(s.text(), m.getName())));
        }
      }
    }
    return out;
  }

  /**
   * Rewrites each {@code return e;} of {@code fn}, whose body is {@code body}, for a void result:
   * {@code return;} (nothing, when it ends the body) for an {@code e} without effects, {@code e;
   * return;} for a call, an assignment or an increment; refused for another.
   */
  private static void voidReturns(
      SignatureChange change, Signatures.Source s, ASTNode fn, Block body) {
    String text = s.text();
    for (ReturnStatement r : Nodes.all(body, ReturnStatement.class)) {
      Expression e = r.getExpression();
      if (e == null || function(r) != fn) {
        continue;
      }
      List<?> statements = body.statements();
      boolean last = r.getParent() == body && statements.get(statements.size() - 1) == r;
      int lineStart = Lines.start(text, r.getStartPosition());
      int lineEnd = Lines.end(text, Nodes.end(r) - 1);
      boolean alone =
          text.substring(lineStart, r.getStartPosition()).isBlank()
              && text.substring(Nodes.end(r), lineEnd).isBlank();
      if (quietAndSafe(e)) {
        if (last && alone) {
          change.replaceRange(s, lineStart, lineEnd, "");
        } else {
          change.replace(s, r, "return;");
        }
      } else if (statement(e)) {
        String call = Nodes.source(s.text(), e) + ";";
        if (r.getParent() instanceof Block) {
          change.replace(
              s,
              r,
              last ? call : call + "\n" + Lines.indentOf(text, r.getStartPosition()) + "return;");
        } else {
          String outer = Lines.indentOf(text, r.getParent().getStartPosition());
          String inner = outer + " ".repeat(Indent.step(text, 2));
          change.replace(s, r, "{\n" + inner + call + "\n" + inner + "return;\n" + outer + "}");
        }
      } else {
        throw new Refusal(unkept(s, e));
      }
    }
  }

  private static String unkept(Signatures.Source s, ASTNode e) {
    return s.rel()
        + ", line "
        + s.line(e)
        + " returns "
        + Nodes.source(s.text(), e)
        + ", which may do or throw something and is not a statement, so a void method cannot"
        + " keep it. Put it in a local first (extract_variable), or change the body in the same"
        + " batch.";
  }

  /** The method or lambda whose result {@code n} returns. */
  private static ASTNode function(ASTNode n) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (p instanceof MethodDeclaration || p instanceof LambdaExpression) {
        return p;
      }
    }
    return null;
  }

  /** Whether {@code e} can stand as a statement: a call, a new, an assignment or an increment. */
  private static boolean statement(Expression e) {
    return e instanceof MethodInvocation
        || e instanceof SuperMethodInvocation
        || e instanceof ClassInstanceCreation
        || e instanceof Assignment
        || e instanceof PostfixExpression
        || (e instanceof PrefixExpression p
            && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                || p.getOperator() == PrefixExpression.Operator.DECREMENT));
  }

  /**
   * Whether evaluating {@code e} does nothing and cannot throw: names, literals, {@code this.x},
   * and operators on them other than division and a string's {@code +} of an object.
   */
  private static boolean quietAndSafe(Expression e) {
    if (e instanceof SimpleName
        || e instanceof NumberLiteral
        || e instanceof BooleanLiteral
        || e instanceof StringLiteral
        || e instanceof CharacterLiteral
        || e instanceof NullLiteral
        || e instanceof ThisExpression) {
      return true;
    }
    if (e instanceof ParenthesizedExpression p) {
      return quietAndSafe(p.getExpression());
    }
    if (e instanceof FieldAccess f) {
      return f.getExpression() instanceof ThisExpression;
    }
    if (e instanceof PrefixExpression p) {
      return !statement(p) && quietAndSafe(p.getOperand());
    }
    if (e instanceof ConditionalExpression c) {
      return quietAndSafe(c.getExpression())
          && quietAndSafe(c.getThenExpression())
          && quietAndSafe(c.getElseExpression());
    }
    if (e instanceof InfixExpression in) {
      if (in.getOperator() == InfixExpression.Operator.DIVIDE
          || in.getOperator() == InfixExpression.Operator.REMAINDER) {
        return false;
      }
      List<Expression> operands = new ArrayList<>();
      operands.add(in.getLeftOperand());
      operands.add(in.getRightOperand());
      for (Object o : in.extendedOperands()) {
        operands.add((Expression) o);
      }
      for (Expression o : operands) {
        ITypeBinding t = o.resolveTypeBinding();
        boolean plain =
            t != null && (t.isPrimitive() || t.getQualifiedName().equals("java.lang.String"));
        if (!quietAndSafe(o) || !(plain || in.getOperator() != InfixExpression.Operator.PLUS)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
