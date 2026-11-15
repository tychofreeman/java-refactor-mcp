package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

/**
 * A method the signature tools name by {@code line} (and {@code column}) of a file, in place of
 * {@code name}: the method declared on the line, or the one project method the line's calls call.
 */
final class ByLine {

  private ByLine() {}

  /** A declaration or call on the line, where it starts, and the method. */
  private record Hit(ASTNode at, IMethodBinding method, boolean declaration) {}

  /**
   * {@code args} as the tools read them: unchanged when they give {@code name}; else, from {@code
   * line}, the file that declares the method found there and the method's name in it.
   */
  static Args named(SourceProject project, Args args) throws Exception {
    if (args.has("name")) {
      return args;
    }
    if (!args.has("line")) {
      throw new Refusal(
          args.tool()
              + " needs name (Type.method) or line (of the method's declaration or a call of it).");
    }
    String rel = args.required("file");
    int line = args.integer("line");
    Integer column = args.integer("column");
    Opened opened = Opened.of(project, rel);
    CompilationUnit unit = opened.unit();
    int lineStart = unit.getPosition(line, 0);
    List<Hit> hits = new ArrayList<>();
    for (MethodDeclaration d : Nodes.all(unit, MethodDeclaration.class)) {
      hits.add(new Hit(d.getName(), d.resolveBinding(), true));
    }
    for (MethodInvocation c : Nodes.all(unit, MethodInvocation.class)) {
      hits.add(new Hit(c.getName(), c.resolveMethodBinding(), false));
    }
    for (SuperMethodInvocation c : Nodes.all(unit, SuperMethodInvocation.class)) {
      hits.add(new Hit(c.getName(), c.resolveMethodBinding(), false));
    }
    for (ClassInstanceCreation c : Nodes.all(unit, ClassInstanceCreation.class)) {
      hits.add(new Hit(c.getType(), Calls.binding(c), false));
    }
    for (ConstructorInvocation c : Nodes.all(unit, ConstructorInvocation.class)) {
      hits.add(new Hit(c, c.resolveConstructorBinding(), false));
    }
    for (SuperConstructorInvocation c : Nodes.all(unit, SuperConstructorInvocation.class)) {
      hits.add(new Hit(c, c.resolveConstructorBinding(), false));
    }
    Map<String, List<Hit>> methods = new LinkedHashMap<>();
    List<Hit> declared = new ArrayList<>();
    for (Hit h : hits) {
      int start = h.at().getStartPosition();
      if (h.method() == null
          || unit.getLineNumber(start) != line
          || (column != null && start - lineStart + 1 != column)
          || TypeSwitch.fileOf(project, Names.outermost(h.method().getDeclaringClass())) == null) {
        continue;
      }
      if (h.declaration()) {
        declared.add(h);
      }
      methods
          .computeIfAbsent(h.method().getMethodDeclaration().getKey(), k -> new ArrayList<>())
          .add(h);
    }
    String where = "line " + line + (column == null ? "" : ", column " + column) + " of " + rel;
    if (declared.size() > 1) {
      List<String> each = new ArrayList<>();
      for (Hit h : declared) {
        each.add(
            Names.methodLabel(h.method())
                + " at column "
                + Nodes.columns(List.of(h.at()), lineStart));
      }
      throw new Refusal(
          where
              + " declares "
              + declared.size()
              + " methods: "
              + String.join("; ", each)
              + ". Pass column, or name.");
    }
    if (declared.isEmpty() && methods.size() != 1) {
      if (methods.isEmpty()) {
        throw new Refusal(
            where
                + " declares or calls no method of the project. Give the line of the declaration"
                + " or of a call, or name the method.");
      }
      List<String> each = new ArrayList<>();
      for (List<Hit> same : methods.values()) {
        List<ASTNode> at = same.stream().map(Hit::at).toList();
        each.add(
            Names.methodLabel(same.get(0).method())
                + (at.size() == 1 ? " at column " : " at columns ")
                + Nodes.columns(at, lineStart));
      }
      throw new Refusal(
          where
              + " calls "
              + methods.size()
              + " methods: "
              + String.join("; ", each)
              + ". Pass column, or name.");
    }
    IMethodBinding method =
        (declared.isEmpty() ? methods.values().iterator().next().get(0) : declared.get(0))
            .method()
            .getMethodDeclaration();
    Path file = TypeSwitch.fileOf(project, Names.outermost(method.getDeclaringClass()));
    String home = project.relative(file);
    for (MethodDeclaration d :
        Nodes.all(Opened.of(project, home).unit(), MethodDeclaration.class)) {
      IMethodBinding b = d.resolveBinding();
      if (b != null && b.getKey().equals(method.getKey())) {
        return args.with("file", home)
            .with(
                "name",
                Names.typeLabel(method.getDeclaringClass()) + "." + Declarations.methodLabel(d));
      }
    }
    throw new Refusal(
        Names.methodLabel(method)
            + " is not written in "
            + home
            + " (a default constructor): declare it first.");
  }

  /**
   * {@code args} for replace_declaration with {@code line}: the file that declares what {@code
   * name} names on that line of {@code file} (its declaration or a use), and its name there, Type
   * or Type.member, as {@link Declarations#find} reads it.
   */
  static Args declaration(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    int line = args.integer("line");
    Integer column = args.integer("column");
    String name = args.required("name");
    String bare = name.contains("(") ? name.substring(0, name.indexOf('(')) : name;
    String simple = bare.substring(bare.lastIndexOf('.') + 1);
    Opened opened = Opened.of(project, rel);
    List<SimpleName> named = new ArrayList<>();
    for (SimpleName n : opened.onLine(line, SimpleName.class)) {
      if (n.getIdentifier().equals(simple)) {
        named.add(n);
      }
    }
    String where = "line " + line + " of " + rel;
    if (named.isEmpty()) {
      throw new Refusal(
          "no name " + simple + " on " + where + ". Give the line of its declaration or of a use.");
    }
    named = opened.atColumn(named, line, column, simple);
    Map<String, SimpleName> byKey = new LinkedHashMap<>();
    for (SimpleName n : named) {
      IBinding b = n.resolveBinding();
      byKey.putIfAbsent(b == null ? "" : b.getKey(), n);
    }
    if (byKey.size() > 1) {
      throw opened.several(List.copyOf(byKey.values()), line, "names " + simple);
    }
    IBinding b = named.get(0).resolveBinding();
    ITypeBinding owner;
    String member;
    if (b instanceof ITypeBinding t) {
      owner = t.getTypeDeclaration();
      member = null;
    } else if (b instanceof IMethodBinding m) {
      IMethodBinding declared = m.getMethodDeclaration();
      owner = declared.getDeclaringClass();
      long same =
          java.util.Arrays.stream(owner.getDeclaredMethods())
              .filter(o -> o.getName().equals(declared.getName()))
              .count();
      member = same > 1 ? Names.methodLabel(declared) : declared.getName();
    } else if (b instanceof IVariableBinding v && v.isField()) {
      owner = v.getVariableDeclaration().getDeclaringClass();
      member = v.getName();
    } else {
      throw new Refusal(
          simple
              + " on "
              + where
              + " is a parameter or local variable, not a type or member. Change it with"
              + " replace_statements or replace_text.");
    }
    Path home = TypeSwitch.fileOf(project, Names.outermost(owner));
    String type = Names.typeLabel(owner);
    if (home == null) {
      throw new Refusal(type + " is not declared in the project's sources.");
    }
    return args.with("file", project.relative(home).toString())
        .with("name", member == null ? type : type + "." + member);
  }
}
