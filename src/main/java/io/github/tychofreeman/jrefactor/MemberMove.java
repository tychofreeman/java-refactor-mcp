package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * The text edits and member lookup shared by the tools that move a method or a field between
 * classes: {@code pull_up} and {@code push_down}.
 */
final class MemberMove {

  private MemberMove() {}

  /**
   * The method or field that {@code t} declares, for {@code tool} to move. The name is {@code m},
   * {@code m(int)} or {@code m(Map<String, Integer>)}: a parameter list picks an overload; without
   * one, a method of that name, else a field. Refused when there is neither, when both exist, when
   * a bare name matches several overloads (listing them), or when the field shares its declaration
   * with other variables.
   */
  static BodyDeclaration member(
      AbstractTypeDeclaration t, String subName, String name, String rel, String tool) {
    Declarations.MemberName memberName = Declarations.memberName(name);
    String base = memberName.base();
    List<String> params = memberName.params();
    List<MethodDeclaration> named = new ArrayList<>();
    for (Object o : t.bodyDeclarations()) {
      if (o instanceof MethodDeclaration m && m.getName().getIdentifier().equals(base)) {
        named.add(m);
      }
    }
    List<MethodDeclaration> methods = Declarations.overloads(named, params);
    List<FieldDeclaration> fields = new ArrayList<>();
    if (params == null) {
      for (Object o : t.bodyDeclarations()) {
        if (o instanceof FieldDeclaration f && declaresField(f, base)) {
          fields.add(f);
        }
      }
    }
    if (methods.isEmpty() && fields.isEmpty()) {
      if (!named.isEmpty()) {
        throw new Refusal(
            subName
                + " declares no "
                + name
                + ". Its methods named "
                + base
                + " are "
                + labels(named)
                + ".");
      }
      throw new Refusal(subName + " declares no method or field " + base + " in " + rel + ".");
    }
    if (!methods.isEmpty() && !fields.isEmpty()) {
      throw new Refusal(
          subName
              + " declares both a field and a method named "
              + base
              + "; "
              + tool
              + " cannot tell which to move.");
    }
    if (methods.size() > 1) {
      throw new Refusal(
          subName
              + " has several methods named "
              + base
              + ": "
              + labels(methods)
              + ". Pick one: name one with its parameter types, as "
              + subName
              + "."
              + Declarations.methodLabel(methods.get(0))
              + ".");
    }
    if (!methods.isEmpty()) {
      return methods.get(0);
    }
    FieldDeclaration field = fields.get(0);
    if (field.fragments().size() > 1) {
      throw new Refusal(
          subName
              + "."
              + base
              + " shares its declaration with other variables; "
              + tool
              + " moves one variable per declaration.");
    }
    return field;
  }

  /**
   * Whether {@code t} already declares {@code moved}: a method with the same name and parameter
   * types (an overload with other parameters is not the same), or a field of the same name.
   */
  static boolean declares(TypeDeclaration t, BodyDeclaration moved) {
    ITypeBinding type = t.resolveBinding();
    if (type == null) {
      return false;
    }
    if (moved instanceof MethodDeclaration m) {
      IMethodBinding mine = m.resolveBinding();
      for (IMethodBinding b : type.getDeclaredMethods()) {
        if (mine != null
            && b.getName().equals(mine.getName())
            && erasedParams(b).equals(erasedParams(mine))) {
          return true;
        }
      }
      return false;
    }
    String name = fieldName((FieldDeclaration) moved);
    for (IVariableBinding v : type.getDeclaredFields()) {
      if (v.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  /** The erased qualified names of {@code b}'s parameter types, for comparing signatures. */
  static List<String> erasedParams(IMethodBinding b) {
    List<String> out = new ArrayList<>();
    for (ITypeBinding p : b.getParameterTypes()) {
      out.add(p.getErasure().getQualifiedName());
    }
    return out;
  }

  private static String labels(List<MethodDeclaration> methods) {
    List<String> out = new ArrayList<>();
    for (MethodDeclaration m : methods) {
      out.add(Declarations.methodLabel(m));
    }
    return String.join(", ", out);
  }

  private static String fieldName(FieldDeclaration f) {
    return ((VariableDeclarationFragment) f.fragments().get(0)).getName().getIdentifier();
  }

  private static boolean declaresField(FieldDeclaration f, String name) {
    for (Object o : f.fragments()) {
      if (((VariableDeclarationFragment) o).getName().getIdentifier().equals(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The edit that removes {@code m}'s lines, and one blank line beside them: the one after it, or
   * for the last member the one before, so the members left keep one blank line between them. A
   * line of only spaces is blank.
   */
  static TextEdit removal(String text, BodyDeclaration m) {
    int start = Lines.start(text, m.getStartPosition());
    int end = Lines.end(text, Nodes.end(m) - 1);
    int next = text.indexOf('\n', end);
    if (next >= 0 && text.substring(end, next).isBlank()) {
      end = next + 1;
    } else if (text.substring(end).stripLeading().startsWith("}")
        && start >= 1
        && text.charAt(start - 1) == '\n') {
      int before = Lines.start(text, start - 1);
      if (text.substring(before, start - 1).isBlank()) {
        start = before;
      }
    }
    return new TextEdit(start, end, "");
  }

  /**
   * The edits that add {@code moved} as the last member of {@code type}, a blank line before it. A
   * body on one line ({@code {}} or {@code { int x; }}) is opened up first, so the members end up
   * one per line.
   */
  static List<TextEdit> appendTo(String text, AbstractTypeDeclaration type, String moved) {
    String indent = Indent.member(text, type, 2);
    String member = indent + Indent.restep(moved, indent, Indent.step(text));
    TextEdit afterConstants = afterConstants(text, type, indent, member);
    if (afterConstants != null) {
      return List.of(afterConstants);
    }
    int brace = Nodes.end(type) - 1;
    int at = Lines.start(text, brace);
    if (text.substring(at, brace).isBlank()) {
      return List.of(new TextEdit(at, at, "\n" + member + "\n"));
    }
    List<?> members = type.bodyDeclarations();
    if (members.isEmpty()) {
      int open = text.lastIndexOf('{', brace - 1);
      // The closing brace keeps the indentation of the line it was on, a member type's header.
      return List.of(
          new TextEdit(open + 1, brace, "\n" + member + "\n" + Lines.indentOf(text, brace)));
    }
    int first = ((ASTNode) members.get(0)).getStartPosition();
    int last = Nodes.end(((ASTNode) members.get(members.size() - 1)));
    int open = text.lastIndexOf('{', first - 1);
    List<TextEdit> edits = new ArrayList<>();
    if (Lines.start(text, open) == at) {
      // The members share the line of both braces: start them on a line of their own.
      edits.add(new TextEdit(open + 1, first, "\n" + indent));
    }
    edits.add(new TextEdit(last, brace, "\n\n" + member + "\n" + Lines.indentOf(text, brace)));
    return edits;
  }

  /** The text of {@code m} as written in {@code text}. */
  static String textOf(String text, BodyDeclaration m) {
    return text.substring(m.getStartPosition(), Nodes.end(m));
  }

  /** The top-level type of {@code unit} named {@code name}, or null. */
  static TypeDeclaration typeNamed(CompilationUnit unit, String name) {
    for (Object o : unit.types()) {
      if (o instanceof TypeDeclaration t && t.getName().getIdentifier().equals(name)) {
        return t;
      }
    }
    return null;
  }

  /**
   * The edits that add {@code moved}, a member of {@code rank} ({@link Declarations#rank}), to
   * {@code type} where {@code add_declaration} puts one: a field after the type's fields, a
   * constructor after its constructors or fields, either before its first member when it has none
   * of those, and any other member at its end ({@link #appendTo}). {@code moved} is its source as
   * written, its lines after the first at their old indentation; they are moved to the type's.
   */
  static List<TextEdit> addTo(String text, AbstractTypeDeclaration type, String moved, int rank) {
    Place place = Placement.place(type, rank);
    if (place.member() == null) {
      return appendTo(text, type, moved);
    }
    int start = place.member().getStartPosition();
    int line = Lines.start(text, start);
    String indent = text.substring(line, start);
    if (!indent.isBlank()) {
      // The member shares its line with the brace: appendTo opens the body up.
      return appendTo(text, type, moved);
    }
    String member = Indent.restep(moved, indent, Indent.step(text));
    if (place.before()) {
      return List.of(new TextEdit(line, line, indent + member + "\n\n"));
    }
    int end = Nodes.end(place.member());
    return List.of(new TextEdit(end, end, "\n\n" + indent + member));
  }

  /**
   * For an enum with no member, the edit that adds {@code block} (indented as a member, by {@code
   * indent}) after its constants: a {@code ;} ends them when none does, a blank line follows, and a
   * body on one line is opened up, its constants kept. Null for any other type, where a member
   * needs no separator.
   */
  static TextEdit afterConstants(
      String text, AbstractTypeDeclaration type, String indent, String block) {
    if (!(type instanceof EnumDeclaration e) || !e.bodyDeclarations().isEmpty()) {
      return null;
    }
    List<?> constants = e.enumConstants();
    int close = Nodes.end(type) - 1;
    int first = constants.isEmpty() ? close : ((ASTNode) constants.get(0)).getStartPosition();
    int open = text.lastIndexOf('{', first - 1);
    int last =
        constants.isEmpty() ? open + 1 : Nodes.end((ASTNode) constants.get(constants.size() - 1));
    int next = pastComments(text, last);
    boolean comma = text.charAt(next) == ',';
    if (comma) {
      next = pastComments(text, next + 1);
    }
    String separator = text.charAt(next) == ';' ? "" : ";";
    int line = Lines.start(text, close);
    if (text.substring(line, close).isBlank()) {
      if (separator.isEmpty()) {
        return new TextEdit(line, line, "\n" + block + "\n");
      }
      if (comma || constants.isEmpty()) {
        // After a trailing comma the ; goes on a line of its own, as it does with no constant.
        return new TextEdit(line, line, indent + ";\n\n" + block + "\n");
      }
      return new TextEdit(last, line, ";" + text.substring(last, line) + "\n" + block + "\n");
    }
    String head = text.substring(open + 1, last);
    String tail = text.substring(last, close).stripTrailing();
    String inner = (head + (comma ? tail + separator : separator + tail)).strip();
    String closing = Lines.indentOf(text, close);
    return new TextEdit(open + 1, close, "\n" + indent + inner + "\n\n" + block + "\n" + closing);
  }

  /** The offset of the first character at or after {@code at} that is not space or a comment. */
  private static int pastComments(String text, int at) {
    while (at < text.length()) {
      if (Character.isWhitespace(text.charAt(at))) {
        at++;
      } else if (text.startsWith("//", at)) {
        at = Lines.end(text, at);
      } else if (text.startsWith("/*", at)) {
        at = text.indexOf("*/", at + 2) + 2;
      } else {
        break;
      }
    }
    return at;
  }
}
