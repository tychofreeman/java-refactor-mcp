package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

/**
 * The check that a change keeps what every call runs: each method call, {@code new}, {@code
 * this(...)}, {@code super(...)} and method reference in the checked files must resolve, after the
 * change, to the declaration it resolved to before. The gate cannot see this: a parameter widened
 * to a wildcard, or a type that became a type variable, can make a call pick another overload and
 * still compile. The targets are read before the change is written ({@link #of}) and required after
 * it ({@link #require}), as {@code Rename.checkMeanings} is; the refusal puts the files back. A
 * virtual call that now names a method its old target overrides ({@code value.equals(x)} once
 * {@code value} is a {@code T}) runs the same body, and passes.
 *
 * <p>A declaration is known by where it stands (its file and its place among the file's methods),
 * not by its binding key, which holds the signature these changes alter. So the change may add or
 * remove no call and no method in the checked files.
 */
final class CallTargets {

  /**
   * What a call runs: the declaration's place, its label, the call's line and kind, and, for a
   * virtual call, the places of the methods the declaration overrides.
   */
  private record Target(String id, String label, int line, String kind, List<String> overrides) {}

  /** A method that overrides another: its label, what it overrides, and where it is declared. */
  private record Overriding(String label, String overridden, Path file, int line) {}

  /** Each checked file's calls in source order; a null id for one that did not resolve. */
  private final Map<Path, List<Target>> before;

  /** Each override among the checked files' methods, by "its place > the overridden's place". */
  private final Map<String, Overriding> overrides;

  private CallTargets(Map<Path, List<Target>> before, Map<String, Overriding> overrides) {
    this.before = before;
    this.overrides = overrides;
  }

  /**
   * What the calls in {@code units}, parsed together from the project's files, run now, and which
   * methods of them override which.
   */
  static CallTargets of(Collection<CompilationUnit> units) {
    return new CallTargets(targets(units), overrides(units));
  }

  /**
   * Refuses when a call in the checked files, as they now are on disk, runs another declaration
   * than it did: "{@code what} would make the call at line 8 of Use.java run ..., not ... as it
   * does now." A call that does not resolve on either side is left to the gate, which a batch may
   * have deferred.
   */
  void require(SourceProject project, String what) throws IOException {
    require(project, Jdt.parseAll(project, new ArrayList<>(before.keySet())).values(), what);
  }

  private static Map<Path, List<Target>> targets(Collection<CompilationUnit> units) {
    Map<String, String> places = places(units);
    Map<Path, List<Target>> out = new LinkedHashMap<>();
    for (CompilationUnit unit : units) {
      List<Target> calls = new ArrayList<>();
      for (ASTNode n : Nodes.all(unit, ASTNode.class)) {
        String kind = kind(n);
        if (kind == null) {
          continue;
        }
        IMethodBinding b = binding(n);
        int line = unit.getLineNumber(n.getStartPosition());
        if (b == null || b.isRecovered()) {
          calls.add(new Target(null, null, line, kind, List.of()));
        } else {
          IMethodBinding d = b.getMethodDeclaration();
          List<String> overrides = new ArrayList<>();
          if (n instanceof MethodInvocation && !Modifier.isStatic(d.getModifiers())) {
            for (IMethodBinding o : Hierarchy.overridden(d)) {
              overrides.add(place(o.getMethodDeclaration(), places));
            }
          }
          calls.add(new Target(place(d, places), Signatures.label(d), line, kind, overrides));
        }
      }
      out.put(Jdt.fileOf(unit), calls);
    }
    return out;
  }

  /**
   * Where each method and type of {@code units} stands: its file and its number among the file's.
   */
  private static Map<String, String> places(Collection<CompilationUnit> units) {
    Map<String, String> places = new HashMap<>();
    for (CompilationUnit unit : units) {
      int k = 0;
      for (MethodDeclaration m : Nodes.all(unit, MethodDeclaration.class)) {
        IMethodBinding b = m.resolveBinding();
        if (b != null) {
          places.put(b.getKey(), Jdt.fileOf(unit) + "#" + k);
        }
        k++;
      }
      k = 0;
      for (AbstractTypeDeclaration t : Nodes.all(unit, AbstractTypeDeclaration.class)) {
        ITypeBinding b = t.resolveBinding();
        if (b != null) {
          places.put(b.getKey(), Jdt.fileOf(unit) + "#type " + k);
        }
        k++;
      }
    }
    return places;
  }

  /**
   * Where {@code d} stands in the parsed files; for a constructor nothing declares, its type's
   * place; for a declaration outside them, which the change does not edit, its key. An anonymous
   * class's constructor is its own each time, and its key holds its supertype: it is not compared.
   */
  private static String place(IMethodBinding d, Map<String, String> places) {
    if (d.getDeclaringClass().isAnonymous()) {
      return "anonymous";
    }
    String at = places.get(d.getKey());
    if (at == null && d.isDefaultConstructor()) {
      String type = places.get(d.getDeclaringClass().getTypeDeclaration().getKey());
      at = type == null ? null : type + " default constructor";
    }
    return at == null ? d.getKey() : at;
  }

  /** The kind of call {@code n} is, with its name; null when it is none. */
  private static String kind(ASTNode n) {
    return switch (n) {
      case MethodInvocation m -> "call " + m.getName().getIdentifier();
      case SuperMethodInvocation m -> "super call " + m.getName().getIdentifier();
      case ClassInstanceCreation c -> "new";
      case EnumConstantDeclaration c -> "new constant";
      case ConstructorInvocation c -> "new this";
      case SuperConstructorInvocation c -> "new super";
      case MethodReference r -> "reference";
      default -> null;
    };
  }

  private static IMethodBinding binding(ASTNode n) {
    return switch (n) {
      case MethodInvocation m -> m.resolveMethodBinding();
      case SuperMethodInvocation m -> m.resolveMethodBinding();
      case ClassInstanceCreation c -> c.resolveConstructorBinding();
      case EnumConstantDeclaration c -> c.resolveConstructorBinding();
      case ConstructorInvocation c -> c.resolveConstructorBinding();
      case SuperConstructorInvocation c -> c.resolveConstructorBinding();
      case MethodReference r -> r.resolveMethodBinding();
      default -> null;
    };
  }

  /**
   * As {@link #require(SourceProject, String)}, with the checked files already parsed again, in one
   * parse, as they now are on disk: {@code units}. A method that stops or starts overriding another
   * is refused too, with {@code fix} as the way out.
   */
  void require(SourceProject project, Collection<CompilationUnit> units, String what, String fix)
      throws IOException {
    Map<Path, List<Target>> after = targets(units);
    for (Map.Entry<Path, List<Target>> e : before.entrySet()) {
      List<Target> was = e.getValue();
      List<Target> now = after.get(e.getKey());
      if (now == null || now.size() != was.size()) {
        throw new IllegalStateException(
            "the calls of "
                + e.getKey()
                + " changed in number, so their targets cannot be compared");
      }
      for (int k = 0; k < was.size(); k++) {
        Target a = was.get(k);
        Target b = now.get(k);
        if (!a.kind().equals(b.kind())) {
          throw new IllegalStateException(
              "the calls of " + e.getKey() + " changed, so their targets cannot be compared");
        }
        // A virtual call that now names a method its old target overrides runs the same body.
        if (a.id() != null
            && b.id() != null
            && !a.id().equals(b.id())
            && !a.overrides().contains(b.id())) {
          throw new Refusal(
              what
                  + " would make the call at line "
                  + a.line()
                  + " of "
                  + project.relative(e.getKey())
                  + " run "
                  + b.label()
                  + ", not "
                  + a.label()
                  + " as it does now. "
                  + (a.kind().startsWith("new")
                      ? "Change the parameters of one of the two constructors first."
                      : "Rename one of the two first."));
        }
      }
    }
    Map<String, Overriding> now = overrides(units);
    for (Map.Entry<String, Overriding> e : overrides.entrySet()) {
      if (!now.containsKey(e.getKey())) {
        throw new Refusal(overriding(project, what, e.getValue(), "no longer override", fix));
      }
    }
    for (Map.Entry<String, Overriding> e : now.entrySet()) {
      if (!overrides.containsKey(e.getKey())) {
        throw new Refusal(overriding(project, what, e.getValue(), "override", fix));
      }
    }
  }

  /**
   * As {@link #require(SourceProject, Collection, String, String)}, a change of override refused
   * with the fix of changing one of the two signatures.
   */
  void require(SourceProject project, Collection<CompilationUnit> units, String what)
      throws IOException {
    require(project, units, what, "Change the parameters of one of the two first.");
  }

  /**
   * Each method of {@code units} that overrides another, by "its place > the overridden's place"; a
   * method of a library is known by its key.
   */
  private static Map<String, Overriding> overrides(Collection<CompilationUnit> units) {
    Map<String, String> places = places(units);
    Map<String, Overriding> out = new LinkedHashMap<>();
    for (CompilationUnit unit : units) {
      for (MethodDeclaration m : Nodes.all(unit, MethodDeclaration.class)) {
        IMethodBinding b = m.resolveBinding();
        if (b == null || b.isConstructor() || Modifier.isStatic(b.getModifiers())) {
          continue;
        }
        for (IMethodBinding o : Hierarchy.overridden(b)) {
          IMethodBinding d = o.getMethodDeclaration();
          out.put(
              places.get(b.getKey()) + " > " + place(d, places),
              new Overriding(
                  Signatures.label(b),
                  Signatures.label(d),
                  Jdt.fileOf(unit),
                  unit.getLineNumber(m.getName().getStartPosition())));
        }
      }
    }
    return out;
  }

  /** The refusal for a method that would {@code verb} (start or stop overriding) another. */
  private static String overriding(
      SourceProject project, String what, Overriding o, String verb, String fix) {
    return what
        + " would make "
        + o.label()
        + " (line "
        + o.line()
        + " of "
        + project.relative(o.file())
        + ") "
        + verb
        + " "
        + o.overridden()
        + ". "
        + fix;
  }
}
