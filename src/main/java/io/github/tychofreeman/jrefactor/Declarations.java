package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/** Edits of whole declarations, found by name: adding members to a type, replacing one. */
final class Declarations {

  private Declarations() {}

  /** A type of the file, with its name as a tool argument writes it ("A.Inner"). */
  record Owner(AbstractTypeDeclaration decl, String label) {}

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

  static Outcome add(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String content = args.string("content");
    String testFor = args.string("test_for");
    String implement = args.string("implement");
    boolean constructor = args.has("constructor");
    int given =
        (content == null ? 0 : 1)
            + (testFor == null ? 0 : 1)
            + (implement == null ? 0 : 1)
            + (constructor ? 1 : 0);
    if (given != 1) {
      throw new Refusal(
          given == 0
              ? "add_declaration needs content, test_for for a test of a method, implement for"
                  + " methods of a supertype, or constructor for one from fields."
              : "add_declaration takes one of content, test_for, implement and constructor.");
    }
    Path path = project.javaFile(rel);
    String text = Files.readString(path);
    List<String> chosen = new ArrayList<>(args.list("imports"));
    String made = "";
    if (testFor != null) {
      TestFor.Framework framework = TestFor.framework(project);
      TestFor.Target target = TestFor.target(project, testFor);
      if (target.method() == null) {
        throw new Refusal(
            "test_for in add_declaration names a method ("
                + testFor
                + ".method); for a test class, use create_file with test_for.");
      }
      boolean staticFail =
          text.contains("import static " + framework.assertions() + ".fail;")
              || text.contains("import static " + framework.assertions() + ".*;");
      String name = TestFor.freeName(text, target.method().getName());
      content = TestFor.method(target, framework, name, staticFail);
      chosen.add(framework.test());
      if (!staticFail) {
        chosen.add(framework.assertions());
      }
      made = TestFor.note(target);
    }
    if (project.session().skeletonOnly && args.string("content") != null) {
      Generated.checkMembers(members(content), " in place of content.");
    }
    if (implement != null) {
      content = Implement.members(project, path, text, args.string("type"), implement);
    }
    if (constructor) {
      content = FromFields.constructor(text, rel, args.string("type"), args.list("constructor"));
    }
    Planned planned = plan(text, rel, args.string("type"), content);

    Edits.Written written =
        Edits.gated(
            project,
            path,
            text,
            planned.text(),
            chosen,
            "adding to " + planned.where(),
            "Declare what the new members use first.");
    Imports.Fixed fixed = written.fixed();
    Stubs.Declared stubs = written.declared();
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
    return new Outcome(String.join("; ", said) + fixed.said() + made, stubs);
  }

  /**
   * {@code text}, the file {@code rel}, with {@code content}'s members added to {@code type}: at
   * its end, or after or before the member named. Stubs among them are replaced instead.
   */
  static Planned plan(String text, String rel, String type, String content) {
    CompilationUnit unit = Edits.parses(text, rel);
    Owner owner = owner(unit, type, rel);
    List<BodyDeclaration> added = members(content);
    String indent = memberIndent(text, owner.decl(), content);

    String where = owner.label() + " in " + rel;
    List<Change> changes = new ArrayList<>();
    List<BodyDeclaration> pending = new ArrayList<>();
    Map<BodyDeclaration, String> blocks = new HashMap<>();
    for (BodyDeclaration d : added) {
      String block = reindent(content, d, indent);
      BodyDeclaration stub = stubFor(owner.decl(), d, text);
      if (stub != null) {
        int start = Lines.start(text, stub.getStartPosition());
        String said =
            stub instanceof MethodDeclaration m && !isStub(m)
                ? "replaced the constructor " + label(stub) + ", which only set fields, in " + where
                : "replaced the stub " + label(stub) + " in " + where;
        changes.add(
            new Change(start, Lines.end(text, end(stub)), block + "\n", 0, block.length(), said));
      } else {
        pending.add(d);
        blocks.put(d, block);
      }
    }
    // Each member goes where its kind goes: a field after the last field, a constructor after the
    // last constructor or field, before the first member when there is none; the rest at the end.
    pending.sort(Comparator.comparingInt(Placement::rank));
    Map<Place, List<BodyDeclaration>> byPlace = new LinkedHashMap<>();
    for (BodyDeclaration d : pending) {
      byPlace
          .computeIfAbsent(Placement.place(owner.decl(), Placement.rank(d)), p -> new ArrayList<>())
          .add(d);
    }
    for (Map.Entry<Place, List<BodyDeclaration>> e : byPlace.entrySet()) {
      String joined = String.join("\n\n", e.getValue().stream().map(blocks::get).toList());
      String said =
          "added "
              + String.join(", ", e.getValue().stream().map(Declarations::label).toList())
              + " to "
              + where;
      BodyDeclaration m = e.getKey().member();
      TextEdit afterConstants = MemberMove.afterConstants(text, owner.decl(), indent, joined);
      if (m != null && !e.getKey().before()) {
        int at = Lines.end(text, end(m));
        changes.add(new Change(at, at, "\n" + joined + "\n", 1, joined.length(), said));
      } else if (m != null) {
        int at = Lines.start(text, m.getStartPosition());
        changes.add(new Change(at, at, joined + "\n\n", 0, joined.length(), said));
      } else if (afterConstants != null) {
        // An enum with no member yet: its constants end with a ; first.
        String insert = afterConstants.text();
        int blockAt = insert.length() - joined.length() - 1;
        changes.add(
            new Change(
                afterConstants.start(),
                afterConstants.end(),
                insert,
                blockAt,
                joined.length(),
                said));
      } else {
        int close = end(owner.decl());
        int at = Lines.start(text, close);
        String lead = text.substring(at, close).isBlank() ? "" : "\n";
        if (!lead.isEmpty()) {
          at = close;
        }
        String gap = owner.decl().bodyDeclarations().isEmpty() ? "" : "\n";
        String insert = lead + gap + joined + "\n";
        changes.add(
            new Change(at, at, insert, lead.length() + gap.length(), joined.length(), said));
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

  static Outcome replace(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String name = args.required("name");
    String content = args.string("content");
    if (content == null) {
      throw new Refusal("replace_declaration needs content (empty, to delete the declaration).");
    }
    Path path = project.javaFile(rel);
    String text = Files.readString(path);
    CompilationUnit unit = Edits.parses(text, rel);
    Named target = find(unit, name, rel);
    BodyDeclaration decl = target.decl();
    int firstLine = Lines.number(text, decl.getStartPosition());
    int lastLine = Lines.number(text, end(decl));
    if (content.isBlank()) {
      SafeDelete.check(project, rel, name, target.label());
    }

    if (content.isBlank()
        && decl instanceof AbstractTypeDeclaration
        && decl.getParent() == unit
        && unit.types().size() == 1) {
      // The file's only type: the file goes with it (a null text deletes it; undo restores it).
      Map<Path, String> gone = new HashMap<>();
      gone.put(path, null);
      Stubs.Declared stubs =
          Gate.applyOrRefuse(
              project, gone, "deleting " + target.label(), "Remove or change its uses first.");
      return new Outcome(
          "deleted %s and its file %s (was lines %d-%d)"
              .formatted(target.label(), rel, firstLine, lastLine),
          stubs);
    }
    if (content.isBlank()) {
      int start = Lines.start(text, decl.getStartPosition());
      int end = Lines.end(text, end(decl));
      if (start >= 2 && text.charAt(start - 1) == '\n' && text.charAt(start - 2) == '\n') {
        start--;
      } else if (end < text.length() && text.charAt(end) == '\n') {
        end++;
      }
      String out = text.substring(0, start) + text.substring(end);
      Edits.Written written =
          Edits.gated(
              project,
              path,
              text,
              out,
              args.list("imports"),
              "deleting " + target.label(),
              "Remove or change its uses first.");
      Imports.Fixed fixed = written.fixed();
      Stubs.Declared stubs = written.declared();
      return new Outcome(
          "deleted %s from %s (was lines %d-%d)%s"
              .formatted(target.label(), rel, firstLine, lastLine, fixed.said()),
          stubs);
    }

    List<BodyDeclaration> replacement = members(content);
    if (project.session().skeletonOnly) {
      for (BodyDeclaration d : replacement) {
        if (d instanceof AbstractTypeDeclaration t) {
          Generated.checkMembers(
              t.bodyDeclarations(),
              " in an add_declaration after the replace_declaration, in one batch, leaving it out of "
                  + t.getName().getIdentifier()
                  + ".");
        }
      }
    }
    boolean keepDoc = decl.getJavadoc() != null && replacement.get(0).getJavadoc() == null;
    int from = decl.getStartPosition();
    if (keepDoc) {
      from = decl.getJavadoc().getStartPosition() + decl.getJavadoc().getLength();
      while (from < text.length() && Character.isWhitespace(text.charAt(from))) {
        from++;
      }
    }
    int start = Lines.start(text, from);
    int end = Lines.end(text, end(decl));
    String indent =
        text.substring(Lines.start(text, decl.getStartPosition()), decl.getStartPosition());
    StringBuilder blocks = new StringBuilder();
    for (BodyDeclaration d : replacement) {
      blocks.append(blocks.isEmpty() ? "" : "\n\n").append(reindent(content, d, indent));
    }
    String out = text.substring(0, start) + blocks + "\n" + text.substring(end);
    Edits.Written written =
        Edits.gated(
            project,
            path,
            text,
            out,
            args.list("imports"),
            "replacing " + target.label(),
            "Change its uses in the same way first, or keep the old signature.");
    Imports.Fixed fixed = written.fixed();
    Stubs.Declared stubs = written.declared();
    return new Outcome(
        "replaced %s in %s, %s%s"
            .formatted(
                target.label(),
                rel,
                range(out, start, blocks.length(), fixed.lineShift()),
                fixed.said()),
        stubs);
  }

  /**
   * The parts of a parameter list split at its commas, outside any {@code <...>} or {@code (...)}.
   */
  static List<String> topLevelCommas(String list) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int from = 0;
    for (int i = 0; i < list.length(); i++) {
      char c = list.charAt(i);
      if (c == '<' || c == '(') {
        depth++;
      } else if (c == '>' || c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        parts.add(list.substring(from, i));
        from = i + 1;
      }
    }
    parts.add(list.substring(from));
    return parts;
  }

  /** A declaration found by name, and how to say which one ("A.b(int)"). */
  record Named(BodyDeclaration decl, String label) {}

  static Named find(CompilationUnit unit, String name, String rel) {
    MemberName memberNameResult = memberName(name);
    String base = memberNameResult.base();
    List<String> params = memberNameResult.params();
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
    List<MethodDeclaration> named = new ArrayList<>();
    for (Object o : owner.decl().bodyDeclarations()) {
      BodyDeclaration d = (BodyDeclaration) o;
      if (names(d).contains(memberName)) {
        found.add(d);
        if (d instanceof MethodDeclaration m) {
          named.add(m);
        }
      }
    }
    if (params != null) {
      found = new ArrayList<>(overloads(named, params));
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

  /**
   * {@code name} read as a member's name: {@code m}, {@code Type.m}, or either with parameter types
   * ({@code m(int, String)}), which are null when it has none.
   */
  static MemberName memberName(String name) {
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
      for (String p : inside.isEmpty() ? List.<String>of() : topLevelCommas(inside)) {
        params.add(p.replace(" ", ""));
      }
    }
    return new MemberName(base, params);
  }

  /** A member's name as written: the name, and its parameter types or null. */
  record MemberName(String base, List<String> params) {}

  /** The type at {@code parts} (top-level, then nested), or null. */
  static AbstractTypeDeclaration typePath(CompilationUnit unit, String[] parts) {
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

  static Owner owner(CompilationUnit unit, String type, String rel) {
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
      return methodLabel(m);
    }
    if (d instanceof Initializer) {
      return "an initializer";
    }
    return String.join(", ", names(d));
  }

  static List<String> types(MethodDeclaration m) {
    List<String> types = new ArrayList<>();
    for (Object o : m.parameters()) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) o;
      types.add(p.getType().toString().replace(" ", "") + (p.isVarargs() ? "..." : ""));
    }
    return types;
  }

  /**
   * The stub in {@code type} that {@code d} replaces: a method of its name whose parameter types
   * erase to the same ({@code List} and {@code List<?>}) and whose body only throws "not
   * implemented" (or, for a constructor, only assigns its parameters to fields of their names, as
   * an edit or a stub may have written it), or a field of its name without a value whose line ends
   * in the stub mark.
   */
  private static BodyDeclaration stubFor(
      AbstractTypeDeclaration type, BodyDeclaration d, String text) {
    for (Object o : type.bodyDeclarations()) {
      if (d instanceof MethodDeclaration m
          && o instanceof MethodDeclaration old
          && old.getName().getIdentifier().equals(m.getName().getIdentifier())
          && erased(types(old)).equals(erased(types(m)))
          && (isStub(old) || old.isConstructor() && Generated.onlyAssignsParameters(old))) {
        return old;
      }
      if (d instanceof FieldDeclaration f
          && o instanceof FieldDeclaration old
          && old.fragments().size() == 1
          && names(old).equals(names(f))
          && ((VariableDeclarationFragment) old.fragments().get(0)).getInitializer() == null
          && text.substring(end(old) + 1, Lines.end(text, end(old)))
              .strip()
              .equals(Stubs.FIELD_MARK)) {
        return old;
      }
    }
    return null;
  }

  /** {@code types} without their type arguments: {@code Map<K, List<V>>} as {@code Map}. */
  private static List<String> erased(List<String> types) {
    List<String> out = new ArrayList<>();
    for (String t : types) {
      String e = t;
      for (String was = null; !e.equals(was); ) {
        was = e;
        e = e.replaceAll("<[^<>]*>", "");
      }
      out.add(e);
    }
    return out;
  }

  static boolean isStub(MethodDeclaration m) {
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

  /** {@code content} parsed as members of a type, or refused, naming the line where it breaks. */
  private static List<BodyDeclaration> members(String content) {
    ASTNode node = Jdt.parseMembers(content);
    int[] broken = {-1};
    node.accept(
        new ASTVisitor() {
          @Override
          public void preVisit(ASTNode n) {
            if ((n.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0
                && (broken[0] < 0 || n.getStartPosition() > broken[0])) {
              broken[0] = n.getStartPosition();
            }
          }
        });
    List<BodyDeclaration> members = new ArrayList<>();
    if (node instanceof TypeDeclaration holder) {
      for (Object o : holder.bodyDeclarations()) {
        members.add((BodyDeclaration) o);
      }
    }
    if (broken[0] >= 0 || members.isEmpty()) {
      // The line of the first syntax error, in content wrapped as a class body (one line before
      // it).
      int line = broken[0] >= 0 ? Lines.number(content, broken[0]) : 0;
      for (IProblem p : Jdt.parseSyntax("class X {\n" + content + "\n}").getProblems()) {
        if (p.isError()) {
          line = Math.max(1, Math.min(p.getSourceLineNumber() - 1, (int) content.lines().count()));
          break;
        }
      }
      String where =
          line == 0
              ? ""
              : " from content line "
                  + line
                  + " ("
                  + content.lines().skip(line - 1).findFirst().orElse("").strip()
                  + ")";
      throw new Refusal(
          "content does not parse as members of a type (methods, fields, constructors, nested"
              + " types)"
              + where
              + ". Send whole declarations, without a package line or imports.");
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
    int start = Lines.start(content, d.getStartPosition());
    int end = Lines.end(content, end(d));
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
   * is the file's, else that of the {@code content} being added, else 2 (as the tools that move
   * members write them).
   */
  private static String memberIndent(String text, AbstractTypeDeclaration type, String content) {
    if (!type.bodyDeclarations().isEmpty()) {
      BodyDeclaration first = (BodyDeclaration) type.bodyDeclarations().get(0);
      int at = Lines.start(text, first.getStartPosition());
      String lead = text.substring(at, first.getStartPosition());
      if (lead.isBlank()) {
        return lead;
      }
    }
    int at = Lines.start(text, type.getStartPosition());
    String typeIndent = text.substring(at, at + leading(text, at));
    int step = step(text) > 0 ? step(text) : step(content) > 0 ? step(content) : 2;
    return typeIndent + " ".repeat(step);
  }

  private static int leading(String text, int at) {
    int n = 0;
    while (at + n < text.length() && (text.charAt(at + n) == ' ' || text.charAt(at + n) == '\t')) {
      n++;
    }
    return n;
  }

  /**
   * The smallest nonzero indentation of a code line in {@code text}, or 0 when nothing is indented.
   * A comment's continuation line ({@code " * text"}) is aligned to its star, not to a step.
   */
  static int step(String text) {
    int step = Integer.MAX_VALUE;
    for (String line : text.split("\n")) {
      String code = line.stripLeading();
      int n = line.length() - code.length();
      if (n > 0 && !code.isEmpty() && !code.startsWith("*")) {
        step = Math.min(step, n);
      }
    }
    return step == Integer.MAX_VALUE ? 0 : step;
  }

  /**
   * "lines a-b" (or "line a"): where {@code length} characters at {@code at} in {@code text} are,
   * moved by {@code lineShift} lines.
   */
  private static String range(CharSequence text, int at, int length, int lineShift) {
    int first = Lines.number(text, at) + lineShift;
    int last = Lines.number(text, at + Math.max(length - 1, 0)) + lineShift;
    return Lines.range(first, last);
  }

  /** The offset of the last character of {@code node}. */
  private static int end(ASTNode node) {
    return node.getStartPosition() + node.getLength() - 1;
  }

  /**
   * How a name tells {@code m} apart from the other methods of its type: its written parameter
   * types ({@code raise(T)}), or, when another method there is written alike, its erased ones
   * ({@code raise(Throwable)}), which {@link #find} also takes.
   */
  static String methodLabel(MethodDeclaration m) {
    String written = writtenLabel(m);
    if (m.getParent() instanceof AbstractTypeDeclaration t) {
      for (Object o : t.bodyDeclarations()) {
        if (o != m && o instanceof MethodDeclaration other && writtenLabel(other).equals(written)) {
          return m.getName().getIdentifier() + "(" + String.join(", ", erasedTypes(m)) + ")";
        }
      }
    }
    return written;
  }

  /**
   * {@code m}'s parameter types erased, read from the syntax: a type variable of the method or of a
   * type around it becomes the erasure of its first bound, or {@code Object}, and type arguments
   * are dropped ({@code Throwable} for {@code T} of {@code <T extends Throwable>}).
   */
  static List<String> erasedTypes(MethodDeclaration m) {
    List<String> types = new ArrayList<>();
    for (Object o : m.parameters()) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) o;
      types.add(erasure(p.getType(), m) + (p.isVarargs() ? "..." : ""));
    }
    return types;
  }

  private static String erasure(Type type, ASTNode at) {
    if (type instanceof ArrayType a) {
      return erasure(a.getElementType(), at) + "[]".repeat(a.getDimensions());
    }
    if (type instanceof ParameterizedType p) {
      return erasure(p.getType(), at);
    }
    if (type instanceof SimpleType s && s.getName().isSimpleName()) {
      TypeParameter v = typeVariable(s.getName().getFullyQualifiedName(), at);
      if (v != null) {
        return v.typeBounds().isEmpty()
            ? "Object"
            : erasure((Type) v.typeBounds().get(0), v.getParent());
      }
    }
    return erased(List.of(type.toString().replace(" ", ""))).get(0);
  }

  /** The type parameter {@code name} declared by the method or type nearest around {@code at}. */
  private static TypeParameter typeVariable(String name, ASTNode at) {
    for (ASTNode n = at; n != null; n = n.getParent()) {
      List<?> declared =
          switch (n) {
            case MethodDeclaration m -> m.typeParameters();
            case TypeDeclaration t -> t.typeParameters();
            case RecordDeclaration r -> r.typeParameters();
            default -> List.of();
          };
      for (Object o : declared) {
        if (((TypeParameter) o).getName().getIdentifier().equals(name)) {
          return (TypeParameter) o;
        }
      }
    }
    return null;
  }

  /**
   * The methods of {@code named} that {@code params} picks: all when it is null, those whose
   * written parameter types are {@code params}, or else those whose erased ones are ({@code
   * raise(Throwable)} for {@code <T extends Throwable> T raise(T)}; see {@link #methodLabel}).
   */
  static List<MethodDeclaration> overloads(List<MethodDeclaration> named, List<String> params) {
    List<MethodDeclaration> methods = new ArrayList<>();
    for (MethodDeclaration m : named) {
      if (params == null || types(m).equals(params)) {
        methods.add(m);
      }
    }
    if (methods.isEmpty() && params != null) {
      for (MethodDeclaration m : named) {
        if (erasedTypes(m).equals(params)) {
          methods.add(m);
        }
      }
    }
    return methods;
  }

  /** {@code m(int)} for a method: its parameter types as written, as {@link #find} reads them. */
  static String writtenLabel(MethodDeclaration m) {
    return m.getName().getIdentifier() + "(" + String.join(", ", Declarations.types(m)) + ")";
  }
}
