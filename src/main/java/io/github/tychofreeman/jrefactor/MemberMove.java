package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
      TypeDeclaration t, String subName, String name, String rel, String tool) {
    String base = name;
    List<String> params = null;
    int paren = name.indexOf('(');
    if (paren >= 0) {
      if (!name.endsWith(")")) {
        throw new Refusal("name " + name + " has an unclosed parenthesis.");
      }
      base = name.substring(0, paren).strip();
      String inside = name.substring(paren + 1, name.length() - 1).strip();
      params = new ArrayList<>();
      for (String p : inside.isEmpty() ? List.<String>of() : Declarations.topLevelCommas(inside)) {
        params.add(p.replace(" ", ""));
      }
    }
    List<MethodDeclaration> named = new ArrayList<>();
    for (MethodDeclaration m : t.getMethods()) {
      if (m.getName().getIdentifier().equals(base)) {
        named.add(m);
      }
    }
    List<MethodDeclaration> methods = new ArrayList<>();
    for (MethodDeclaration m : named) {
      if (params == null || Declarations.types(m).equals(params)) {
        methods.add(m);
      }
    }
    List<FieldDeclaration> fields = new ArrayList<>();
    if (params == null) {
      for (FieldDeclaration f : t.getFields()) {
        if (declaresField(f, base)) {
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
              + label(methods.get(0))
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

  /** "m(int)" for a method, as {@code member} reads a name. */
  static String label(MethodDeclaration m) {
    return m.getName().getIdentifier() + "(" + String.join(", ", Declarations.types(m)) + ")";
  }

  private static String labels(List<MethodDeclaration> methods) {
    List<String> out = new ArrayList<>();
    for (MethodDeclaration m : methods) {
      out.add(label(m));
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
   * for the last member the one before, so the members left keep one blank line between them.
   */
  static TextEdit removal(String text, BodyDeclaration m) {
    int start = Lines.start(text, m.getStartPosition());
    int end = Lines.end(text, Nodes.end(m) - 1);
    if (end < text.length() && text.charAt(end) == '\n') {
      end++;
    }
    int next = text.indexOf('\n', end);
    if (next >= 0 && text.substring(end, next).isBlank()) {
      end = next + 1;
    } else if (start >= 2 && text.charAt(start - 1) == '\n') {
      int before = Lines.start(text, start - 2);
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
    int brace = Nodes.end(type) - 1;
    int at = Lines.start(text, brace);
    if (text.substring(at, brace).isBlank()) {
      return List.of(new TextEdit(at, at, "\n  " + moved + "\n"));
    }
    List<?> members = type.bodyDeclarations();
    if (members.isEmpty()) {
      int open = text.lastIndexOf('{', brace - 1);
      return List.of(new TextEdit(open + 1, brace, "\n  " + moved + "\n"));
    }
    int first = ((ASTNode) members.get(0)).getStartPosition();
    int last = Nodes.end(((ASTNode) members.get(members.size() - 1)));
    int open = text.lastIndexOf('{', first - 1);
    List<TextEdit> edits = new ArrayList<>();
    if (Lines.start(text, open) == at) {
      // The members share the line of both braces: start them on a line of their own.
      edits.add(new TextEdit(open + 1, first, "\n  "));
    }
    edits.add(new TextEdit(last, brace, "\n\n  " + moved + "\n"));
    return edits;
  }

  /** The text of {@code m} as written in {@code text}. */
  static String textOf(String text, BodyDeclaration m) {
    return text.substring(m.getStartPosition(), Nodes.end(m));
  }

  /**
   * Applies {@code edits} to each file's text in {@code texts} (the file's imports fixed first),
   * and runs the result through the gate. Each file's edits are applied back to front.
   */
  static Stubs.Declared gate(
      SourceProject project,
      Map<Path, String> texts,
      Map<Path, List<TextEdit>> edits,
      String what,
      String fix)
      throws IOException {
    return gate(project, texts, edits, what, fix, List.of());
  }

  /** {@link #gate}, with {@code chosen} the classes to import where a name is ambiguous. */
  static Stubs.Declared gate(
      SourceProject project,
      Map<Path, String> texts,
      Map<Path, List<TextEdit>> edits,
      String what,
      String fix,
      List<String> chosen)
      throws IOException {
    Map<Path, String> changed = new LinkedHashMap<>();
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      String before = texts.getOrDefault(e.getKey(), Files.readString(e.getKey()));
      List<TextEdit> list = new ArrayList<>(e.getValue());
      list.sort((a, b) -> b.start() - a.start());
      String after = TextEdit.apply(before, list, 0);
      changed.put(e.getKey(), Imports.fix(project, e.getKey(), before, after, chosen).text());
    }
    return Gate.applyOrRefuse(project, changed, what, fix);
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
   * of those, and any other member at its end ({@link #appendTo}).
   */
  static List<TextEdit> addTo(String text, AbstractTypeDeclaration type, String moved, int rank) {
    Declarations.Place place = Declarations.place(type, rank);
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
    if (place.before()) {
      return List.of(new TextEdit(line, line, indent + moved + "\n\n"));
    }
    int end = Nodes.end(place.member());
    return List.of(new TextEdit(end, end, "\n\n" + indent + moved));
  }
}
