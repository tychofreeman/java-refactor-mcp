package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * change_try: one change to the try statement that starts on a line: a catch clause or a type of a
 * multi-catch added or removed, a finally added or removed, the try unwrapped, or a resource added
 * or taken out. Each is a text edit on the file, through the gate as the edit primitives are.
 */
final class ChangeTry {

  private ChangeTry() {}

  /** The changes, one of which a call makes. */
  private static final List<String> CHANGES =
      List.of(
          "add_catch",
          "remove_catch",
          "finally",
          "remove_finally",
          "unwrap",
          "add_resource",
          "remove_resource");

  /** A try's new text, what the result says of it, and what it says after the place. */
  private record Edit(String after, String said, String note) {
    Edit(String after, String said) {
      this(after, said, "");
    }
  }

  static Outcome run(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    int line = args.integer("line");
    List<String> given = new ArrayList<>();
    for (String change : CHANGES) {
      if (args.has(change) && !Boolean.FALSE.equals(args.raw(change))) {
        given.add(change);
      }
    }
    if (given.size() != 1) {
      throw new Refusal(
          "change_try takes one of add_catch, remove_catch, finally, remove_finally, unwrap,"
              + " add_resource and remove_resource"
              + (given.isEmpty() ? "" : "; got " + String.join(" and ", given))
              + ". Send one change a call, or several calls in a batch.");
    }
    Opened opened = Opened.of(project, rel);
    TryStatement t =
        opened.startingOn(line, TryStatement.class, x -> true, "try statement", "", "the try");
    Edit edit =
        switch (given.get(0)) {
          case "add_catch" -> addCatch(project, opened, t, args);
          case "remove_catch" -> removeCatch(opened.text(), t, args.required("remove_catch"));
          case "finally" -> addFinally(opened.text(), t, args.required("finally"));
          case "remove_finally" -> removeFinally(opened.text(), t);
          case "unwrap" -> new Edit(unwrapped(opened.text(), t), "unwrapped the try");
          case "add_resource" ->
              addResource(
                  opened.text(), t, args.required("add_resource"), args.integer("position"));
          default -> removeResource(opened.text(), t, args.required("remove_resource"));
        };
    return Primitives.gated(
        project,
        opened,
        edit.after(),
        args,
        edit.said() + " at line " + line + " of " + rel + edit.note(),
        "changing the try");
  }

  private static Edit addCatch(SourceProject project, Opened opened, TryStatement t, Args args)
      throws Exception {
    String text = opened.text();
    String type = args.required("add_catch").strip();
    List<String> supers =
        ThrowsCallers.exceptionSupers(
            project, opened.file(), opened.rel(), text, t, type, args.list("imports"));
    List<CatchClause> clauses = clauses(t);
    for (CatchClause c : clauses) {
      for (Type a : alternatives(c)) {
        if (supers.get(0).equals(qualified(a))) {
          throw new Refusal(
              "the try already catches "
                  + type
                  + " (line "
                  + Lines.number(text, c.getStartPosition())
                  + "). Change that clause's statements with replace_statements.");
        }
      }
    }
    String join = args.string("join");
    if (join != null) {
      for (CatchClause c : clauses) {
        List<Type> alternatives = alternatives(c);
        for (Type a : alternatives) {
          if (names(a, join.strip())) {
            int at = Nodes.end(alternatives.get(alternatives.size() - 1));
            return new Edit(
                text.substring(0, at) + " | " + type + text.substring(at),
                "added " + type + " to the catch of " + join.strip());
          }
        }
      }
      throw new Refusal(
          "the try has no catch of " + join.strip() + " to join; it catches " + caught(t) + ".");
    }
    int before = clauses.size();
    for (int i = 0; i < clauses.size() && before == clauses.size(); i++) {
      for (Type a : alternatives(clauses.get(i))) {
        if (supers.contains(qualified(a))) {
          before = i;
        }
      }
    }
    int at = before == 0 ? Nodes.end(t.getBody()) : Nodes.end(clauses.get(before - 1));
    String indent = Lines.indentOf(text, t.getStartPosition());
    String body = args.string("body");
    String var = body == null ? ThrowsCallers.freeIn(t) : "e";
    String content = body == null ? "throw " + var + ";" : body;
    String clause =
        " catch ("
            + type
            + " "
            + var
            + ") {\n"
            + Primitives.indent(content, indent + ThrowsCallers.step(text))
            + "\n"
            + indent
            + "}";
    return new Edit(
        text.substring(0, at) + clause + text.substring(at),
        "added a catch of "
            + type
            + (before < clauses.size()
                ? ", before the catch of " + clauses.get(before).getException().getType()
                : ""));
  }

  private static Edit removeCatch(String text, TryStatement t, String type) {
    List<CatchClause> clauses = clauses(t);
    for (int i = 0; i < clauses.size(); i++) {
      CatchClause c = clauses.get(i);
      List<Type> alternatives = alternatives(c);
      for (Type a : alternatives) {
        if (!names(a, type.strip())) {
          continue;
        }
        if (alternatives.size() > 1) {
          List<String> rest = new ArrayList<>();
          for (Type other : alternatives) {
            if (other != a) {
              rest.add(other.toString());
            }
          }
          Type union = c.getException().getType();
          return new Edit(
              text.substring(0, union.getStartPosition())
                  + String.join(" | ", rest)
                  + text.substring(Nodes.end(union)),
              "removed " + type.strip() + " from the multi-catch");
        }
        if (clauses.size() == 1 && t.getFinally() == null && t.resources().isEmpty()) {
          return new Edit(
              unwrapped(text, t),
              "removed the catch of "
                  + type.strip()
                  + "; with no clause left, the try's statements took its place");
        }
        int from = i == 0 ? Nodes.end(t.getBody()) : Nodes.end(clauses.get(i - 1));
        return new Edit(
            text.substring(0, from) + text.substring(Nodes.end(c)),
            "removed the catch of " + type.strip());
      }
    }
    throw new Refusal(
        "the try has no catch of " + type.strip() + "; it catches " + caught(t) + ".");
  }

  private static Edit addFinally(String text, TryStatement t, String content) {
    if (t.getFinally() != null) {
      throw new Refusal(
          "the try has a finally already (line "
              + Lines.number(text, t.getFinally().getStartPosition())
              + "). Change its statements with replace_statements.");
    }
    int at = last(t);
    String indent = Lines.indentOf(text, t.getStartPosition());
    return new Edit(
        text.substring(0, at)
            + " finally {\n"
            + Primitives.indent(content, indent + ThrowsCallers.step(text))
            + "\n"
            + indent
            + "}"
            + text.substring(at),
        "added a finally");
  }

  private static Edit removeFinally(String text, TryStatement t) {
    if (t.getFinally() == null) {
      throw new Refusal("the try has no finally.");
    }
    if (t.catchClauses().isEmpty() && t.resources().isEmpty()) {
      return new Edit(
          unwrappedBody(text, t),
          "removed the finally; with no clause left, the try's statements took its place");
    }
    return new Edit(
        text.substring(0, last(t)) + text.substring(Nodes.end(t.getFinally())),
        "removed the finally");
  }

  /** {@code text} with the try's statements in its place; refused with resources or a finally. */
  private static String unwrapped(String text, TryStatement t) {
    if (!t.resources().isEmpty()) {
      throw new Refusal(
          "the try declares resources, which it closes; unwrap takes a try without them.");
    }
    if (t.getFinally() != null) {
      throw new Refusal(
          "the try's finally would no longer run. Send remove_finally first, or move its"
              + " statements after the try.");
    }
    return unwrappedBody(text, t);
  }

  /** {@code text} with the lines of the try replaced by its body's statements, one step out. */
  private static String unwrappedBody(String text, TryStatement t) {
    int open = t.getBody().getStartPosition();
    int close = Nodes.end(t.getBody()) - 1;
    String content =
        Lines.number(text, open) == Lines.number(text, close)
            ? text.substring(open + 1, close).strip()
            : text.substring(Lines.end(text, open), Lines.start(text, close));
    int from = Lines.start(text, t.getStartPosition());
    int to = Lines.end(text, Nodes.end(t) - 1);
    String indent = Lines.indentOf(text, t.getStartPosition());
    String statements =
        content.isBlank() ? "" : Primitives.indent(content, indent).stripTrailing() + "\n";
    return text.substring(0, from) + statements + text.substring(to);
  }

  /** Where a clause or a finally added last goes: past the last catch, or the body. */
  private static int last(TryStatement t) {
    List<CatchClause> clauses = clauses(t);
    return clauses.isEmpty() ? Nodes.end(t.getBody()) : Nodes.end(clauses.get(clauses.size() - 1));
  }

  private static List<CatchClause> clauses(TryStatement t) {
    List<CatchClause> out = new ArrayList<>();
    for (Object o : t.catchClauses()) {
      out.add((CatchClause) o);
    }
    return out;
  }

  /** The types a clause catches: one, or each of a multi-catch. */
  private static List<Type> alternatives(CatchClause c) {
    Type type = c.getException().getType();
    List<Type> out = new ArrayList<>();
    if (type instanceof UnionType u) {
      for (Object o : u.types()) {
        out.add((Type) o);
      }
    } else {
      out.add(type);
    }
    return out;
  }

  /** Whether {@code a} is the type {@code name}: as written, or by its simple or qualified name. */
  private static boolean names(Type a, String name) {
    ITypeBinding b = a.resolveBinding();
    return a.toString().equals(name)
        || (b != null && (b.getName().equals(name) || qualified(a).equals(name)));
  }

  private static String qualified(Type a) {
    ITypeBinding b = a.resolveBinding();
    return b == null ? a.toString() : b.getErasure().getQualifiedName();
  }

  /** The types the try catches, for a refusal: "IOException and IllegalStateException". */
  private static String caught(TryStatement t) {
    List<String> out = new ArrayList<>();
    for (CatchClause c : clauses(t)) {
      out.add(c.getException().getType().toString());
    }
    return out.isEmpty() ? "nothing" : String.join(" and ", out);
  }

  /**
   * The try with the resource {@code added} at {@code position} of its list (1-based, last when
   * null); a try without resources becomes a try-with-resources.
   */
  private static Edit addResource(String text, TryStatement t, String added, Integer position) {
    List<Expression> resources = resources(t);
    String resource = added.strip();
    if (resource.endsWith(";")) {
      resource = resource.substring(0, resource.length() - 1).strip();
    }
    int at = position == null ? resources.size() + 1 : position;
    if (at < 1 || at > resources.size() + 1) {
      throw new Refusal(
          "position "
              + at
              + " is not in the resource list: pass 1 to "
              + (resources.size() + 1)
              + ", or leave it out to add the resource last.");
    }
    int eq = resource.indexOf('=');
    if (eq > 0) {
      String[] words = resource.substring(0, eq).strip().split("\\W+");
      String name = words[words.length - 1];
      List<ASTNode> scope = new ArrayList<>(resources.subList(at - 1, resources.size()));
      scope.add(t.getBody());
      hidden(text, scope, name, "the resource " + name, "Give the resource another name.");
    }
    String after;
    if (resources.isEmpty()) {
      int open = t.getBody().getStartPosition();
      after = text.substring(0, open) + "(" + resource + ") " + text.substring(open);
    } else if (at <= resources.size()) {
      int start = resources.get(at - 1).getStartPosition();
      after = text.substring(0, start) + resource + "; " + text.substring(start);
    } else {
      int end = Nodes.end(resources.get(resources.size() - 1));
      after = text.substring(0, end) + "; " + resource + text.substring(end);
    }
    return new Edit(
        after,
        "added the resource "
            + resource
            + ", "
            + at
            + " of "
            + (resources.size() + 1)
            + (resources.isEmpty() ? "" : " (resources close last first)"));
  }

  /**
   * The try with the resource {@code name} taken out: declared just before a try of the body and
   * the resources after it, whose finally closes it (when it is not null), as the
   * try-with-resources did. The resources before it, the catches and the finally stay on a try
   * around both, which is dropped when it is left with none of them.
   */
  private static Edit removeResource(String text, TryStatement t, String name) {
    List<Expression> resources = resources(t);
    List<String> names = new ArrayList<>();
    for (Expression r : resources) {
      names.add(variable(r));
    }
    int i = names.indexOf(name.strip());
    if (i < 0) {
      throw new Refusal(
          resources.isEmpty()
              ? "the try has no resources."
              : "the try has no resource "
                  + name.strip()
                  + "; its resources are "
                  + String.join(" and ", names)
                  + ".");
    }
    Expression r = resources.get(i);
    String var = names.get(i);
    boolean outer = i > 0 || !t.catchClauses().isEmpty() || t.getFinally() != null;
    String indent = Lines.indentOf(text, t.getStartPosition());
    String step = ThrowsCallers.step(text);
    String in = outer ? indent + step : indent;
    if (!outer) {
      hidden(
          text,
          later(t),
          var,
          "declared before the try, " + var,
          "Rename the resource first (rename), then remove it.");
    }
    String receiver = r instanceof VariableDeclarationExpression ? var : Nodes.source(text, r);
    boolean mayBeNull =
        !(r instanceof VariableDeclarationExpression v
            && ((VariableDeclarationFragment) v.fragments().get(0)).getInitializer()
                instanceof ClassInstanceCreation);
    String close =
        mayBeNull
            ? "if ("
                + receiver
                + " != null) {\n"
                + in
                + step
                + step
                + receiver
                + ".close();\n"
                + in
                + step
                + "}"
            : receiver + ".close();";
    String body = Nodes.source(text, t.getBody());
    String replaced =
        (r instanceof VariableDeclarationExpression ? Nodes.source(text, r) + ";\n" + in : "")
            + "try "
            + listed(text, resources.subList(i + 1, resources.size()))
            + (outer ? Indent.restep(body, in, step.length()) : body)
            + " finally {\n"
            + in
            + step
            + close
            + "\n"
            + in
            + "}";
    if (outer) {
      replaced =
          "try "
              + listed(text, resources.subList(0, i))
              + "{\n"
              + in
              + replaced
              + "\n"
              + indent
              + "}"
              + text.substring(Nodes.end(t.getBody()), Nodes.end(t));
    }
    return new Edit(
        text.substring(0, t.getStartPosition()) + replaced + text.substring(Nodes.end(t)),
        "took the resource " + var + " out of the try",
        ": "
            + (r instanceof VariableDeclarationExpression
                ? outer
                    ? "declared inside the try, just before a try of the body that closes it"
                    : "declared just before the try, which closes it"
                : outer ? "a try of the body inside the try closes it" : "the try closes it")
            + " in its finally"
            + (mayBeNull ? " when it is not null" : "")
            + ". Changed: an exception "
            + receiver
            + ".close() throws while another is thrown (by the body or a later resource's"
            + " close()) now replaces that one, where the try-with-resources added it to the"
            + " other's suppressed exceptions.");
  }

  private static List<Expression> resources(TryStatement t) {
    List<Expression> out = new ArrayList<>();
    for (Object o : t.resources()) {
      out.add((Expression) o);
    }
    return out;
  }

  /** The name a resource is known by: its variable's, or the name it reads ({@code this.in}). */
  private static String variable(Expression r) {
    if (r instanceof VariableDeclarationExpression v) {
      return ((VariableDeclarationFragment) v.fragments().get(0)).getName().getIdentifier();
    }
    if (r instanceof FieldAccess f) {
      return f.getName().getIdentifier();
    }
    if (r instanceof QualifiedName q) {
      return q.getName().getIdentifier();
    }
    return r.toString();
  }

  /** {@code resources} as a try's list, {@code "(a; b) "}, or nothing when there are none. */
  private static String listed(String text, List<Expression> resources) {
    if (resources.isEmpty()) {
      return "";
    }
    return "("
        + text.substring(
            resources.get(0).getStartPosition(), Nodes.end(resources.get(resources.size() - 1)))
        + ") ";
  }

  /** The statements after {@code t} in its block or switch; refused where a local cannot go. */
  private static List<ASTNode> later(TryStatement t) {
    List<?> statements =
        t.getParent() instanceof Block b
            ? b.statements()
            : t.getParent() instanceof SwitchStatement s ? s.statements() : null;
    if (statements == null) {
      throw new Refusal(
          "the try is not in a block, so no local can be declared before it. Put braces around"
              + " it first.");
    }
    List<ASTNode> out = new ArrayList<>();
    for (Object o : statements.subList(statements.indexOf(t) + 1, statements.size())) {
      out.add((ASTNode) o);
    }
    return out;
  }

  /**
   * Refuses when a variable named {@code name} is used in {@code scope}, which a new local of that
   * name, {@code what}, would hide.
   */
  private static void hidden(
      String text, List<ASTNode> scope, String name, String what, String fix) {
    for (ASTNode node : scope) {
      for (SimpleName n : Nodes.all(node, SimpleName.class)) {
        if (n.getIdentifier().equals(name) && n.resolveBinding() instanceof IVariableBinding b) {
          throw new Refusal(
              what
                  + " would hide the "
                  + (b.isField() ? "field " : "variable ")
                  + name
                  + " that line "
                  + Lines.number(text, n.getStartPosition())
                  + " uses. "
                  + fix);
        }
      }
    }
  }
}
