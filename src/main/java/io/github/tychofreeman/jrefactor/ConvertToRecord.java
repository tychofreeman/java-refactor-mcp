package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * convert_to_record: a final-field class becomes a record. Its instance fields are the components,
 * its one constructor (which only assigns each field from its parameter, in order) is the canonical
 * one and goes; the rest stays, and an accessor ({@code x()}) becomes public. A record generates
 * value-based equals, hashCode and toString for those the class does not define, which changes
 * behavior, so that needs {@code value_semantics}; an {@code equals} that does not take an Object
 * is an overload and does not count. Field annotations go onto the components; a transient field,
 * and a constructor less visible than the class, are refused.
 */
final class ConvertToRecord {

  private record Component(String type, String name) {}

  private ConvertToRecord() {}

  static Outcome run(SourceProject project, String rel, String name, boolean valueSemantics)
      throws Exception {
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    TypeDeclaration type = opened.topLevel(name);
    ITypeBinding binding = type.resolveBinding();
    Precondition.require(
        Precondition.that(() -> !type.isInterface(), () -> name + " is an interface, not a class."),
        Precondition.that(
            () -> type.getSuperclassType() == null,
            () ->
                name + " extends " + type.getSuperclassType() + ", and a record extends nothing."),
        Precondition.that(
            () -> !Modifier.isAbstract(type.getModifiers()),
            () -> name + " is abstract, and a record is final."));
    List<Component> components = new ArrayList<>();
    List<TextEdit> edits = new ArrayList<>();
    List<MethodDeclaration> constructors = new ArrayList<>();
    for (Object o : type.bodyDeclarations()) {
      BodyDeclaration d = (BodyDeclaration) o;
      boolean isStatic = Modifier.isStatic(d.getModifiers());
      if (d instanceof FieldDeclaration f && !isStatic) {
        String fieldType = annotations(text, f) + Nodes.source(text, f.getType());
        for (Object v : f.fragments()) {
          VariableDeclarationFragment frag = (VariableDeclarationFragment) v;
          String field = frag.getName().getIdentifier();
          Precondition.require(
              Precondition.that(
                  () -> !Modifier.isTransient(f.getModifiers()),
                  () ->
                      field
                          + " is transient, and a record serializes every component. Remove"
                          + " transient first, if that is wanted."),
              Precondition.that(
                  () -> Modifier.isFinal(f.getModifiers()),
                  () ->
                      field + " is not final, and a record's components are. Make it final first."),
              Precondition.that(
                  () -> frag.getInitializer() == null && frag.extraDimensions().isEmpty(),
                  () ->
                      field
                          + " has an initializer (or [] after its name), and a record's component"
                          + " is set by its constructor. Set it there first."));
          components.add(new Component(fieldType, field));
        }
        remove(edits, MemberMove.removal(text, f));
      } else if (d instanceof Initializer && !isStatic) {
        throw new Refusal(name + " has an instance initializer, which a record cannot have.");
      } else if (d instanceof MethodDeclaration m && m.isConstructor()) {
        constructors.add(m);
      }
    }
    Precondition.require(
        Precondition.that(
            () -> !components.isEmpty(), () -> name + " has no instance fields to be components."),
        Precondition.that(
            () -> constructors.size() == 1,
            () ->
                name
                    + " has "
                    + constructors.size()
                    + " constructors; a record has one canonical constructor. Leave one that"
                    + " assigns each field from its parameter."));
    MethodDeclaration ctor = constructors.get(0);
    Precondition.require(
        Precondition.that(
            () -> canonical(ctor, components),
            () ->
                "the constructor of "
                    + name
                    + " does more than assign each field from its parameter, in field order, so it"
                    + " is not a record's canonical constructor. Make it do only that first."));
    Precondition.require(
        Precondition.that(
            () -> access(ctor.getModifiers()) >= access(type.getModifiers()),
            () ->
                "the constructor of "
                    + name
                    + " is "
                    + accessWord(ctor.getModifiers())
                    + ", and a record's canonical constructor is as visible as the record. Make it"
                    + " as visible as the class first, if that is wanted."));
    remove(edits, MemberMove.removal(text, ctor));
    refuseSubclasses(project, opened.file(), binding, name);

    List<String> missing = new ArrayList<>();
    if (!definesEquals(type)) {
      missing.add("equals");
    }
    if (!declares(type, "hashCode", 0)) {
      missing.add("hashCode");
    }
    if (!declares(type, "toString", 0)) {
      missing.add("toString");
    }
    if (!missing.isEmpty() && !valueSemantics) {
      throw new Refusal(
          name
              + " does not define "
              + Extract.and(missing)
              + overload(type)
              + ", which a record generates from its components: equality would compare values,"
              + " not identity, and toString would list them. Pass value_semantics=true if that is"
              + " wanted, or define them first.");
    }
    List<String> widened = new ArrayList<>();
    for (MethodDeclaration m : type.getMethods()) {
      boolean accessor =
          !m.isConstructor()
              && m.parameters().isEmpty()
              && components.stream().anyMatch(c -> c.name().equals(m.getName().getIdentifier()));
      if (accessor && !Modifier.isPublic(m.getModifiers())) {
        edits.add(publicEdit(text, m));
        widened.add(m.getName().getIdentifier() + "()");
      }
    }

    int headerStart =
        type.getJavadoc() != null
            ? skipSpace(text, Nodes.end(type.getJavadoc()))
            : type.getStartPosition();
    int afterHeader = Nodes.end(type.getName());
    for (Object o : type.typeParameters()) {
      afterHeader = Math.max(afterHeader, Nodes.end(((ASTNode) o)));
    }
    for (Object o : type.superInterfaceTypes()) {
      afterHeader = Math.max(afterHeader, Nodes.end(((ASTNode) o)));
    }
    int open = text.indexOf('{', afterHeader);
    List<String> modifiers = new ArrayList<>();
    for (Object o : type.modifiers()) {
      if (o instanceof Annotation a) {
        modifiers.add(Nodes.source(text, a));
      } else if (o instanceof Modifier m && !m.isFinal() && !m.isStatic()) {
        modifiers.add(m.getKeyword().toString());
      }
    }
    List<String> parts = new ArrayList<>();
    for (Component c : components) {
      parts.add(c.type() + " " + c.name());
    }
    List<String> interfaces = new ArrayList<>();
    for (Object o : type.superInterfaceTypes()) {
      interfaces.add(Nodes.source(text, (ASTNode) o));
    }
    String typeParameters =
        type.typeParameters().isEmpty()
            ? ""
            : text.substring(
                ((ASTNode) type.typeParameters().get(0)).getStartPosition() - 1,
                Nodes.end(((ASTNode) type.typeParameters().get(type.typeParameters().size() - 1)))
                    + 1);
    String header =
        (modifiers.isEmpty() ? "" : String.join(" ", modifiers) + " ")
            + "record "
            + name
            + typeParameters
            + "("
            + String.join(", ", parts)
            + ")"
            + (interfaces.isEmpty() ? "" : " implements " + String.join(", ", interfaces))
            + " {";
    String edited = TextEdit.apply(text, edits, 0);
    String after = edited.substring(0, headerStart) + header + edited.substring(open + 1);

    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            after,
            List.of(),
            "converting " + name + " to a record",
            "Nothing was changed. Change what the errors name first.");
    List<String> names = components.stream().map(Component::name).toList();
    return new Outcome(
        "converted "
            + name
            + " to a record ("
            + String.join(", ", names)
            + ") in "
            + rel
            + (missing.isEmpty() ? "" : "; it now has the record's " + Extract.and(missing))
            + (widened.isEmpty() ? "" : "; made " + Extract.and(widened) + " public")
            + written.fixed().said(),
        written.declared());
  }

  /** Whether {@code ctor} only assigns each component from its parameter, in order. */
  private static boolean canonical(MethodDeclaration ctor, List<Component> components) {
    List<?> params = ctor.parameters();
    List<?> statements = ctor.getBody() == null ? List.of() : ctor.getBody().statements();
    if (params.size() != components.size() || statements.size() != components.size()) {
      return false;
    }
    for (int i = 0; i < components.size(); i++) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) params.get(i);
      if (!(statements.get(i) instanceof ExpressionStatement s)
          || !(s.getExpression() instanceof Assignment a)
          || a.getOperator() != Assignment.Operator.ASSIGN
          || !(a.getLeftHandSide() instanceof FieldAccess f)
          || !(f.getExpression() instanceof ThisExpression)
          || !f.getName().getIdentifier().equals(components.get(i).name())
          || !(a.getRightHandSide() instanceof SimpleName v)
          || !v.getIdentifier().equals(p.getName().getIdentifier())
          || p.isVarargs()) {
        return false;
      }
    }
    return true;
  }

  private static boolean declares(TypeDeclaration type, String method, int parameters) {
    for (MethodDeclaration m : type.getMethods()) {
      if (m.getName().getIdentifier().equals(method) && m.parameters().size() == parameters) {
        return true;
      }
    }
    return false;
  }

  /** Refuses a class of the project that extends {@code type}: a record is final. */
  private static void refuseSubclasses(
      SourceProject project, Path file, ITypeBinding type, String name) throws Exception {
    Holding holding = Holding.of(project, name, file);
    String key = type.getTypeDeclaration().getKey();
    for (CompilationUnit unit : holding.units().values()) {
      for (TypeDeclaration t : Nodes.all(unit, TypeDeclaration.class)) {
        ITypeBinding b = t.resolveBinding();
        if (b != null
            && b.getSuperclass() != null
            && b.getSuperclass().getTypeDeclaration().getKey().equals(key)) {
          throw new Refusal(
              t.getName().getIdentifier()
                  + " extends "
                  + name
                  + ", and a record cannot be extended. Change that class first.");
        }
      }
    }
  }

  /** The edit that makes {@code m} public: replacing private or protected, or adding public. */
  private static TextEdit publicEdit(String text, MethodDeclaration m) {
    for (Object o : m.modifiers()) {
      if (o instanceof Modifier mod && (mod.isPrivate() || mod.isProtected())) {
        return new TextEdit(mod.getStartPosition(), Nodes.end(mod), "public");
      }
    }
    int at = m.getJavadoc() != null ? Nodes.end(m.getJavadoc()) : m.getStartPosition();
    for (Object o : m.modifiers()) {
      if (o instanceof Annotation a) {
        at = Math.max(at, Nodes.end(a));
      }
    }
    at = skipSpace(text, at);
    return new TextEdit(at, at, "public ");
  }

  private static int skipSpace(String text, int at) {
    while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
      at++;
    }
    return at;
  }

  /** Whether {@code type} declares equals(Object), the method a record would generate. */
  private static boolean definesEquals(TypeDeclaration type) {
    for (MethodDeclaration m : type.getMethods()) {
      IMethodBinding b = m.resolveBinding();
      if (m.getName().getIdentifier().equals("equals")
          && b != null
          && b.getParameterTypes().length == 1
          && b.getParameterTypes()[0].getQualifiedName().equals("java.lang.Object")) {
        return true;
      }
    }
    return false;
  }

  /** " (its equals(T) does not override Object.equals)" for an equals overload, else "". */
  private static String overload(TypeDeclaration type) {
    for (MethodDeclaration m : type.getMethods()) {
      if (m.getName().getIdentifier().equals("equals")
          && m.parameters().size() == 1
          && !definesEquals(type)) {
        SingleVariableDeclaration p = (SingleVariableDeclaration) m.parameters().get(0);
        return " (its equals(" + p.getType() + ") does not override Object.equals)";
      }
    }
    return "";
  }

  /** The field's annotations, each followed by a space, for its component. */
  private static String annotations(String text, FieldDeclaration f) {
    StringBuilder out = new StringBuilder();
    for (Object o : f.modifiers()) {
      if (o instanceof Annotation a) {
        out.append(Nodes.source(text, a)).append(' ');
      }
    }
    return out.toString();
  }

  /** Access as a rank: private 0, package 1, protected 2, public 3. */
  private static int access(int modifiers) {
    if (Modifier.isPublic(modifiers)) {
      return 3;
    }
    if (Modifier.isProtected(modifiers)) {
      return 2;
    }
    return Modifier.isPrivate(modifiers) ? 0 : 1;
  }

  private static String accessWord(int modifiers) {
    return List.of("private", "package-private", "protected", "public").get(access(modifiers));
  }

  /**
   * Adds {@code removal} to {@code edits}, less the part an earlier removal takes: the constructor
   * as the last member takes the blank line before it, which the field before it took too.
   */
  private static void remove(List<TextEdit> edits, TextEdit removal) {
    int start = removal.start();
    for (TextEdit e : edits) {
      if (e.start() <= start && start < e.end()) {
        start = e.end();
      }
    }
    edits.add(new TextEdit(Math.min(start, removal.end()), removal.end(), ""));
  }
}
