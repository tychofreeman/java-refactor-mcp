package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Renames a declaration and every name in the project that refers to it, Javadoc included: a local
 * variable or parameter within its method, a field, a method with every declaration it overrides or
 * that overrides it, a type with its constructors (and its file, for a top-level type). The edit is
 * applied, then the project is type-checked; if the edit adds compile errors it is rolled back and
 * refused.
 */
public final class Rename {

  private Rename() {}

  /**
   * @param relFile path relative to the project root
   * @param line 1-based line of any use of the name, or its declaration
   * @param name the current name: {@code count}, {@code Type.count} for a member of Type, or {@code
   *     local count} for a local variable or parameter
   * @param column 1-based column where the name starts; 0 when not given
   * @return what was renamed, and the lines of each file it changed
   */
  public static Outcome run(
      SourceProject project, String relFile, int line, String name, Integer column, String newName)
      throws IOException {
    if (column != null && column < 1) {
      throw new Refusal(
          "column starts at 1; got "
              + column
              + ". Give the 1-based column of the name, or leave column out.");
    }
    Path file = project.root().resolve(relFile).normalize();
    if (!file.startsWith(project.root())
        || !Files.isRegularFile(file)
        || !relFile.endsWith(".java")) {
      throw new Refusal(
          "'"
              + relFile
              + "' is not a Java file in this project. Pass a path relative to the project root.");
    }
    Precondition.require(Precondition.identifier(newName, "the new name"));

    Query query = Query.of(name);
    String source = Files.readString(file);
    CompilationUnit cu = Jdt.parse(project, file, source);
    SimpleName at =
        column != null
            ? atColumn(cu, source, line, query.simple(), column)
            : byName(cu, source, relFile, line, query);
    String oldName = at.getIdentifier();

    IBinding binding = declarationOf(at.resolveBinding());
    if (binding instanceof IMethodBinding constructor && constructor.isConstructor()) {
      binding = constructor.getDeclaringClass().getTypeDeclaration();
    }
    refuseLibrary(project, binding);
    if (binding instanceof IVariableBinding field && field.isField()) {
      return renameField(project, field, newName);
    }
    if (binding instanceof IMethodBinding method) {
      return renameMethod(project, method, newName);
    }
    if (binding instanceof ITypeBinding type) {
      return renameType(project, type, newName);
    }
    if (!(binding instanceof IVariableBinding variable)) {
      throw new Refusal(
          "'"
              + oldName
              + "' is not a variable, field, method or type. rename renames local variables, "
              + "parameters, fields, methods and types.");
    }

    ASTNode scope = scopeOf(cu, variable, oldName);
    Collected found = collect(scope, variable, newName);
    if (found.clash() != null) {
      throw new Refusal(
          "renaming '"
              + oldName
              + "' to '"
              + newName
              + "' would clash: '"
              + newName
              + "' is already a variable used in this method (line "
              + cu.getLineNumber(found.clash().getStartPosition())
              + "). Choose another name.");
    }

    Stubs.Declared stubs =
        gate(project, Map.of(file, apply(source, found.occurrences(), newName)), oldName, newName);
    return new Outcome(
        "renamed %s to %s in %s: %s, %s"
            .formatted(
                oldName,
                newName,
                project.relative(file),
                Names.plural(found.occurrences().size(), "occurrence"),
                Lines.list(lineNumbers(cu, found.occurrences()))),
        stubs);
  }

  /** The name {@code name} that starts at {@code line}, {@code column}. */
  private static SimpleName atColumn(
      CompilationUnit cu, String source, int line, String name, int column) {
    ASTNode covering = NodeFinder.perform(cu, Positions.offset(source, line, column), 0);
    if (!(covering instanceof SimpleName at) || !at.getIdentifier().equals(name)) {
      throw new Refusal(
          "line "
              + line
              + ", column "
              + column
              + " is not on '"
              + name
              + "'. Put the position on the name, at a use or at its declaration,"
              + " or leave column out.");
    }
    return at;
  }

  /**
   * The first occurrence on {@code line} of the declaration {@code query} names, when every
   * occurrence of the name there that the query accepts is the same declaration.
   */
  private static SimpleName byName(
      CompilationUnit cu, String source, String relFile, int line, Query query) {
    List<String> lines = source.lines().toList();
    if (line < 1 || line > lines.size()) {
      throw new Refusal(
          "line " + line + " is outside " + relFile + ", which has " + lines.size() + " lines.");
    }
    String name = query.simple();
    List<SimpleName> onLine = new ArrayList<>();
    cu.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(SimpleName node) {
            if (node.getIdentifier().equals(name)
                && cu.getLineNumber(node.getStartPosition()) == line) {
              onLine.add(node);
            }
            return true;
          }
        });
    onLine.sort(Comparator.comparingInt(ASTNode::getStartPosition));

    List<SimpleName> firstOfEach = new ArrayList<>();
    List<IBinding> declarations = new ArrayList<>();
    for (SimpleName node : onLine) {
      IBinding b = declarationOf(node.resolveBinding());
      if (b != null && declarations.stream().noneMatch(b::isEqualTo)) {
        declarations.add(b);
        firstOfEach.add(node);
      }
    }
    List<SimpleName> accepted =
        firstOfEach.stream().filter(n -> query.accepts(declarationOf(n.resolveBinding()))).toList();
    if (accepted.size() == 1) {
      return accepted.get(0);
    }
    if (accepted.size() > 1) {
      throw twoMeanings(cu, name, line, accepted);
    }
    if (onLine.isEmpty()) {
      throw new Refusal(
          "'"
              + name
              + "' is not on line "
              + line
              + " of "
              + relFile
              + ". That line is: "
              + lines.get(line - 1).strip()
              + "\nGive a line where it is declared or used.");
    }
    if (firstOfEach.isEmpty()) {
      throw new Refusal(
          "'" + name + "' on line " + line + " is not a variable, field, method or type.");
    }
    List<String> there = new ArrayList<>();
    for (SimpleName node : firstOfEach) {
      there.add(describe(declarationOf(node.resolveBinding())));
    }
    throw new Refusal(
        "'"
            + name
            + "' on line "
            + line
            + " is "
            + Names.listed(there)
            + ", not "
            + query.text()
            + ". Pass name "
            + pickList(firstOfEach)
            + ".");
  }

  /**
   * The {@code name} argument: a simple name, with {@code Type.} in front for a member of Type, or
   * {@code local } for a local variable or parameter.
   */
  private record Query(String simple, String type, boolean local) {
    static Query of(String name) {
      String n = name.strip();
      if (n.startsWith("local ")) {
        return new Query(n.substring("local ".length()).strip(), null, true);
      }
      int dot = n.lastIndexOf('.');
      if (dot > 0) {
        return new Query(n.substring(dot + 1), n.substring(0, dot), false);
      }
      return new Query(n, null, false);
    }

    /** Whether {@code declaration} is one this query can name. */
    boolean accepts(IBinding declaration) {
      if (local) {
        return declaration instanceof IVariableBinding v && !v.isField();
      }
      if (type == null) {
        return true;
      }
      ITypeBinding owner = owner(declaration);
      if (owner == null) {
        return false;
      }
      String label = Names.typeLabel(owner);
      return label.equals(type)
          || label.endsWith("." + type)
          || owner.getErasure().getQualifiedName().equals(type);
    }

    String text() {
      return local ? "a local" : "a member of " + type;
    }
  }

  /** The type a field or method is declared in; null for anything else. */
  private static ITypeBinding owner(IBinding declaration) {
    if (declaration instanceof IVariableBinding v && v.isField()) {
      return v.getDeclaringClass();
    }
    if (declaration instanceof IMethodBinding m) {
      return m.getDeclaringClass();
    }
    return null;
  }

  /**
   * The declaration a use's binding stands for (a generic member or type as declared, not as used);
   * null for what rename does not rename (packages, primitive types, unresolved names).
   */
  private static IBinding declarationOf(IBinding binding) {
    if (binding instanceof IVariableBinding v) {
      return v.getVariableDeclaration();
    }
    if (binding instanceof IMethodBinding m) {
      return m.getMethodDeclaration();
    }
    if (binding instanceof ITypeBinding t && !t.isPrimitive() && !t.isNullType()) {
      return t.getTypeDeclaration();
    }
    return null;
  }

  /** "the field Counter.count", "the parameter count", "the method Cart.add", "the type Cart". */
  private static String describe(IBinding declaration) {
    if (declaration instanceof IVariableBinding v) {
      if (v.isField()) {
        return "the field " + Names.typeLabel(v.getDeclaringClass()) + "." + v.getName();
      }
      return (v.isParameter() ? "the parameter " : "the local ") + v.getName();
    }
    if (declaration instanceof IMethodBinding m) {
      return "the method " + Names.typeLabel(m.getDeclaringClass()) + "." + m.getName();
    }
    return "the type " + Names.typeLabel((ITypeBinding) declaration);
  }

  /** The {@code name} argument that picks {@code declaration}: Counter.count, or local count. */
  private static String pick(IBinding declaration) {
    ITypeBinding owner = owner(declaration);
    if (owner != null) {
      return Names.typeLabel(owner) + "." + declaration.getName();
    }
    if (declaration instanceof ITypeBinding t) {
      return Names.typeLabel(t);
    }
    return "local " + declaration.getName();
  }

  /** {@code "A" or "B"}: the name arguments that pick each of {@code nodes}. */
  private static String pickList(List<SimpleName> nodes) {
    List<String> picks = new ArrayList<>();
    for (SimpleName node : nodes) {
      picks.add('"' + pick(declarationOf(node.resolveBinding())) + '"');
    }
    return String.join(" or ", picks);
  }

  /**
   * The refusal for a name that means several declarations on the line: each, with its column, and
   * the name arguments that pick one when they differ.
   */
  private static Refusal twoMeanings(
      CompilationUnit cu, String name, int line, List<SimpleName> nodes) {
    List<String> each = new ArrayList<>();
    java.util.Set<String> picks = new java.util.HashSet<>();
    for (SimpleName node : nodes) {
      IBinding declaration = declarationOf(node.resolveBinding());
      each.add(
          describe(declaration)
              + " (column "
              + (cu.getColumnNumber(node.getStartPosition()) + 1)
              + ")");
      picks.add(pick(declaration));
    }
    String pass =
        picks.size() == nodes.size()
            ? "Pass name " + pickList(nodes) + ", or column."
            : "Pass column.";
    return new Refusal(
        "'"
            + name
            + "' on line "
            + line
            + " names "
            + nodes.size()
            + " declarations: "
            + Names.listed(each)
            + ". "
            + pass);
  }

  /**
   * Renames a field and every use of it in the project, Javadoc references included. Refuses a new
   * name the type already has as a field, and one a local of that name would hide at an unqualified
   * use.
   */
  private static Outcome renameField(SourceProject project, IVariableBinding field, String newName)
      throws IOException {
    String oldName = field.getName();
    String owner = Names.typeLabel(field.getDeclaringClass());
    String key = field.getKey();
    List<String> clashKeys = new ArrayList<>();
    for (IVariableBinding other : field.getDeclaringClass().getDeclaredFields()) {
      if (other.getName().equals(newName)) {
        clashKeys.add(other.getKey());
      }
    }

    Map<Path, Map<Integer, String>> existing = meanings(project, newName);
    Written written =
        renameUses(
            project,
            Holding.of(project, oldName),
            oldName,
            newName,
            node ->
                node.resolveBinding() instanceof IVariableBinding v
                    && v.getVariableDeclaration().getKey().equals(key),
            (f, unit, uses) -> {
              for (String clashKey : clashKeys) {
                ASTNode clash = unit.findDeclaringNode(clashKey);
                if (clash != null) {
                  throw new Refusal(
                      owner
                          + " already has a field "
                          + newName
                          + " (line "
                          + unit.getLineNumber(clash.getStartPosition())
                          + "). Choose another name.");
                }
              }
              for (SimpleName use : uses) {
                VariableDeclaration local = localInScope(unit, use, newName);
                if (local != null) {
                  int at = unit.getLineNumber(use.getStartPosition());
                  throw new Refusal(
                      "renaming the field "
                          + owner
                          + "."
                          + oldName
                          + " to "
                          + newName
                          + " would change what line "
                          + at
                          + " of "
                          + project.relative(f)
                          + " means: line "
                          + at
                          + " would read the local "
                          + newName
                          + " declared at line "
                          + unit.getLineNumber(local.getStartPosition())
                          + ", not the field. Choose another name, or rename that local first.");
                }
              }
            },
            null,
            null);

    Stubs.Declared stubs = gate(project, written.texts(), oldName, newName);
    checkMeanings(project, written.renamedAt(), existing, oldName, newName);
    return summary(
        "renamed the field " + owner + "." + oldName + " to " + newName,
        written.lines(),
        written.occurrences(),
        stubs);
  }

  /**
   * Renames a method, every declaration it overrides or that overrides it, and every call and
   * reference of them in the project, Javadoc included. Refuses a family member that overrides a
   * library method, and a new name a declaring type already has with the same parameter types.
   */
  private static Outcome renameMethod(SourceProject project, IMethodBinding method, String newName)
      throws IOException {
    String oldName = method.getName();
    Holding holding = Holding.of(project, oldName);
    Set<String> keys =
        Signatures.family(project, holding.units().values(), method, "so it cannot be renamed.");

    TreeSet<String> others = new TreeSet<>();
    Map<Path, Map<Integer, String>> existing = meanings(project, newName);
    Written written =
        renameUses(
            project,
            holding,
            oldName,
            newName,
            node ->
                node.resolveBinding() instanceof IMethodBinding b
                    && keys.contains(b.getMethodDeclaration().getKey()),
            (f, unit, uses) -> {
              for (MethodDeclaration m : Nodes.all(unit, MethodDeclaration.class)) {
                IMethodBinding b = m.resolveBinding();
                if (b != null && keys.contains(b.getMethodDeclaration().getKey())) {
                  others.add(Names.methodLabel(b));
                  refuseMethodClash(unit, b, newName);
                }
              }
            },
            null,
            null);

    Stubs.Declared stubs = gate(project, written.texts(), oldName, newName);
    checkMeanings(project, written.renamedAt(), existing, oldName, newName);
    List<String> names = new ArrayList<>(List.of(Names.methodLabel(method)));
    others.remove(names.get(0));
    names.addAll(others);
    return summary(
        "renamed the method " + Names.listed(names) + " to " + newName,
        written.lines(),
        written.occurrences(),
        stubs);
  }

  /**
   * What each name {@code newName} means before the rename, by file and position: what an existing
   * use of the new name must still mean after it.
   */
  private static Map<Path, Map<Integer, String>> meanings(SourceProject project, String newName)
      throws IOException {
    Map<Path, Map<Integer, String>> out = new TreeMap<>();
    Holding holding = Holding.of(project, newName);
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      for (SimpleName n : Nodes.all(e.getValue(), SimpleName.class)) {
        String key = Bindings.declarationKey(n.resolveBinding());
        if (n.getIdentifier().equals(newName) && key != null) {
          out.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(n.getStartPosition(), key);
        }
      }
    }
    return out;
  }

  /**
   * Refuses, once the rename is written (the refusal puts the files back), when a name it wrote
   * means another declaration than a renamed one, or a name that was already {@code newName} means
   * something else now: a closer, inherited or nested declaration of the new name captured it, or
   * the renamed one did.
   */
  private static void checkMeanings(
      SourceProject project,
      Map<Path, List<Integer>> renamedAt,
      Map<Path, Map<Integer, String>> existing,
      String oldName,
      String newName)
      throws IOException {
    Set<Path> files = new TreeSet<>(renamedAt.keySet());
    files.addAll(existing.keySet());
    Map<Path, CompilationUnit> after = Jdt.parseAll(project, new ArrayList<>(files));
    Set<String> renamed = new HashSet<>();
    for (Map.Entry<Path, CompilationUnit> e : after.entrySet()) {
      List<Integer> at = renamedAt.getOrDefault(e.getKey(), List.of());
      List<TextEdit> edits = renames(at, oldName, newName);
      for (SimpleName n : Nodes.all(e.getValue(), SimpleName.class)) {
        // A constructor's and a record's name declare them too, though JDT's isDeclaration says
        // not.
        boolean declares =
            n.isDeclaration()
                || (n.getParent() instanceof MethodDeclaration d && d.getName() == n)
                || (n.getParent() instanceof AbstractTypeDeclaration t && t.getName() == n);
        if (declares
            && n.getIdentifier().equals(newName)
            && at.contains(NameCheck.before(edits, n.getStartPosition()))) {
          renamed.add(Bindings.declarationKey(n.resolveBinding()));
        }
      }
    }
    for (Map.Entry<Path, CompilationUnit> e : after.entrySet()) {
      CompilationUnit unit = e.getValue();
      List<Integer> at = renamedAt.getOrDefault(e.getKey(), List.of());
      List<TextEdit> edits = renames(at, oldName, newName);
      Map<Integer, String> was = existing.getOrDefault(e.getKey(), Map.of());
      for (SimpleName n : Nodes.all(unit, SimpleName.class)) {
        String key = Bindings.declarationKey(n.resolveBinding());
        if (!n.getIdentifier().equals(newName) || key == null) {
          continue;
        }
        int b = NameCheck.before(edits, n.getStartPosition());
        boolean wrote = at.contains(b);
        if ((wrote && !renamed.contains(key))
            || (!wrote && was.containsKey(b) && !was.get(b).equals(key))) {
          throw new Refusal(
              "renaming "
                  + oldName
                  + " to "
                  + newName
                  + " would change what "
                  + newName
                  + " at line "
                  + unit.getLineNumber(n.getStartPosition())
                  + " of "
                  + project.relative(e.getKey())
                  + " means: it would mean "
                  + describe(n.resolveBinding())
                  + (wrote ? ", not the renamed one" : ", not what it meant before")
                  + ". A declaration of that name in a class around it, a nested class or a"
                  + " subclass captures it. Choose another name.");
        }
      }
    }
  }

  /** The edits that renamed {@code oldName} to {@code newName} at each of {@code at}, in order. */
  private static List<TextEdit> renames(List<Integer> at, String oldName, String newName) {
    return at.stream().map(p -> new TextEdit(p, p + oldName.length(), newName)).toList();
  }

  /**
   * Renames a type and every reference to it in the project (imports, constructors, Javadoc), and a
   * top-level type's file with it. Refuses a type parameter, and a new name whose file exists or
   * that the enclosing type already has as a member type.
   */
  private static Outcome renameType(SourceProject project, ITypeBinding type, String newName)
      throws IOException {
    if (type.isTypeVariable()) {
      return renameTypeParameter(project, type, newName);
    }
    String oldName = type.getName();
    String key = type.getKey();
    Path from = null;
    Path to = null;
    if (type.getDeclaringClass() == null) {
      from = project.fileDeclaring(type.getQualifiedName());
      if (from != null) {
        to = from.resolveSibling(newName + ".java");
      }
      if (to != null && Files.exists(to)) {
        throw new Refusal(project.relative(to) + " already exists. Choose another name.");
      }
    } else {
      for (ITypeBinding member : type.getDeclaringClass().getDeclaredTypes()) {
        if (member.getName().equals(newName)) {
          throw new Refusal(
              Names.typeLabel(type.getDeclaringClass())
                  + " already has a type "
                  + newName
                  + ". Choose another name.");
        }
      }
    }

    Map<Path, Map<Integer, String>> existing = meanings(project, newName);
    Written written =
        renameUses(
            project,
            Holding.of(project, oldName),
            oldName,
            newName,
            node -> refersTo(node.resolveBinding(), key),
            (f, unit, uses) -> {},
            from,
            to);

    Stubs.Declared stubs = gate(project, written.texts(), oldName, newName);
    if (from != null && existing.containsKey(from)) {
      existing.put(to, existing.remove(from));
    }
    checkMeanings(project, written.renamedAt(), existing, oldName, newName);
    String what = "renamed the type " + type.getQualifiedName() + " to " + newName;
    if (from != null) {
      what += ", and " + project.relative(from) + " to " + project.relative(to) + ",";
    }
    return summary(what, written.lines(), written.occurrences(), stubs);
  }

  /**
   * Renames a type parameter in the class or method that declares it, Javadoc's {@code @param <T>}
   * included. Refuses a new name that the declaration already uses for a type: the parameter would
   * hide it.
   */
  private static Outcome renameTypeParameter(
      SourceProject project, ITypeBinding parameter, String newName) throws IOException {
    String oldName = parameter.getName();
    IBinding owner =
        parameter.getDeclaringMethod() != null
            ? parameter.getDeclaringMethod()
            : parameter.getDeclaringClass();
    ITypeBinding top =
        parameter.getDeclaringMethod() != null
            ? parameter.getDeclaringMethod().getDeclaringClass()
            : parameter.getDeclaringClass();
    top = Names.outermost(top);
    // The file that declares the top-level type, found by its binding: a second top-level class
    // of a file is not in the file its name implies.
    String topKey = top.getErasure().getTypeDeclaration().getKey();
    Path file = null;
    for (Map.Entry<Path, CompilationUnit> e :
        Holding.of(project, top.getName()).units().entrySet()) {
      if (e.getValue().findDeclaringNode(topKey) != null) {
        file = e.getKey();
      }
    }
    String rel = file == null ? top.getName() : project.relative(file).toString();
    if (file == null) {
      throw new Refusal("the file that declares " + oldName + " was not found in the project.");
    }
    String source = Files.readString(file);
    CompilationUnit cu = Jdt.parse(project, file, source);
    ASTNode scope =
        cu.findDeclaringNode(
            owner instanceof IMethodBinding m
                ? m.getMethodDeclaration().getKey()
                : ((ITypeBinding) owner).getTypeDeclaration().getKey());
    if (scope == null) {
      throw new Refusal("the declaration of " + oldName + " was not found in " + rel + ".");
    }
    String key = parameter.getKey();
    String ownerName =
        owner instanceof IMethodBinding m ? m.getName() + "()" : ((ITypeBinding) owner).getName();
    List<SimpleName> occurrences = new ArrayList<>();
    scope.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(SimpleName n) {
            IBinding b = n.resolveBinding();
            if (b instanceof ITypeBinding t && t.getKey().equals(key)) {
              occurrences.add(n);
            } else if (b == null
                && n.getParent() instanceof TagElement tag
                && TagElement.TAG_PARAM.equals(tag.getTagName())
                && n.getIdentifier().equals(oldName)) {
              occurrences.add(n);
            } else if (b instanceof ITypeBinding t && n.getIdentifier().equals(newName)) {
              throw new Refusal(
                  newName
                      + " is a type that "
                      + ownerName
                      + " already names (line "
                      + cu.getLineNumber(n.getStartPosition())
                      + "); renamed, "
                      + oldName
                      + " would hide it. Choose another name.");
            }
            return true;
          }
        });
    Stubs.Declared stubs =
        gate(project, Map.of(file, apply(source, occurrences, newName)), oldName, newName);
    return new Outcome(
        "renamed %s to %s in %s: %s, %s"
            .formatted(
                oldName,
                newName,
                project.relative(file),
                Names.plural(occurrences.size(), "occurrence"),
                Lines.list(lineNumbers(cu, occurrences))),
        stubs);
  }

  /**
   * Whether {@code binding} is the type with declaration key {@code key}, or a constructor of it.
   */
  private static boolean refersTo(IBinding binding, String key) {
    if (binding instanceof ITypeBinding t) {
      return t.getTypeDeclaration().getKey().equals(key);
    }
    return binding instanceof IMethodBinding m
        && m.isConstructor()
        && m.getDeclaringClass().getTypeDeclaration().getKey().equals(key);
  }

  /** Refuses a field, method or type that a library declares, not one of the project's files. */
  private static void refuseLibrary(SourceProject project, IBinding declaration) {
    ITypeBinding owner = declaration instanceof ITypeBinding t ? t : owner(declaration);
    if (owner != null && !owner.isTypeVariable() && !Signatures.projects(project, owner)) {
      throw new Refusal(
          describe(declaration)
              + " is declared in "
              + owner.getErasure().getQualifiedName()
              + ", not in this project. rename renames the project's own declarations.");
    }
  }

  /**
   * Refuses when {@code b}'s type already declares {@code newName} with b's parameter types, or
   * when it or a supertype has another method {@code newName} of as many parameters: an overload,
   * which existing calls of that name could resolve to the renamed method from, or calls of it to
   * that.
   */
  private static void refuseMethodClash(CompilationUnit unit, IMethodBinding b, String newName) {
    List<String> types = new ArrayList<>();
    for (ITypeBinding t : b.getParameterTypes()) {
      types.add(t.getErasure().getQualifiedName());
    }
    for (IMethodBinding other : b.getDeclaringClass().getDeclaredMethods()) {
      List<String> otherTypes = new ArrayList<>();
      for (ITypeBinding t : other.getParameterTypes()) {
        otherTypes.add(t.getErasure().getQualifiedName());
      }
      if (other.getName().equals(newName) && otherTypes.equals(types)) {
        ASTNode at = unit.findDeclaringNode(other.getKey());
        String label = Names.methodLabel(other);
        throw new Refusal(
            Names.typeLabel(b.getDeclaringClass())
                + " already has a method "
                + label.substring(label.indexOf('.') + 1)
                + (at == null ? "" : " (line " + unit.getLineNumber(at.getStartPosition()) + ")")
                + ". Choose another name.");
      }
    }
    int arity = b.getParameterTypes().length;
    for (ITypeBinding t : Hierarchy.supertypes(b.getDeclaringClass(), true)) {
      for (IMethodBinding other : t.getDeclaredMethods()) {
        if (other.getName().equals(newName)
            && !other.isConstructor()
            && other.getParameterTypes().length == arity) {
          throw new Refusal(
              Names.typeLabel(t)
                  + " has "
                  + Names.methodLabel(other)
                  + ", another method "
                  + newName
                  + " of "
                  + arity
                  + (arity == 1 ? " parameter" : " parameters")
                  + ": after the rename, a call of either could resolve to the other. Choose another"
                  + " name.");
        }
      }
    }
  }

  /**
   * "{@code what} in F: n occurrences, lines a, b" for one file; "{@code what} in k files: n
   * occurrences, F lines a, b; G line c" for several.
   */
  private static Outcome summary(
      String what, Map<String, List<Integer>> lines, int occurrences, Stubs.Declared stubs) {
    if (lines.size() == 1) {
      Map.Entry<String, List<Integer>> only = lines.entrySet().iterator().next();
      return new Outcome(
          "%s in %s: %s, %s"
              .formatted(
                  what,
                  only.getKey(),
                  Names.plural(occurrences, "occurrence"),
                  Lines.list(only.getValue())),
          stubs);
    }
    List<String> each = new ArrayList<>();
    lines.forEach((rel, ls) -> each.add(rel + " " + Lines.list(ls)));
    return new Outcome(
        "%s in %d files: %s, %s"
            .formatted(
                what,
                lines.size(),
                Names.plural(occurrences, "occurrence"),
                String.join("; ", each)),
        stubs);
  }

  /**
   * The local variable or parameter named {@code name} whose scope holds the unqualified use {@code
   * use}, declared before it; null if none, or if the use is qualified ({@code this.x}, {@code
   * o.x}, a Javadoc reference).
   */
  private static VariableDeclaration localInScope(
      CompilationUnit unit, SimpleName use, String name) {
    StructuralPropertyDescriptor where = use.getLocationInParent();
    if (where == FieldAccess.NAME_PROPERTY
        || where == QualifiedName.NAME_PROPERTY
        || where == SuperFieldAccess.NAME_PROPERTY
        || where == MemberRef.NAME_PROPERTY
        || where == VariableDeclarationFragment.NAME_PROPERTY) {
      return null;
    }
    VariableDeclaration[] found = new VariableDeclaration[1];
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SingleVariableDeclaration d) {
            check(d);
            return true;
          }

          @Override
          public boolean visit(VariableDeclarationFragment d) {
            check(d);
            return true;
          }

          private void check(VariableDeclaration d) {
            if (found[0] == null
                && d.getName().getIdentifier().equals(name)
                && !(d.getParent() instanceof FieldDeclaration)
                && d.getStartPosition() < use.getStartPosition()) {
              ASTNode scope = d.getParent();
              if (scope instanceof VariableDeclarationStatement
                  || scope instanceof VariableDeclarationExpression) {
                scope = scope.getParent();
              }
              int start = scope.getStartPosition();
              if (use.getStartPosition() >= start
                  && use.getStartPosition() < start + scope.getLength()) {
                found[0] = d;
              }
            }
          }
        });
    return found[0];
  }

  /** Writes the renamed files through the gate, refusing (and restoring) on new compile errors. */
  private static Stubs.Declared gate(
      SourceProject project, Map<Path, String> texts, String oldName, String newName)
      throws IOException {
    return Gate.applyOrRefuse(
        project,
        texts,
        "renaming '" + oldName + "' to '" + newName + "'",
        "Choose a name that does not clash with a name the code already uses.");
  }

  /** The distinct lines of {@code nodes}, in order. */
  private static List<Integer> lineNumbers(CompilationUnit unit, List<? extends ASTNode> nodes) {
    TreeSet<Integer> lines = new TreeSet<>();
    for (ASTNode node : nodes) {
      lines.add(unit.getLineNumber(node.getStartPosition()));
    }
    return new ArrayList<>(lines);
  }

  /** The method, initializer or field the variable is declared in; the compilation unit if none. */
  private static ASTNode scopeOf(CompilationUnit cu, IVariableBinding variable, String name) {
    ASTNode declaration = cu.findDeclaringNode(variable);
    if (declaration == null) {
      throw new Refusal(
          "cannot find where '"
              + name
              + "' is declared. The file may not compile; "
              + "fix the compile errors first.");
    }
    ASTNode scope = declaration;
    while (scope != null && !(scope instanceof BodyDeclaration)) {
      scope = scope.getParent();
    }
    return scope != null ? scope : cu;
  }

  private record Collected(List<SimpleName> occurrences, SimpleName clash) {}

  /**
   * Uses of {@code variable} in {@code scope}, and the first use of {@code newName} as another
   * variable.
   */
  private static Collected collect(ASTNode scope, IVariableBinding variable, String newName) {
    List<SimpleName> occurrences = new ArrayList<>();
    SimpleName[] clash = new SimpleName[1];
    scope.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(SimpleName node) {
            IBinding binding = node.resolveBinding();
            if (binding instanceof IVariableBinding other) {
              if (other.isEqualTo(variable)) {
                occurrences.add(node);
              } else if (clash[0] == null && node.getIdentifier().equals(newName)) {
                clash[0] = node;
              }
            }
            return true;
          }
        });
    occurrences.sort(Comparator.comparingInt(ASTNode::getStartPosition));
    return new Collected(occurrences, clash[0]);
  }

  /** Replaces each occurrence with {@code newName}. */
  private static String apply(String source, List<SimpleName> occurrences, String newName) {
    return TextEdit.apply(
        source,
        occurrences.stream()
            .map(n -> new TextEdit(n.getStartPosition(), Nodes.end(n), newName))
            .toList(),
        0);
  }

  /**
   * What a rename writes: each file's text (null for one it deletes), the lines it changed in each
   * file, where each renamed name started, and how many it renamed.
   */
  private record Written(
      Map<Path, String> texts,
      Map<String, List<Integer>> lines,
      Map<Path, List<Integer>> renamedAt,
      int occurrences) {}

  /** A check of one file before it is renamed, given its uses in order; it refuses. */
  private interface FileCheck {
    void check(Path file, CompilationUnit unit, List<SimpleName> uses);
  }

  /**
   * Renames to {@code newName} each name {@code oldName} in {@code holding}'s files that {@code
   * isUse} accepts, Javadoc included, once {@code check} has seen the file; the file {@code from},
   * when not null, is written as {@code to} and deleted.
   */
  private static Written renameUses(
      SourceProject project,
      Holding holding,
      String oldName,
      String newName,
      Predicate<SimpleName> isUse,
      FileCheck check,
      Path from,
      Path to) {
    Map<Path, String> texts = new TreeMap<>();
    Map<String, List<Integer>> lines = new TreeMap<>();
    Map<Path, List<Integer>> renamedAt = new TreeMap<>();
    int occurrences = 0;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      Path f = e.getKey();
      CompilationUnit unit = e.getValue();
      List<SimpleName> uses = new ArrayList<>();
      unit.accept(
          new ASTVisitor(true) {
            @Override
            public boolean visit(SimpleName node) {
              if (node.getIdentifier().equals(oldName) && isUse.test(node)) {
                uses.add(node);
              }
              return true;
            }
          });
      uses.sort(Comparator.comparingInt(ASTNode::getStartPosition));
      check.check(f, unit, uses);
      if (uses.isEmpty()) {
        continue;
      }
      Path written = f.equals(from) ? to : f;
      if (f.equals(from)) {
        texts.put(from, null);
      }
      texts.put(written, apply(holding.texts().get(f), uses, newName));
      lines.put(project.relative(written), lineNumbers(unit, uses));
      renamedAt.put(written, uses.stream().map(ASTNode::getStartPosition).toList());
      occurrences += uses.size();
    }
    return new Written(texts, lines, renamedAt, occurrences);
  }
}
