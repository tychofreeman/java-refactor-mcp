package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/** Edits of whole declarations, found by name: adding members to a type, replacing one. */
final class Declarations {

  private Declarations() {}

  /** A type of the file, with its name as a tool argument writes it ("A.Inner"). */
  private record Owner(AbstractTypeDeclaration decl, String label) {}

  /**
   * One replacement of {@code text[start, end)}: {@code said} is what it does, and the lines
   * reported are those of the {@code blockLength} characters at {@code blockAt} in {@code text}.
   */
  private record Change(
      int start, int end, String text, int blockAt, int blockLength, String said) {}

  /**
   * Members added to a file's text: the new text, each change, where each change's block starts in
   * it, and the type they went to ("Cart in com/example/Cart.java").
   */
  record Planned(String text, List<Change> changes, List<Integer> blockStarts, String where) {}

  static String add(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String content = args.required("content");
    String after = args.string("after");
    String before = args.string("before");
    if (after != null && before != null) {
      throw new Refusal("Give after or before, not both.");
    }
    Path path = javaFile(project, rel);
    String text = Files.readString(path);
    Planned planned = plan(text, rel, args.string("type"), content, after, before);

    Imports.Fixed fixed = Imports.fix(project, path, text, planned.text(), args.list("imports"));
    Gate.applyOrRefuse(
        project,
        Map.of(path, fixed.text()),
        "adding to " + planned.where(),
        "Declare what the new members use first.");
    List<String> said = new ArrayList<>();
    for (int i = 0; i < planned.changes().size(); i++) {
      Change c = planned.changes().get(i);
      said.add(
          c.said()
              + ", "
              + range(
                  planned.text(),
                  planned.blockStarts().get(i),
                  c.blockLength(),
                  fixed.lineShift()));
    }
    return String.join("; ", said) + fixed.said();
  }

  /**
   * {@code text}, the file {@code rel}, with {@code content}'s members added to {@code type}: at
   * its end, or after or before the member named. Stubs among them are replaced instead.
   */
  static Planned plan(
      String text, String rel, String type, String content, String after, String before) {
    CompilationUnit unit = Edits.parses(text, rel);
    Owner owner = owner(unit, type, rel);
    List<BodyDeclaration> added = members(content);
    String indent = memberIndent(text, owner.decl(), content);

    String where = owner.label() + " in " + rel;
    List<Change> changes = new ArrayList<>();
    List<String> pending = new ArrayList<>();
    List<String> addedLabels = new ArrayList<>();
    for (BodyDeclaration d : added) {
      String block = reindent(content, d, indent);
      MethodDeclaration stub = d instanceof MethodDeclaration m ? stubFor(owner.decl(), m) : null;
      if (stub != null) {
        int start = lineStart(text, stub.getStartPosition());
        String said = "replaced the stub " + label(stub) + " in " + where;
        changes.add(
            new Change(start, lineEnd(text, end(stub)), block + "\n", 0, block.length(), said));
      } else {
        pending.add(block);
        addedLabels.add(label(d));
      }
    }
    if (!pending.isEmpty()) {
      String blocks = String.join("\n\n", pending);
      String said = "added " + String.join(", ", addedLabels) + " to " + where;
      if (after != null) {
        BodyDeclaration m = member(owner, after, true);
        int at = lineEnd(text, end(m));
        changes.add(new Change(at, at, "\n" + blocks + "\n", 1, blocks.length(), said));
      } else if (before != null) {
        BodyDeclaration m = member(owner, before, false);
        int at = lineStart(text, m.getStartPosition());
        changes.add(new Change(at, at, blocks + "\n\n", 0, blocks.length(), said));
      } else {
        int close = end(owner.decl());
        int at = lineStart(text, close);
        String lead = text.substring(at, close).isBlank() ? "" : "\n";
        if (!lead.isEmpty()) {
          at = close;
        }
        String gap = owner.decl().bodyDeclarations().isEmpty() ? "" : "\n";
        String insert = lead + gap + blocks + "\n";
        changes.add(
            new Change(at, at, insert, lead.length() + gap.length(), blocks.length(), said));
      }
    }

    changes.sort(Comparator.comparingInt(Change::start));
    StringBuilder out = new StringBuilder();
    List<Integer> blockStarts = new ArrayList<>();
    int from = 0;
    for (Change c : changes) {
      out.append(text, from, c.start());
      blockStarts.add(out.length() + c.blockAt());
      out.append(c.text());
      from = c.end();
    }
    out.append(text, from, text.length());
    return new Planned(out.toString(), changes, blockStarts, where);
  }

  static String replace(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String name = args.required("name");
    String content = args.string("content");
    if (content == null) {
      throw new Refusal("replace_declaration needs content (empty, to delete the declaration).");
    }
    Path path = javaFile(project, rel);
    String text = Files.readString(path);
    CompilationUnit unit = Edits.parses(text, rel);
    Named target = find(unit, name, rel);
    BodyDeclaration decl = target.decl();
    int firstLine = Edits.lineOf(text, decl.getStartPosition());
    int lastLine = Edits.lineOf(text, end(decl));

    if (content.isBlank()) {
      int start = lineStart(text, decl.getStartPosition());
      int end = lineEnd(text, end(decl));
      if (start >= 2 && text.charAt(start - 1) == '\n' && text.charAt(start - 2) == '\n') {
        start--;
      } else if (end < text.length() && text.charAt(end) == '\n') {
        end++;
      }
      String out = text.substring(0, start) + text.substring(end);
      Imports.Fixed fixed = Imports.fix(project, path, text, out, args.list("imports"));
      Gate.applyOrRefuse(
          project,
          Map.of(path, fixed.text()),
          "deleting " + target.label(),
          "Remove or change its uses first.");
      return "deleted %s from %s (was lines %d-%d)%s"
          .formatted(target.label(), rel, firstLine, lastLine, fixed.said());
    }

    List<BodyDeclaration> replacement = members(content);
    boolean keepDoc = decl.getJavadoc() != null && replacement.get(0).getJavadoc() == null;
    int from = decl.getStartPosition();
    if (keepDoc) {
      from = decl.getJavadoc().getStartPosition() + decl.getJavadoc().getLength();
      while (from < text.length() && Character.isWhitespace(text.charAt(from))) {
        from++;
      }
    }
    int start = lineStart(text, from);
    int end = lineEnd(text, end(decl));
    String indent =
        text.substring(lineStart(text, decl.getStartPosition()), decl.getStartPosition());
    StringBuilder blocks = new StringBuilder();
    for (BodyDeclaration d : replacement) {
      blocks.append(blocks.isEmpty() ? "" : "\n\n").append(reindent(content, d, indent));
    }
    String out = text.substring(0, start) + blocks + "\n" + text.substring(end);
    Imports.Fixed fixed = Imports.fix(project, path, text, out, args.list("imports"));
    Gate.applyOrRefuse(
        project,
        Map.of(path, fixed.text()),
        "replacing " + target.label(),
        "Change its uses in the same way first, or keep the old signature.");
    return "replaced %s in %s, %s%s"
        .formatted(
            target.label(),
            rel,
            range(out, start, blocks.length(), fixed.lineShift()),
            fixed.said());
  }

  /** A declaration found by name, and how to say which one ("A.b(int)"). */
  record Named(BodyDeclaration decl, String label) {}

  static Named find(CompilationUnit unit, String name, String rel) {
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
      for (String p : inside.isEmpty() ? new String[0] : inside.split(",")) {
        params.add(p.replace(" ", ""));
      }
    }
    String[] parts = base.split("\\.");
    if (params == null) {
      AbstractTypeDeclaration type = typePath(unit, parts);
      if (type != null) {
        return new Named(type, base);
      }
    }
    String memberName = parts[parts.length - 1];
    Owner owner =
        owner(
            unit,
            parts.length == 1
                ? null
                : String.join(".", List.of(parts).subList(0, parts.length - 1)),
            rel);
    List<BodyDeclaration> found = new ArrayList<>();
    for (Object o : owner.decl().bodyDeclarations()) {
      BodyDeclaration d = (BodyDeclaration) o;
      if (names(d).contains(memberName)
          && (params == null || (d instanceof MethodDeclaration m && params.equals(types(m))))) {
        found.add(d);
      }
    }
    if (found.isEmpty()) {
      throw new Refusal(
          owner.label()
              + " has no member "
              + memberName
              + (params == null ? "" : " with parameters (" + String.join(", ", params) + ")")
              + ". Its members are "
              + memberLabels(owner.decl())
              + ".");
    }
    if (found.size() > 1) {
      List<String> labels = found.stream().map(Declarations::label).toList();
      throw new Refusal(
          owner.label()
              + " has "
              + found.size()
              + " members named "
              + memberName
              + ": "
              + String.join(", ", labels)
              + ". Name one with its parameter types, as "
              + labels.get(labels.size() - 1)
              + ".");
    }
    BodyDeclaration d = found.get(0);
    if (d instanceof FieldDeclaration f && f.fragments().size() > 1) {
      throw new Refusal(
          owner.label()
              + " declares "
              + label(d)
              + " in one statement. Change one of them with replace_text, or replace them all"
              + " by naming the first and giving content for all.");
    }
    return new Named(d, owner.label() + "." + label(d));
  }

  /** The type at {@code parts} (top-level, then nested), or null. */
  private static AbstractTypeDeclaration typePath(CompilationUnit unit, String[] parts) {
    AbstractTypeDeclaration at = null;
    List<?> scope = unit.types();
    for (String part : parts) {
      at = null;
      for (Object o : scope) {
        if (o instanceof AbstractTypeDeclaration t && t.getName().getIdentifier().equals(part)) {
          at = t;
        }
      }
      if (at == null) {
        return null;
      }
      scope = at.bodyDeclarations();
    }
    return at;
  }

  private static Owner owner(CompilationUnit unit, String type, String rel) {
    if (type == null) {
      List<?> types = unit.types();
      if (types.size() == 1) {
        AbstractTypeDeclaration t = (AbstractTypeDeclaration) types.get(0);
        return new Owner(t, t.getName().getIdentifier());
      }
      throw new Refusal(
          rel
              + " declares "
              + types.size()
              + " top-level types ("
              + String.join(", ", typeLabels(unit))
              + "). Name the one to use.");
    }
    AbstractTypeDeclaration t = typePath(unit, type.split("\\."));
    if (t == null) {
      throw new Refusal(
          rel + " has no type " + type + ". Its types are " + String.join(", ", typeLabels(unit)));
    }
    return new Owner(t, type);
  }

  private static List<String> typeLabels(CompilationUnit unit) {
    List<String> labels = new ArrayList<>();
    for (Object o : unit.types()) {
      collectTypes((AbstractTypeDeclaration) o, "", labels);
    }
    return labels;
  }

  private static void collectTypes(AbstractTypeDeclaration t, String prefix, List<String> into) {
    String label = prefix + t.getName().getIdentifier();
    into.add(label);
    for (Object o : t.bodyDeclarations()) {
      if (o instanceof AbstractTypeDeclaration inner) {
        collectTypes(inner, label + ".", into);
      }
    }
  }

  /** The member of {@code owner} named {@code name}: the last such if {@code last}, else first. */
  private static BodyDeclaration member(Owner owner, String name, boolean last) {
    BodyDeclaration found = null;
    for (Object o : owner.decl().bodyDeclarations()) {
      BodyDeclaration d = (BodyDeclaration) o;
      if (names(d).contains(name) || label(d).equals(name.replace(" ", ""))) {
        found = d;
        if (!last) {
          break;
        }
      }
    }
    if (found == null) {
      throw new Refusal(
          owner.label()
              + " has no member "
              + name
              + ". Its members are "
              + memberLabels(owner.decl())
              + ".");
    }
    return found;
  }

  private static String memberLabels(AbstractTypeDeclaration type) {
    List<String> labels = new ArrayList<>();
    for (Object o : type.bodyDeclarations()) {
      labels.add(label((BodyDeclaration) o));
    }
    return labels.isEmpty() ? "none" : String.join(", ", labels);
  }

  /** The names a member declares. */
  private static List<String> names(BodyDeclaration d) {
    if (d instanceof MethodDeclaration m) {
      return List.of(m.getName().getIdentifier());
    }
    if (d instanceof FieldDeclaration f) {
      List<String> names = new ArrayList<>();
      for (Object o : f.fragments()) {
        names.add(((VariableDeclarationFragment) o).getName().getIdentifier());
      }
      return names;
    }
    if (d instanceof AbstractTypeDeclaration t) {
      return List.of(t.getName().getIdentifier());
    }
    return List.of();
  }

  /** "b(int, String)" for a method, the names for a field, the name for a type. */
  private static String label(BodyDeclaration d) {
    if (d instanceof MethodDeclaration m) {
      return m.getName().getIdentifier() + "(" + String.join(", ", types(m)) + ")";
    }
    if (d instanceof Initializer) {
      return "an initializer";
    }
    return String.join(", ", names(d));
  }

  private static List<String> types(MethodDeclaration m) {
    List<String> types = new ArrayList<>();
    for (Object o : m.parameters()) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) o;
      types.add(p.getType().toString().replace(" ", "") + (p.isVarargs() ? "..." : ""));
    }
    return types;
  }

  /**
   * A method of {@code type} with {@code m}'s signature whose body only throws "not implemented".
   */
  private static MethodDeclaration stubFor(AbstractTypeDeclaration type, MethodDeclaration m) {
    for (Object o : type.bodyDeclarations()) {
      if (o instanceof MethodDeclaration old
          && old.getName().getIdentifier().equals(m.getName().getIdentifier())
          && types(old).equals(types(m))
          && isStub(old)) {
        return old;
      }
    }
    return null;
  }

  private static boolean isStub(MethodDeclaration m) {
    if (m.getBody() == null || m.getBody().statements().size() != 1) {
      return false;
    }
    return m.getBody().statements().get(0) instanceof ThrowStatement t
        && t.getExpression() instanceof ClassInstanceCreation c
        && c.getType().toString().endsWith("UnsupportedOperationException")
        && c.arguments().size() == 1
        && c.arguments().get(0) instanceof StringLiteral s
        && s.getLiteralValue().equals("not implemented");
  }

  /** {@code content} parsed as members of a type, or refused. */
  private static List<BodyDeclaration> members(String content) {
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
    parser.setSource(content.toCharArray());
    Map<String, String> options = org.eclipse.jdt.core.JavaCore.getOptions();
    org.eclipse.jdt.core.JavaCore.setComplianceOptions(Jdt.SOURCE_LEVEL, options);
    parser.setCompilerOptions(options);
    ASTNode node = parser.createAST(null);
    boolean[] broken = {false};
    node.accept(
        new ASTVisitor() {
          @Override
          public void preVisit(ASTNode n) {
            if ((n.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
              broken[0] = true;
            }
          }
        });
    List<BodyDeclaration> members = new ArrayList<>();
    if (node instanceof TypeDeclaration holder) {
      for (Object o : holder.bodyDeclarations()) {
        members.add((BodyDeclaration) o);
      }
    }
    if (broken[0] || members.isEmpty()) {
      throw new Refusal(
          "content does not parse as members of a type (methods, fields, constructors, nested"
              + " types). Send whole declarations, without a package line or imports.");
    }
    return members;
  }

  /**
   * The lines of {@code d} in {@code content}, re-indented to {@code indent}. The common
   * indentation comes from the lines after the first: content copied from a file often has its
   * first line at column 0 and the rest at the file's indentation, and the first line is then taken
   * to be at that indentation too.
   */
  private static String reindent(String content, BodyDeclaration d, String indent) {
    int start = lineStart(content, d.getStartPosition());
    int end = lineEnd(content, end(d));
    String[] lines = content.substring(start, end).replaceAll("\n$", "").split("\n", -1);
    int common = Integer.MAX_VALUE;
    for (int i = 1; i < lines.length; i++) {
      if (!lines[i].isBlank()) {
        common = Math.min(common, leadingSpaces(lines[i]));
      }
    }
    int first = leadingSpaces(lines[0]);
    if (common == Integer.MAX_VALUE) {
      common = first;
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      out.append(i == 0 ? "" : "\n");
      if (lines[i].isBlank()) {
        continue;
      }
      if (i == 0 && first < common) {
        out.append(indent).append(lines[0].stripLeading());
      } else {
        out.append(indent).append(lines[i].substring(Math.min(common, leadingSpaces(lines[i]))));
      }
    }
    return out.toString();
  }

  private static int leadingSpaces(String line) {
    return line.length() - line.stripLeading().length();
  }

  /**
   * The indentation of {@code type}'s members: that of its first member, else one step in. The step
   * is the file's, else that of the {@code content} being added, else 4.
   */
  private static String memberIndent(String text, AbstractTypeDeclaration type, String content) {
    if (!type.bodyDeclarations().isEmpty()) {
      BodyDeclaration first = (BodyDeclaration) type.bodyDeclarations().get(0);
      int at = lineStart(text, first.getStartPosition());
      String lead = text.substring(at, first.getStartPosition());
      if (lead.isBlank()) {
        return lead;
      }
    }
    int at = lineStart(text, type.getStartPosition());
    String typeIndent = text.substring(at, at + leading(text, at));
    int step = step(text) > 0 ? step(text) : step(content) > 0 ? step(content) : 4;
    return typeIndent + " ".repeat(step);
  }

  private static int leading(String text, int at) {
    int n = 0;
    while (at + n < text.length() && (text.charAt(at + n) == ' ' || text.charAt(at + n) == '\t')) {
      n++;
    }
    return n;
  }

  /** The smallest nonzero indentation in {@code text}, or 0 when nothing is indented. */
  private static int step(String text) {
    int step = Integer.MAX_VALUE;
    for (String line : text.split("\n")) {
      int n = line.length() - line.stripLeading().length();
      if (n > 0 && !line.isBlank()) {
        step = Math.min(step, n);
      }
    }
    return step == Integer.MAX_VALUE ? 0 : step;
  }

  private static Path javaFile(SourceProject project, String rel) {
    if (!rel.endsWith(".java")) {
      throw new Refusal(rel + " is not a .java file.");
    }
    Path path = project.file(rel);
    if (!Files.isRegularFile(path)) {
      throw new Refusal("No file " + rel + " in the project. Create it with create_file.");
    }
    return path;
  }

  /**
   * "lines a-b" (or "line a"): where {@code length} characters at {@code at} in {@code text} are,
   * moved by {@code lineShift} lines.
   */
  private static String range(CharSequence text, int at, int length, int lineShift) {
    int first = Edits.lineOf(text, at) + lineShift;
    int last = Edits.lineOf(text, at + Math.max(length - 1, 0)) + lineShift;
    return first == last ? "line " + first : "lines " + first + "-" + last;
  }

  /** The offset of the last character of {@code node}. */
  private static int end(ASTNode node) {
    return node.getStartPosition() + node.getLength() - 1;
  }

  private static int lineStart(String text, int offset) {
    int i = offset;
    while (i > 0 && text.charAt(i - 1) != '\n') {
      i--;
    }
    return i;
  }

  /** The offset just past the line ending of the line that holds {@code offset}. */
  private static int lineEnd(String text, int offset) {
    int i = text.indexOf('\n', offset);
    return i < 0 ? text.length() : i + 1;
  }
}
