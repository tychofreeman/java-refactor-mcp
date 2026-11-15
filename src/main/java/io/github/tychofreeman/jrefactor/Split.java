package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * split with kind phase (gorefactor's): the statements of a method body from a line to its end
 * become a new method, and the locals the statements before them compute for them are carried in a
 * new record. Composed of extract_function, move_parameter and extract_type, in one call.
 */
final class Split {

  private Split() {}

  static Outcome run(SourceProject project, Args args) throws Exception {
    String kind = args.required("kind");
    if (!kind.equals("phase")) {
      throw new Refusal(
          "kind "
              + kind
              + " is not built here; split takes kind phase. For a loop or a variable, use"
              + " extract_function and extract_variable.");
    }
    String rel = args.required("file");
    int line = args.integer("line");
    String newName = args.required("new_name");
    String type = args.required("type");
    Opened opened = Opened.of(project, rel);
    Statement at = null;
    for (Statement s : opened.onLine(line, Statement.class)) {
      if (s.getParent() instanceof Block b && b.getParent() instanceof MethodDeclaration) {
        at = s;
        break;
      }
    }
    if (at == null) {
      throw new Refusal(
          "no statement of a method body starts on line "
              + line
              + ". Give the line of the first statement of the second phase, directly in the"
              + " method's body.");
    }
    Block body = (Block) at.getParent();
    MethodDeclaration method = (MethodDeclaration) body.getParent();
    List<Statement> statements = new ArrayList<>();
    for (Object o : body.statements()) {
      statements.add((Statement) o);
    }
    int index = statements.indexOf(at);
    Statement last = statements.get(statements.size() - 1);
    Map<String, IVariableBinding> declared = new LinkedHashMap<>();
    for (Statement s : statements.subList(0, index)) {
      if (s instanceof VariableDeclarationStatement d) {
        for (Object f : d.fragments()) {
          IVariableBinding b = ((VariableDeclarationFragment) f).resolveBinding();
          if (b != null) {
            declared.put(b.getKey(), b);
          }
        }
      }
    }
    List<String> carried = new ArrayList<>();
    for (Statement s : statements.subList(index, statements.size())) {
      for (SimpleName n : Nodes.all(s, SimpleName.class)) {
        if (n.resolveBinding() instanceof IVariableBinding v
            && declared.containsKey(v.getVariableDeclaration().getKey())
            && !carried.contains(v.getName())) {
          carried.add(v.getName());
        }
      }
    }
    if (carried.isEmpty()) {
      throw new Refusal(
          "the statements before line "
              + line
              + " compute nothing the rest uses, so there is nothing to carry. Use"
              + " extract_function for the rest.");
    }
    String owner = Names.typeLabelAt(method);
    String label = owner + "." + method.getName().getIdentifier();
    int end = opened.unit().getLineNumber(Nodes.end(last) - 1);
    Tools.call(
        Tools.named("extract_function"),
        project,
        Map.of("file", rel, "start_line", line, "end_line", end, "name", newName));
    List<String> order = parameters(project, rel, owner, newName);
    // The carried values first, in their order: extract_type groups adjacent parameters.
    List<String> sorted = new ArrayList<>(order);
    sorted.sort((a, b) -> Boolean.compare(!carried.contains(a), !carried.contains(b)));
    for (int i = 0; i < sorted.size(); i++) {
      String want = sorted.get(i);
      if (!order.get(i).equals(want)) {
        Tools.call(
            Tools.named("move_parameter"),
            project,
            Map.of(
                "file",
                rel,
                "name",
                owner + "." + newName,
                "parameter",
                want,
                "before",
                order.get(i)));
        order = parameters(project, rel, owner, newName);
      }
    }
    List<String> members = sorted.subList(0, carried.size());
    String holder = Character.toLowerCase(type.charAt(0)) + type.substring(1);
    Tools.call(
        Tools.named("extract_type"),
        project,
        Map.of(
            "file", rel,
            "name", owner + "." + newName,
            "members", List.copyOf(members),
            "new_type", type,
            "as", holder));
    List<String> signature = new ArrayList<>();
    for (SingleVariableDeclaration p : declaration(project, rel, owner, newName)) {
      signature.add(Nodes.show(p));
    }
    String typeFile = project.relative(project.javaFile(rel).resolveSibling(type + ".java"));
    return Outcome.of(
        "split "
            + label
            + " at line "
            + line
            + ": the rest is "
            + newName
            + "("
            + String.join(", ", signature)
            + "), and "
            + type
            + " ("
            + typeFile
            + ") carries "
            + String.join(", ", members));
  }

  /** The parameter names of {@code owner.name} in {@code rel}, in order. */
  private static List<String> parameters(
      SourceProject project, String rel, String owner, String name) throws Exception {
    List<String> out = new ArrayList<>();
    for (SingleVariableDeclaration p : declaration(project, rel, owner, name)) {
      out.add(p.getName().getIdentifier());
    }
    return out;
  }

  /** The parameters of the method {@code name} of {@code owner} in {@code rel}, as it is now. */
  private static List<SingleVariableDeclaration> declaration(
      SourceProject project, String rel, String owner, String name) throws Exception {
    Opened now = Opened.of(project, rel);
    for (AbstractTypeDeclaration t : Nodes.all(now.unit(), AbstractTypeDeclaration.class)) {
      if (!Names.typeLabelAt(t).equals(owner)) {
        continue;
      }
      for (Object o : t.bodyDeclarations()) {
        if (o instanceof MethodDeclaration m && m.getName().getIdentifier().equals(name)) {
          List<SingleVariableDeclaration> out = new ArrayList<>();
          for (Object p : m.parameters()) {
            out.add((SingleVariableDeclaration) p);
          }
          return out;
        }
      }
    }
    throw new IllegalStateException(owner + "." + name + " is not in " + rel);
  }
}
