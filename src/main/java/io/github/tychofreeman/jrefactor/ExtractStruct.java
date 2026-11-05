package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

/**
 * extract_struct with parameters (introduce parameter object): consecutive parameters of a method
 * become one parameter of a new record, in the method and every declaration of its family; each
 * body reads the record's components where it read the parameters, and every call passes {@code new
 * Record(...)} of its arguments.
 */
final class ExtractStruct {

  private ExtractStruct() {}

  static Outcome run(SourceProject project, Args args) throws Exception {
    String struct = args.required("struct");
    String parameter = args.required("parameter");
    List<String> chosen = args.list("parameters");
    Precondition.require(
        Precondition.that(
            () -> SourceVersion.isIdentifier(struct) && !SourceVersion.isKeyword(struct),
            () -> struct + " is not a valid name for the record."),
        Precondition.that(
            () -> SourceVersion.isIdentifier(parameter) && !SourceVersion.isKeyword(parameter),
            () -> parameter + " is not a valid name for the new parameter."),
        Precondition.that(
            () -> !chosen.isEmpty(), () -> "parameters names the parameters to group."));
    String rel = args.required("file");
    Path record =
        project.javaFile(rel).toAbsolutePath().normalize().resolveSibling(struct + ".java");
    Precondition.require(
        Precondition.that(
            () -> !Files.exists(record),
            () -> project.relative(record) + " already exists. Choose another name."));
    MethodFamily family = MethodFamily.of(project, args);
    List<Integer> at = new ArrayList<>();
    for (String p : chosen) {
      at.add(family.index(p, "parameters", false));
    }
    Collections.sort(at);
    int first = at.get(0);
    int last = at.get(at.size() - 1);
    Precondition.require(
        Precondition.that(
            () -> last - first == at.size() - 1,
            () ->
                Extract.and(chosen)
                    + " are not next to each other in "
                    + family.label
                    + ", and grouped at one place their arguments would run in another order."
                    + " Move them together first (move_parameter)."),
        Precondition.that(
            () -> family.lambdas.isEmpty(),
            () ->
                "lambdas implement "
                    + family.label
                    + ", whose parameters extract_struct does not group yet. Write them as methods"
                    + " first."));

    List<String> components = new ArrayList<>();
    for (int i = first; i <= last; i++) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) family.target.parameters().get(i);
      Precondition.require(
          Precondition.that(
              () -> !p.isVarargs(),
              () -> p.getName() + " is varargs, which a record cannot hold."));
      components.add(
          Extract.typeName(p.resolveBinding().getType()) + " " + p.getName().getIdentifier());
    }

    SignatureChange change = new SignatureChange(family);
    for (Signatures.Decl d : family.decls) {
      List<?> params = d.m().parameters();
      change.replaceRange(
          d.file(),
          ((ASTNode) params.get(first)).getStartPosition(),
          Signatures.end((ASTNode) params.get(last)),
          struct + " " + parameter);
      if (d.m().getBody() == null) {
        continue;
      }
      List<IVariableBinding> grouped = new ArrayList<>();
      for (int i = first; i <= last; i++) {
        grouped.add(((SingleVariableDeclaration) params.get(i)).resolveBinding());
      }
      d.m()
          .getBody()
          .accept(
              new ASTVisitor() {
                @Override
                public boolean visit(SimpleName n) {
                  if (n.getIdentifier().equals(parameter)) {
                    throw new Refusal(
                        "the body of "
                            + family.label
                            + " already names "
                            + parameter
                            + ". Choose another name for the new parameter.");
                  }
                  if (!(n.resolveBinding() instanceof IVariableBinding v)
                      || grouped.stream().noneMatch(g -> g != null && g.isEqualTo(v))) {
                    return true;
                  }
                  ASTNode p = n.getParent();
                  if ((p instanceof Assignment a && a.getLeftHandSide() == n)
                      || p instanceof PostfixExpression
                      || (p instanceof PrefixExpression pre
                          && (pre.getOperator() == PrefixExpression.Operator.INCREMENT
                              || pre.getOperator() == PrefixExpression.Operator.DECREMENT))) {
                    throw new Refusal(
                        "the body of "
                            + family.label
                            + " assigns "
                            + n
                            + ", which as a record's component cannot be. Assign a local instead"
                            + " first.");
                  }
                  change.replaceRange(
                      d.file(),
                      n.getStartPosition(),
                      Signatures.end(n),
                      parameter + "." + n.getIdentifier() + "()");
                  return true;
                }
              });
    }
    for (Signatures.Call c : family.calls) {
      List<?> arguments = c.arguments();
      int s = ((ASTNode) arguments.get(first)).getStartPosition();
      int e = Signatures.end((ASTNode) arguments.get(last));
      change.replaceRange(
          c.file(), s, e, "new " + struct + "(" + c.file().text().substring(s, e) + ")");
    }

    Opened home = Opened.of(project, rel);
    String pkg =
        home.unit().getPackage() == null
            ? ""
            : "package " + home.unit().getPackage().getName().getFullyQualifiedName() + ";\n\n";
    String text = pkg + "public record " + struct + "(" + String.join(", ", components) + ") {}\n";
    Gate.applyOrRefuse(
        project,
        Map.of(record, Imports.fix(project, record, "", text, List.of()).text()),
        "creating " + struct,
        "Nothing was changed.");
    return change.apply(
        project,
        "grouped "
            + Extract.and(chosen)
            + " of "
            + family.label
            + " into "
            + struct
            + " "
            + parameter
            + " ("
            + project.relative(record)
            + ")",
        args.list("imports"));
  }
}
