package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;

/**
 * extract_variable with as: the expression becomes a constant ({@code private static final}), a
 * field ({@code private final}) of the enclosing type, or a new parameter of the enclosing method
 * that every call passes it to. It must be literals and constants only: anything else would be
 * evaluated at another time (a field's initializer runs once) or place (a call site).
 */
final class Introduce {

  private Introduce() {}

  static Outcome run(
      SourceProject project,
      Opened opened,
      Expression e,
      String name,
      String as,
      ITypeBinding type,
      String source,
      int line,
      boolean all)
      throws Exception {
    Precondition.require(
        Precondition.that(
            () -> List.of("constant", "field", "parameter").contains(as),
            () -> "as is constant, field or parameter: what " + source + " becomes."),
        Precondition.that(
            () -> as.equals("parameter") ? ParameterValue.byParameters(e) : constantOnly(e),
            () ->
                source
                    + (as.equals("parameter")
                        ? " uses a local, a field that is not a constant, a call on an enclosing"
                            + " object or on super, or a parameter the method assigns, so a call"
                            + " could not pass it"
                        : " uses a local, a parameter, a field that is not a constant, or a call,"
                            + " so as a "
                            + as
                            + " it would be evaluated at another time or place")
                    + ". Extract it as a variable instead."));
    String typeName = Extract.typeName(type);
    String rel = opened.rel();
    if (as.equals("parameter")) {
      return parameter(project, opened, e, name, typeName, source, all);
    }
    AbstractTypeDeclaration owner = enclosing(e, AbstractTypeDeclaration.class);
    BodyDeclaration member = enclosing(e, BodyDeclaration.class);
    Precondition.require(
        Precondition.that(
            () -> owner != null && owner.resolveBinding() != null,
            () -> source + " is not in a type that resolves."),
        Precondition.that(
            () ->
                as.equals("constant")
                    || member == null
                    || !Modifier.isStatic(member.getModifiers()),
            () ->
                source
                    + " is in a static member, which an instance field cannot serve. Use"
                    + " as=constant."));
    for (IVariableBinding f : owner.resolveBinding().getDeclaredFields()) {
      if (f.getName().equals(name)) {
        throw new Refusal(
            owner.getName().getIdentifier()
                + " already has a field "
                + name
                + ". Choose another name.");
      }
    }
    String decl =
        "private "
            + (as.equals("constant") ? "static final " : "final ")
            + typeName
            + " "
            + name
            + " = "
            + source
            + ";";
    String text = opened.text();
    List<TextEdit> edits = new ArrayList<>(MemberMove.addTo(text, owner, decl, 0));
    edits.add(new TextEdit(e.getStartPosition(), e.getStartPosition() + e.getLength(), name));
    String after = TextEdit.apply(text, edits, 0);
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            after,
            List.of(),
            "introducing " + as + " " + name,
            "Choose a name nothing else uses.");
    return new Outcome(
        "introduced "
            + as
            + " "
            + name
            + " ("
            + decl
            + ") in "
            + owner.getName().getIdentifier()
            + ", in place of "
            + source
            + " at line "
            + line
            + " of "
            + rel
            + written.fixed().said(),
        written.declared());
  }

  /**
   * As a parameter: {@code add_parameter} adds it to the enclosing method, every call passing the
   * expression, and the expression (with {@code all}, each occurrence of it in the method) becomes
   * its name, in one change. An expression with a call now runs at each call, before the method:
   * the behavior gate checks that move.
   */
  private static Outcome parameter(
      SourceProject project,
      Opened opened,
      Expression e,
      String name,
      String typeName,
      String source,
      boolean all)
      throws Exception {
    MethodDeclaration m = enclosing(e, MethodDeclaration.class);
    Precondition.require(
        Precondition.that(
            () -> m != null && m.resolveBinding() != null,
            () -> source + " is not directly in a method, whose parameter it could be."));
    int size = m.parameters().size();
    ParameterValue.InMethod read = new ParameterValue.InMethod(source, e, m, size);
    // Right after the last parameter it reads, so the arguments after that run after it, as they
    // ran before the body; one that calls or may throw stays last, after every argument.
    int place =
        read.acts() || read.mayThrow()
            ? size
            : read.uses().stream().max(Integer::compare).map(i -> i + 1).orElse(size);
    ParameterValue.InMethod v = new ParameterValue.InMethod(source, e, m, place);
    Map<String, Object> request =
        new java.util.HashMap<>(
            Map.of(
                "file", opened.rel(),
                "name", Signatures.label(m.resolveBinding()),
                "parameter", name,
                "type", typeName,
                "value", source));
    if (place < size) {
      request.put(
          "before",
          ((SingleVariableDeclaration) m.parameters().get(place)).getName().getIdentifier());
    }
    List<Expression> found = all ? ParameterValue.sameIn(m, e) : List.of(e);
    if (v.acts() && found.size() > 1) {
      throw new Refusal(
          source
              + " calls code and occurs "
              + found.size()
              + " times in "
              + m.getName()
              + ": as a parameter it would run once, at each call, where the method ran it "
              + found.size()
              + " times. Leave out all_occurrences, or extract it as a variable instead.");
    }
    if (v.acts() && m.isConstructor()) {
      throw new Refusal(
          source
              + " would run before the constructor above this one and the field initializers,"
              + " not after them. Extract it as a variable instead.");
    }
    if (v.acts() || v.mayThrow()) {
      Statement first = (Statement) m.getBody().statements().get(0);
      for (Expression x : found) {
        Behavior.flow(
            project,
            new Behavior.Move(x, m.getBody(), List.of(first), source, "making it a parameter"));
      }
    }
    Outcome added =
        Signatures.addParameter(
            project,
            new Args("add_parameter", request),
            ParameterValue.perCall(project, v),
            (family, change) -> {
              if (v.acts() && (family.decls.size() != 1 || !family.lambdas.isEmpty())) {
                throw new Refusal(
                    family.label
                        + " overrides or implements another method, or another overrides or"
                        + " implements it, and a call that runs another body would now run "
                        + source
                        + " too. Extract it as a variable instead.");
              }
              Signatures.Decl d =
                  family.decls.stream()
                      .filter(x ->
                          x.file().rel().equals(opened.rel())
                              && x.m().getStartPosition() == m.getStartPosition())
                      .findFirst()
                      .orElseThrow();
              for (Expression x : found) {
                change.replaceRange(d.file(), x.getStartPosition(), Nodes.end(x), name);
              }
              if (v.acts()) {
                Exceptional.change(
                    project,
                    source
                        + " may throw: "
                        + family.label.substring(0, family.label.indexOf('('))
                        + " computed it in its body, and each call now computes it among its"
                        + " arguments, before the method runs",
                    "extract it as a variable instead.");
              }
            });
    List<Integer> lines = new ArrayList<>();
    for (Expression x : found) {
      int n = opened.unit().getLineNumber(x.getStartPosition());
      if (!lines.contains(n)) {
        lines.add(n);
      }
    }
    String at =
        lines.size() == 1
            ? "line " + lines.get(0)
            : "lines "
                + String.join(
                    ", ", lines.subList(0, lines.size() - 1).stream().map(String::valueOf).toList())
                + " and "
                + lines.get(lines.size() - 1);
    return new Outcome(
        added.text() + "; " + source + " at " + at + " is now " + name, added.declared());
  }

  /** Whether {@code e} is literals, constants and operators on them. */
  private static boolean constantOnly(Expression e) {
    for (Effects.Effect x : Effects.of(e).all()) {
      switch (x.kind()) {
        case NAME -> {
          IVariableBinding v = x.variable();
          boolean constant =
              v.isField()
                  && Modifier.isStatic(v.getModifiers())
                  && Modifier.isFinal(v.getModifiers());
          if (!constant) {
            return false;
          }
        }
        case MAY_THROW -> {}
        default -> {
          return false;
        }
      }
    }
    return true;
  }

  /** The nearest {@code type} around {@code n}, or null past a lambda or an anonymous class. */
  static <T extends ASTNode> T enclosing(ASTNode n, Class<T> type) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (type.isInstance(p)) {
        return type.cast(p);
      }
      if (p instanceof LambdaExpression || p instanceof AnonymousClassDeclaration) {
        return null;
      }
      if (p instanceof CompilationUnit) {
        return null;
      }
    }
    return null;
  }
}
