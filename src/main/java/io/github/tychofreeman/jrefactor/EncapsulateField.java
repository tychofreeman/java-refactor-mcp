package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * encapsulate_field: a field becomes private, with a getter and a setter (bean style, unless
 * named), and every use in the project goes through them: a read is the getter call, an assignment
 * statement is the setter call, and a compound write, ++ or -- as a statement is the setter of the
 * getter's value combined. A write inside an expression, a receiver that would run twice, or a
 * plain assignment that also reads the field is refused, since those are not rewritten.
 */
final class EncapsulateField {

  private EncapsulateField() {}

  static Outcome run(
      SourceProject project, String rel, String name, String getterArg, String setterArg)
      throws Exception {
    Qualified q = Qualified.of(name, "Type.field, the class and the field to encapsulate");
    String typeName = q.type();
    String field = q.member();
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    TypeDeclaration owner = opened.topLevel(typeName);
    FieldDeclaration fd = null;
    for (FieldDeclaration f : owner.getFields()) {
      for (Object o : f.fragments()) {
        if (((VariableDeclarationFragment) o).getName().getIdentifier().equals(field)) {
          fd = f;
        }
      }
    }
    if (fd == null) {
      throw new Refusal(
          name + " is not a field of " + typeName + " in " + rel + ". Pass Type.field.");
    }
    if (fd.fragments().size() != 1) {
      throw new Refusal(
          "the declaration of "
              + field
              + " declares several fields. Split it into one declaration per field first.");
    }
    VariableDeclarationFragment frag = (VariableDeclarationFragment) fd.fragments().get(0);
    IVariableBinding fieldBinding = frag.resolveBinding();
    if (fieldBinding == null) {
      throw new Refusal(name + " does not resolve.");
    }
    boolean isStatic = Modifier.isStatic(fd.getModifiers());
    boolean isFinal = Modifier.isFinal(fd.getModifiers());
    String cap = Character.toUpperCase(field.charAt(0)) + field.substring(1);
    String getter = getterArg != null && !getterArg.isBlank() ? getterArg : "get" + cap;
    String setter = setterArg != null && !setterArg.isBlank() ? setterArg : "set" + cap;
    String fieldKey = fieldBinding.getKey();
    String typeText = source(text, fd.getType());
    if (!isStatic) {
      refuseOverride(project, file, owner, getter, List.of(), "getter", field);
      if (!isFinal) {
        String param = fieldBinding.getType().getErasure().getQualifiedName();
        refuseOverride(project, file, owner, setter, List.of(param), "setter", field);
      }
    }

    List<Path> candidates =
        project.javaFiles().stream().map(p -> p.toAbsolutePath().normalize()).distinct().toList();
    Map<Path, String> texts = new LinkedHashMap<>();
    List<Path> toParse = new ArrayList<>();
    for (Path p : candidates) {
      String content = p.equals(file) ? text : Files.readString(p);
      if (p.equals(file) || content.contains(field)) {
        texts.put(p, content);
        toParse.add(p);
      }
    }
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, toParse);

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    Set<Path> usedIn = new LinkedHashSet<>();
    int uses = 0;
    for (Path p : parsed.keySet()) {
      CompilationUnit cu = parsed.get(p);
      String fileText = texts.get(p);
      List<SimpleName> refs = new ArrayList<>();
      cu.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
              if (node.resolveBinding() instanceof IVariableBinding v
                  && v.isField()
                  && v.getKey().equals(fieldKey)
                  && !(node.getParent() instanceof VariableDeclarationFragment)) {
                refs.add(node);
              }
              return true;
            }
          });
      List<TextEdit> list = new ArrayList<>();
      for (SimpleName n : refs) {
        list.add(useEdit(fileText, n, fieldKey, field, getter, setter, isFinal, p));
        usedIn.add(p);
        uses++;
      }
      if (!list.isEmpty()) {
        edits.computeIfAbsent(p, k -> new ArrayList<>()).addAll(list);
      }
    }

    List<TextEdit> own = edits.computeIfAbsent(file, k -> new ArrayList<>());
    boolean wasPrivate = false;
    for (Object o : fd.modifiers()) {
      if (o instanceof org.eclipse.jdt.core.dom.Modifier mod) {
        if (mod.isPrivate()) {
          wasPrivate = true;
        } else if (mod.isPublic() || mod.isProtected()) {
          own.add(
              new TextEdit(
                  mod.getStartPosition(), mod.getStartPosition() + mod.getLength() + 1, ""));
        }
      }
    }
    if (!wasPrivate) {
      int at = fd.getType().getStartPosition();
      own.add(new TextEdit(at, at, "private "));
    }
    String moved =
        (isStatic ? "static " : "")
            + "public "
            + typeText
            + " "
            + getter
            + "() {\n    return "
            + (isStatic ? typeName + "." : "")
            + field
            + ";\n  }";
    if (!isFinal) {
      moved +=
          "\n\n  "
              + (isStatic ? "static " : "")
              + "public void "
              + setter
              + "("
              + typeText
              + " "
              + field
              + ") {\n    "
              + (isStatic ? typeName + "." : "this.")
              + field
              + " = "
              + field
              + ";\n  }";
    }
    own.addAll(MemberMove.appendTo(text, owner, moved));

    Stubs.Declared declared =
        MemberMove.gate(
            project,
            texts,
            edits,
            "encapsulating " + name,
            "Check that the uses of " + field + " the accessors replace still resolve.");
    return new Outcome(
        "encapsulated "
            + typeName
            + "."
            + field
            + " in "
            + rel
            + " with "
            + getter
            + (isFinal ? " (final: no setter)" : " and " + setter)
            + "; "
            + uses
            + " use"
            + (uses == 1 ? "" : "s")
            + " rewritten in "
            + usedIn.size()
            + " file"
            + (usedIn.size() == 1 ? "" : "s"),
        declared);
  }

  /** The edit for one reference to the field: a read, or a plain assignment statement. */
  private static TextEdit useEdit(
      String fileText,
      SimpleName n,
      String fieldKey,
      String field,
      String getter,
      String setter,
      boolean isFinal,
      Path p) {
    ASTNode target = n;
    if (n.getParent() instanceof FieldAccess fa && fa.getName() == n) {
      target = fa;
    } else if (n.getParent() instanceof QualifiedName qn && qn.getName() == n) {
      target = qn;
    }
    int line = Lines.number(fileText, n.getStartPosition());
    ASTNode up = target.getParent();
    if (up instanceof Assignment a && a.getLeftHandSide() == target) {
      if (isFinal) {
        throw new Refusal(
            field
                + " is final and written at line "
                + line
                + " of "
                + p.getFileName()
                + ". A final field's writes stay in its constructor, which encapsulate_field does"
                + " not change.");
      }
      if (!(a.getParent() instanceof ExpressionStatement st)) {
        throw new Refusal(
            field
                + " is written inside an expression at line "
                + line
                + " of "
                + p.getFileName()
                + ". encapsulate_field rewrites only a plain assignment statement; move the write"
                + " into a statement first.");
      }
      if (a.getOperator() != Assignment.Operator.ASSIGN) {
        String op = a.getOperator().toString();
        return combined(
            fileText,
            target,
            n,
            st,
            op.substring(0, op.length() - 1),
            operand(fileText, a.getRightHandSide()),
            getter,
            setter,
            field,
            line,
            p);
      }
      if (refersTo(a.getRightHandSide(), fieldKey)) {
        throw new Refusal(
            "the statement at line "
                + line
                + " of "
                + p.getFileName()
                + " reads and writes "
                + field
                + ". Split it: read the value into a variable, then assign it.");
      }
      String prefix = fileText.substring(target.getStartPosition(), n.getStartPosition());
      int s = st.getStartPosition();
      String rhs = source(fileText, a.getRightHandSide());
      return new TextEdit(s, s + st.getLength(), prefix + setter + "(" + rhs + ");");
    }
    if (isIncDec(up, target)) {
      if (!(up.getParent() instanceof ExpressionStatement st)) {
        throw new Refusal(
            field
                + " is changed with ++ or -- inside an expression at line "
                + line
                + " of "
                + p.getFileName()
                + ". encapsulate_field rewrites such a write only as a statement of its own; move"
                + " it into one first.");
      }
      boolean increment =
          up instanceof PrefixExpression pe
              ? pe.getOperator() == PrefixExpression.Operator.INCREMENT
              : ((PostfixExpression) up).getOperator() == PostfixExpression.Operator.INCREMENT;
      return combined(
          fileText, target, n, st, increment ? "+" : "-", "1", getter, setter, field, line, p);
    }
    int s = n.getStartPosition();
    return new TextEdit(s, s + n.getLength(), getter + "()");
  }

  private static boolean isIncDec(ASTNode up, ASTNode target) {
    if (up instanceof PrefixExpression pe) {
      return pe.getOperand() == target
          && (pe.getOperator() == PrefixExpression.Operator.INCREMENT
              || pe.getOperator() == PrefixExpression.Operator.DECREMENT);
    }
    if (up instanceof PostfixExpression po) {
      return po.getOperand() == target;
    }
    return false;
  }

  private static boolean refersTo(Expression e, String key) {
    boolean[] found = {false};
    e.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName node) {
            if (node.resolveBinding() instanceof IVariableBinding v && v.getKey().equals(key)) {
              found[0] = true;
            }
            return true;
          }
        });
    return found[0];
  }

  private static String source(String text, ASTNode node) {
    return text.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
  }

  /**
   * The setter call that replaces {@code statement}, a write of the field through {@code target}
   * ({@code n} its name): the getter's value combined with {@code operand} by {@code op}, as {@code
   * setX(getX() + 1);}. Refused when the receiver before the name is not a name or {@code this}: it
   * would run twice.
   */
  private static TextEdit combined(
      String fileText,
      ASTNode target,
      SimpleName n,
      Statement statement,
      String op,
      String operand,
      String getter,
      String setter,
      String field,
      int line,
      Path p) {
    if (target instanceof FieldAccess fa
        && !(fa.getExpression() instanceof ThisExpression || fa.getExpression() instanceof Name)) {
      String receiver = source(fileText, fa.getExpression());
      throw new Refusal(
          field
              + " is changed through "
              + receiver
              + " at line "
              + line
              + " of "
              + p.getFileName()
              + ", and as a setter call "
              + receiver
              + " would run twice. Put it in a variable first, and change the field through that.");
    }
    String prefix = fileText.substring(target.getStartPosition(), n.getStartPosition());
    int s = statement.getStartPosition();
    return new TextEdit(
        s,
        s + statement.getLength(),
        prefix + setter + "(" + prefix + getter + "() " + op + " " + operand + ");");
  }

  /** {@code e}'s source, in parentheses unless it is a name, a literal, a call or already so. */
  private static String operand(String fileText, Expression e) {
    String text = source(fileText, e);
    boolean bare =
        e instanceof Name
            || e instanceof NumberLiteral
            || e instanceof StringLiteral
            || e instanceof CharacterLiteral
            || e instanceof BooleanLiteral
            || e instanceof ParenthesizedExpression
            || e instanceof MethodInvocation
            || e instanceof FieldAccess;
    return bare ? text : "(" + text + ")";
  }

  /**
   * Refuses an instance accessor that would override a supertype's method, or be overridden by a
   * subtype's: a call or a read of the field would then run another body than before.
   */
  private static void refuseOverride(
      SourceProject project,
      Path file,
      TypeDeclaration owner,
      String accessor,
      List<String> params,
      String label,
      String field)
      throws IOException {
    Overrides.Clash clash = Overrides.of(project, file, owner.resolveBinding(), accessor, params);
    if (clash == null) {
      return;
    }
    String fix = " Pass " + label + " with another name, or rename " + clash.method() + " first.";
    if (clash.subtype() == null) {
      throw new Refusal(
          "the "
              + label
              + " "
              + accessor
              + "("
              + String.join(
                  ", ", params.stream().map(p -> p.substring(p.lastIndexOf('.') + 1)).toList())
              + ") would override "
              + clash.method()
              + ", so a call of it on a "
              + owner.getName().getIdentifier()
              + " would use the field."
              + fix);
    }
    throw new Refusal(
        clash.method()
            + " would then override the "
            + label
            + " "
            + accessor
            + ", so a use of "
            + field
            + " on a "
            + clash.subtype().getName()
            + " would run that body."
            + fix);
  }
}
