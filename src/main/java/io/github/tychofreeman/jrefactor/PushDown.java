package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * push_down: a method or a field moves from its class to each direct subclass in the project. The
 * class loses it, so a use through the class's own type is refused by the gate. A method that
 * overrides something the superclass declares is refused: moving it would change what the class
 * runs.
 */
final class PushDown {

  private PushDown() {}

  static Outcome run(SourceProject project, String rel, String name) throws IOException {
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      throw new Refusal(
          "name is Sub.member, the class and the method or field to move, not " + name + ".");
    }
    String subName = name.substring(0, dot);
    String member = name.substring(dot + 1);
    Path file = project.javaFile(rel);
    String text = Files.readString(file);
    CompilationUnit unit = Jdt.parse(project, file, text);
    TypeDeclaration sub = MemberMove.typeNamed(unit, subName);
    if (sub == null || sub.resolveBinding() == null) {
      throw new Refusal(
          subName + " is not a top-level class of " + rel + " that resolves. Fix it first.");
    }
    BodyDeclaration moved = MemberMove.member(sub, subName, member, rel, "push_down");
    ITypeBinding subType = sub.resolveBinding();
    if (moved instanceof MethodDeclaration m) {
      refuseOverride(m, subName, member, subType);
    }

    Holding holding = Holding.of(project, subType.getErasure().getName(), file);
    Map<Path, String> texts = holding.texts();
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    String movedText = MemberMove.textOf(text, moved);
    edits.computeIfAbsent(file, k -> new ArrayList<>()).add(MemberMove.removal(text, moved));

    int subclasses = 0;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      for (Object o : e.getValue().types()) {
        if (!(o instanceof TypeDeclaration t) || t.resolveBinding() == null) {
          continue;
        }
        ITypeBinding parent = t.resolveBinding().getSuperclass();
        if (parent == null
            || !parent
                .getErasure()
                .getQualifiedName()
                .equals(subType.getErasure().getQualifiedName())) {
          continue;
        }
        if (MemberMove.declares(t, member)) {
          throw new Refusal(
              t.getName().getIdentifier()
                  + " already declares "
                  + member
                  + ". Rename one of them, or leave it where it is.");
        }
        String subText = texts.getOrDefault(e.getKey(), Files.readString(e.getKey()));
        edits
            .computeIfAbsent(e.getKey(), k -> new ArrayList<>())
            .add(MemberMove.appendTo(subText, t, movedText));
        subclasses++;
      }
    }
    if (subclasses == 0) {
      throw new Refusal(
          subName
              + " has no direct subclass in the project's sources to push "
              + member
              + " into.");
    }

    Stubs.Declared declared =
        MemberMove.gate(
            project,
            texts,
            edits,
            "pushing " + subName + "." + member + " down to its subclasses",
            "Nothing was changed. Keep the member where a caller of "
                + subName
                + " can still reach it.");
    return new Outcome(
        "pushed "
            + subName
            + "."
            + member
            + " down to "
            + subclasses
            + " subclass"
            + (subclasses == 1 ? "" : "es"),
        declared);
  }

  /**
   * Refuses a method that overrides a supertype's: moving it would change what {@code sub} runs.
   */
  private static void refuseOverride(
      MethodDeclaration m, String subName, String method, ITypeBinding subType) {
    for (Object o : m.modifiers()) {
      if (o instanceof MarkerAnnotation a
          && a.getTypeName().getFullyQualifiedName().equals("Override")) {
        throw new Refusal(
            method
                + " overrides a method of a supertype. Pushing it down would change what "
                + subName
                + " runs; leave it where it is.");
      }
    }
    ITypeBinding superclass = subType.getSuperclass();
    if (superclass != null && !"java.lang.Object".equals(superclass.getQualifiedName())) {
      for (IMethodBinding b : superclass.getDeclaredMethods()) {
        if (b.getName().equals(method)) {
          throw new Refusal(
              superclass.getErasure().getName()
                  + " declares "
                  + method
                  + ", so pushing "
                  + subName
                  + "."
                  + method
                  + " down would change what "
                  + subName
                  + " runs. Leave it where it is.");
        }
      }
    }
  }
}
