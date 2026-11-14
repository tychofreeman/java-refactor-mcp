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
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
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
      hits.add(new Hit(c.getType(), c.resolveConstructorBinding(), false));
    }
    for (ConstructorInvocation c : Nodes.all(unit, ConstructorInvocation.class)) {
      hits.add(new Hit(c, c.resolveConstructorBinding(), false));
    }
    for (SuperConstructorInvocation c : Nodes.all(unit, SuperConstructorInvocation.class)) {
      hits.add(new Hit(c, c.resolveConstructorBinding(), false));
    }
    Map<String, List<Hit>> methods = new LinkedHashMap<>();
    Hit declared = null;
    for (Hit h : hits) {
      int start = h.at().getStartPosition();
      if (h.method() == null
          || unit.getLineNumber(start) != line
          || (column != null && start - lineStart + 1 != column)
          || TypeSwitch.fileOf(project, Names.outermost(h.method().getDeclaringClass())) == null) {
        continue;
      }
      if (h.declaration()) {
        declared = h;
      }
      methods
          .computeIfAbsent(h.method().getMethodDeclaration().getKey(), k -> new ArrayList<>())
          .add(h);
    }
    if (declared == null && methods.size() != 1) {
      String where = "line " + line + (column == null ? "" : ", column " + column) + " of " + rel;
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
            Signatures.label(same.get(0).method())
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
        (declared != null ? declared : methods.values().iterator().next().get(0))
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
        Signatures.label(method)
            + " is not written in "
            + home
            + " (a default constructor): declare it first.");
  }
}
