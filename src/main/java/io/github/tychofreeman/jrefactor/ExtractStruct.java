package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * extract_struct with parameters (introduce parameter object): consecutive parameters of a method
 * become one parameter of a new record, in the method and every declaration of its family; each
 * body reads the record's components where it read the parameters, and every call passes {@code new
 * Record(...)} of its arguments.
 */
final class ExtractStruct {

  private ExtractStruct() {}

  static Outcome run(SourceProject project, Args args) throws Exception {
    if (args.has("fields")) {
      return fields(project, args);
    }
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

  /**
   * extract_struct with fields (extract class): the named instance fields move into a new
   * package-private class in a file of its own, held by one {@code private final} field of the
   * class in the first one's place; every use goes through it ({@code home.street}).
   */
  private static Outcome fields(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String struct = args.required("struct");
    String holder = args.required("field");
    List<String> chosen = args.list("fields");
    Precondition.require(
        Precondition.that(
            () -> SourceVersion.isIdentifier(struct) && !SourceVersion.isKeyword(struct),
            () -> struct + " is not a valid name for the class."),
        Precondition.that(
            () -> SourceVersion.isIdentifier(holder) && !SourceVersion.isKeyword(holder),
            () -> holder + " is not a valid name for the field that holds it."),
        Precondition.that(() -> !chosen.isEmpty(), () -> "fields names the fields to move."));
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    TypeDeclaration owner = opened.topLevel(args.required("name"));
    String ownerName = owner.getName().getIdentifier();
    Path target = opened.file().resolveSibling(struct + ".java");
    Precondition.require(
        Precondition.that(
            () -> !Files.exists(target),
            () -> project.relative(target) + " already exists. Choose another name."));
    List<FieldDeclaration> moved = new ArrayList<>();
    List<String> keys = new ArrayList<>();
    for (String f : chosen) {
      FieldDeclaration fd = null;
      for (FieldDeclaration d : owner.getFields()) {
        for (Object o : d.fragments()) {
          if (((VariableDeclarationFragment) o).getName().getIdentifier().equals(f)) {
            fd = d;
          }
        }
      }
      FieldDeclaration decl = fd;
      Precondition.require(
          Precondition.that(() -> decl != null, () -> f + " is not a field of " + ownerName + "."),
          Precondition.that(
              () -> decl.fragments().size() == 1,
              () -> "the declaration of " + f + " declares several fields. Split it first."),
          Precondition.that(
              () -> !Modifier.isStatic(decl.getModifiers()),
              () -> f + " is static; extract_struct moves instance fields."));
      VariableDeclarationFragment frag = (VariableDeclarationFragment) decl.fragments().get(0);
      Expression init = frag.getInitializer();
      Precondition.require(
          Precondition.that(
              () -> !(Modifier.isFinal(decl.getModifiers()) && init == null),
              () ->
                  f
                      + " is final and set by a constructor, which could not set it through the"
                      + " holder. Give it an initializer, or leave it out."),
          Precondition.that(
              () -> init == null || initializedAlone(init),
              () ->
                  "the initializer of "
                      + f
                      + " reads a field, a local or a call, and in the new class it would run when"
                      + " the holder is made, not in its own place. Leave it out, or make it a"
                      + " constant first."));
      moved.add(decl);
      keys.add(frag.resolveBinding().getVariableDeclaration().getKey());
    }
    moved.sort(Comparator.comparingInt(ASTNode::getStartPosition));
    int ownerStart = owner.getStartPosition();
    int ownerEnd = ownerStart + owner.getLength();
    Map<String, Set<Integer>> outside = new TreeMap<>();
    for (String f : chosen) {
      Holding holding = Holding.of(project, f, opened.file());
      for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
        boolean home = e.getKey().equals(opened.file());
        CompilationUnit u = e.getValue();
        u.accept(
            new ASTVisitor() {
              @Override
              public boolean visit(SimpleName n) {
                if (!n.isDeclaration()
                    && n.resolveBinding() instanceof IVariableBinding v
                    && keys.contains(v.getVariableDeclaration().getKey())
                    && (!home
                        || n.getStartPosition() < ownerStart
                        || n.getStartPosition() >= ownerEnd)) {
                  outside
                      .computeIfAbsent(
                          n.getIdentifier() + " is used in " + project.relative(e.getKey()),
                          k -> new TreeSet<>())
                      .add(u.getLineNumber(n.getStartPosition()));
                }
                return true;
              }
            });
      }
    }
    if (!outside.isEmpty()) {
      List<String> each = new ArrayList<>();
      outside.forEach((where, lines) -> each.add(where + " " + Lines.list(new ArrayList<>(lines))));
      throw new Refusal(
          String.join("; ", each)
              + ", and extract_struct moves only fields their class alone uses. Encapsulate them"
              + " first (encapsulate_field), or leave them out.");
    }
    List<TextEdit> edits = new ArrayList<>();
    owner.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            if (n.getIdentifier().equals(holder)) {
              throw new Refusal(
                  ownerName + " already names " + holder + ". Choose another name for the holder.");
            }
            if (!n.isDeclaration()
                && n.resolveBinding() instanceof IVariableBinding v
                && keys.contains(v.getVariableDeclaration().getKey())) {
              edits.add(new TextEdit(n.getStartPosition(), n.getStartPosition(), holder + "."));
            }
            return true;
          }
        });
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < moved.size(); i++) {
      FieldDeclaration fd = moved.get(i);
      int start = Lines.start(text, fd.getStartPosition());
      String fieldText = text.substring(fd.getStartPosition(), Signatures.end(fd));
      for (Object o : fd.modifiers()) {
        if (o instanceof Modifier m && m.isPrivate()) {
          int from = m.getStartPosition() - fd.getStartPosition();
          int to = Signatures.end(m) - fd.getStartPosition();
          while (to < fieldText.length() && fieldText.charAt(to) == ' ') {
            to++;
          }
          fieldText = fieldText.substring(0, from) + fieldText.substring(to);
        }
      }
      body.append("  ").append(fieldText).append("\n");
      if (i == 0) {
        String indent = text.substring(start, fd.getStartPosition());
        edits.add(
            new TextEdit(
                start,
                Lines.end(text, Signatures.end(fd) - 1),
                indent + "private final " + struct + " " + holder + " = new " + struct + "();\n"));
      } else {
        edits.add(MemberMove.removal(text, fd));
      }
    }
    String pkg =
        opened.unit().getPackage() == null
            ? ""
            : "package " + opened.unit().getPackage().getName().getFullyQualifiedName() + ";\n\n";
    String created = pkg + "class " + struct + " {\n" + body + "}\n";
    Gate.applyOrRefuse(
        project,
        Map.of(target, Imports.fix(project, target, "", created, List.of()).text()),
        "creating " + struct,
        "Nothing was changed.");
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            TextEdit.apply(text, edits, 0),
            args.list("imports"),
            "moving fields into " + struct,
            "Nothing was changed.");
    return new Outcome(
        "moved "
            + Extract.and(chosen)
            + " of "
            + ownerName
            + " into "
            + struct
            + " ("
            + project.relative(target)
            + "), held in "
            + holder
            + written.fixed().said(),
        written.declared());
  }

  /** Whether an initializer can run when the holder is made: constants and new objects of them. */
  private static boolean initializedAlone(Expression init) {
    for (Effects.Effect e : Effects.of(init).all()) {
      switch (e.kind()) {
        case NAME -> {
          IVariableBinding v = e.variable();
          if (!(v.isField()
              && Modifier.isStatic(v.getModifiers())
              && Modifier.isFinal(v.getModifiers()))) {
            return false;
          }
        }
        case CREATE, NEW_ARRAY, MAY_THROW -> {}
        default -> {
          return false;
        }
      }
    }
    return true;
  }
}
