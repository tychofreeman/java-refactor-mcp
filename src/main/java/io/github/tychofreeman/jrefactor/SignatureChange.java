package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TagElement;

/** The edits of one signature change, by file, applied together under the gate. */
final class SignatureChange {
  final MethodFamily family;
  final Map<Signatures.Source, List<TextEdit>> edits = new LinkedHashMap<>();

  SignatureChange(MethodFamily family) {
    this.family = family;
  }

  private void add(Signatures.Source file, int start, int end, String text) {
    edits.computeIfAbsent(file, k -> new ArrayList<>()).add(new TextEdit(start, end, text));
  }

  /** Replaces {@code node}'s text in {@code file} with {@code text}. */
  void replace(Signatures.Source file, ASTNode node, String text) {
    add(file, node.getStartPosition(), node.getStartPosition() + node.getLength(), text);
  }

  /** Replaces {@code start}-{@code end} of {@code file} with {@code text}. */
  void replaceRange(Signatures.Source file, int start, int end, String text) {
    add(file, start, end, text);
  }

  /** Inserts {@code text} at position {@code at} of {@code list}, which opens at {@code open}. */
  void insert(Signatures.Source file, List<?> list, int open, int bare, int at, String text) {
    if (list.isEmpty()) {
      if (open < 0) {
        add(file, bare, bare, "(" + text + ")");
      } else {
        add(file, open, open, text);
      }
    } else if (at == list.size()) {
      int end = Nodes.end(((ASTNode) list.get(list.size() - 1)));
      add(file, end, end, ", " + text);
    } else {
      int start = ((ASTNode) list.get(at)).getStartPosition();
      add(file, start, start, text + ", ");
    }
  }

  /** Removes the element at {@code at} of {@code list} with its comma. */
  void remove(Signatures.Source file, List<?> list, int at) {
    ASTNode e = (ASTNode) list.get(at);
    if (list.size() == 1) {
      add(file, e.getStartPosition(), Nodes.end(e), "");
    } else if (at < list.size() - 1) {
      add(file, e.getStartPosition(), ((ASTNode) list.get(at + 1)).getStartPosition(), "");
    } else {
      add(file, Nodes.end(((ASTNode) list.get(at - 1))), Nodes.end(e), "");
    }
  }

  /** Writes {@code params} as lambda {@code l}'s parameters, from its start to its arrow. */
  void lambda(Signatures.Lambda l, List<String> params) {
    LambdaExpression n = l.node();
    List<?> old = n.parameters();
    int from =
        old.isEmpty() ? n.getStartPosition() : Nodes.end(((ASTNode) old.get(old.size() - 1)));
    int arrow = l.file().text().indexOf("->", from);
    add(l.file(), n.getStartPosition(), arrow, "(" + String.join(", ", params) + ") ");
  }

  /** Puts the elements of {@code list} in {@code order}, keeping the separators in place. */
  void reorder(Signatures.Source file, List<?> list, List<Integer> order) {
    for (int k = 0; k < order.size(); k++) {
      if (order.get(k) != k) {
        ASTNode at = (ASTNode) list.get(k);
        ASTNode from = (ASTNode) list.get(order.get(k));
        add(
            file,
            at.getStartPosition(),
            Nodes.end(at),
            file.text().substring(from.getStartPosition(), Nodes.end(from)));
      }
    }
  }

  /** Removes the {@code @param} tag of the parameter at {@code at} from {@code d}'s Javadoc. */
  void removeParamTag(Signatures.Decl d, int at) {
    Javadoc doc = d.m().getJavadoc();
    if (doc == null) {
      return;
    }
    String name =
        ((SingleVariableDeclaration) d.m().parameters().get(at)).getName().getIdentifier();
    String text = d.file().text();
    for (Object o : doc.tags()) {
      TagElement tag = (TagElement) o;
      if ("@param".equals(tag.getTagName())
          && !tag.fragments().isEmpty()
          && tag.fragments().get(0) instanceof SimpleName n
          && n.getIdentifier().equals(name)) {
        int start = text.lastIndexOf('\n', tag.getStartPosition()) + 1;
        int end = text.indexOf('\n', Nodes.end(tag)) + 1;
        add(d.file(), start, end, "");
      }
    }
  }

  /** Applies the edits, fixes each file's imports, and writes them all under the gate. */
  Outcome apply(SourceProject project, String what, List<String> chosen) throws Exception {
    Map<Path, String> texts = new LinkedHashMap<>();
    Set<String> files = new TreeSet<>();
    List<String> notes = new ArrayList<>();
    for (Map.Entry<Signatures.Source, List<TextEdit>> e : edits.entrySet()) {
      Signatures.Source s = e.getKey();
      List<TextEdit> list = new ArrayList<>(e.getValue());
      list.sort((a, b) -> a.start() != b.start() ? b.start() - a.start() : b.end() - a.end());
      int limit = Integer.MAX_VALUE;
      for (TextEdit edit : list) {
        if (edit.end() > limit) {
          throw new Refusal(
              "calls of "
                  + family.label
                  + " are nested in each other's arguments in "
                  + s.rel()
                  + " (line "
                  + s.unit().getLineNumber(edit.start())
                  + "). Assign the inner call to a variable first.");
        }
        limit = edit.start();
      }
      String edited = TextEdit.apply(s.text(), list, 0);
      Imports.Fixed fixed = Imports.fix(project, s.file(), s.text(), edited, chosen);
      texts.put(s.file(), fixed.text());
      files.add(s.rel());
      if (!fixed.notes().isEmpty()) {
        notes.add(s.rel() + ": " + String.join("; ", fixed.notes()));
      }
    }
    Stubs.Declared stubs =
        Gate.applyOrRefuse(
            project,
            texts,
            what,
            "Nothing was changed. If a value or type does not resolve at some call, choose one"
                + " that does.");
    int d = family.decls.size();
    int c = family.calls.size();
    return new Outcome(
        what
            + " in "
            + d
            + (d == 1 ? " declaration" : " declarations")
            + " and "
            + c
            + (c == 1 ? " call" : " calls")
            + " ("
            + String.join(", ", files)
            + ")"
            + (notes.isEmpty() ? "" : "; " + String.join("; ", notes)),
        stubs);
  }
}
