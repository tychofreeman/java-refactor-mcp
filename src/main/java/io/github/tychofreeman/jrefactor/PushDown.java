package io.github.tychofreeman.jrefactor;

import java.io.IOException;
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
    Qualified q = Qualified.of(name, "Sub.member, the class and the method or field to move");
    String subName = q.type();
    String member = q.member();
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    TypeDeclaration sub = opened.topLevel(subName);
    BodyDeclaration moved = MemberMove.member(sub, subName, member, rel, "push_down");
    ITypeBinding subType = sub.resolveBinding();
    if (moved instanceof MethodDeclaration m) {
      refuseOverride(m, subName, member);
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
        if (MemberMove.declares(t, moved)) {
          throw new Refusal(
              t.getName().getIdentifier()
                  + " already declares "
                  + member
                  + ". Rename one of them, or leave it where it is.");
        }
        String subText = Edits.textOf(texts, e.getKey());
        edits
            .computeIfAbsent(e.getKey(), k -> new ArrayList<>())
            .addAll(MemberMove.addTo(subText, t, movedText, Placement.rank(moved)));
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

    NameCheck.require(
        project, texts, edits, NameCheck.meanings(List.of(moved)), "pushing " + name + " down");
    Stubs.Declared declared =
        Edits.gatedFiles(
                project,
                texts,
                edits,
                List.of(),
                "pushing " + subName + "." + member + " down to its subclasses",
                "Nothing was changed. Keep the member where a caller of "
                    + subName
                    + " can still reach it.")
            .declared();
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
   * Any supertype counts, the superclass's superclasses and interfaces (a default method) too.
   */
  private static void refuseOverride(MethodDeclaration m, String subName, String method) {
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
    IMethodBinding mine = m.resolveBinding();
    if (mine == null) {
      return;
    }
    List<IMethodBinding> overridden = Hierarchy.overridden(mine);
    if (!overridden.isEmpty()) {
      throw new Refusal(
          overridden.get(0).getDeclaringClass().getErasure().getName()
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
