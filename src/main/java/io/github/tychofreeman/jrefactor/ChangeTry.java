package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;

/**
 * change_try: one change to the try statement that starts on a line: a catch clause or a type of a
 * multi-catch added or removed, a finally added or removed, or the try unwrapped. Each is a text
 * edit on the file, through the gate as the edit primitives are.
 */
final class ChangeTry {

  private ChangeTry() {}

  /** The changes, one of which a call makes. */
  private static final List<String> CHANGES =
      List.of("add_catch", "remove_catch", "finally", "remove_finally", "unwrap");

  /** A try's new text and what the result says of it. */
  private record Edit(String after, String said) {}

  static Outcome run(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    int line = Primitives.line(args, "line");
    List<String> given = new ArrayList<>();
    for (String change : CHANGES) {
      if (args.has(change) && !Boolean.FALSE.equals(args.raw(change))) {
        given.add(change);
      }
    }
    if (given.size() != 1) {
      throw new Refusal(
          "change_try takes one of add_catch, remove_catch, finally, remove_finally and unwrap"
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
          default -> new Edit(unwrapped(opened.text(), t), "unwrapped the try");
        };
    return Primitives.gated(
        project,
        opened,
        edit.after(),
        args,
        edit.said() + " at line " + line + " of " + rel,
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
}
