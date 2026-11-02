package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;

/** Reads Java source for a model: a file with method bodies elided, or a range of its lines. */
public final class Inspect {

  private Inspect() {}

  /** The file with each method, constructor and initializer body of two lines or more elided. */
  public static String file(SourceProject project, String file) throws IOException {
    Path path = javaFile(project, file);
    String source = Files.readString(path);
    List<String> lines = source.lines().toList();
    CompilationUnit unit = Jdt.parse(project, path, source);
    List<int[]> elided = bodies(unit);

    int width = String.valueOf(lines.size()).length();
    StringBuilder out = new StringBuilder();
    out.append(file).append("  (");
    if (unit.getPackage() != null) {
      out.append("package ").append(unit.getPackage().getName().getFullyQualifiedName());
      out.append(", ");
    }
    out.append(lines.size()).append(" lines)\n\n");
    out.append("source, method bodies elided (inspect with start_line/end_line, to read one):\n");
    int next = 0;
    for (int line = 1; line <= lines.size(); line++) {
      if (next < elided.size() && elided.get(next)[0] == line) {
        int[] range = elided.get(next++);
        out.append(" ".repeat(width + 6))
            .append("… lines ")
            .append(range[0])
            .append('-')
            .append(range[1])
            .append("   [")
            .append(range[1] - range[0] + 1)
            .append(" lines]\n");
        line = range[1];
        continue;
      }
      appendLine(out, width, line, lines.get(line - 1));
    }
    return out.toString();
  }

  /** Lines {@code start} to {@code end} of the file (1-based, inclusive; end is clamped). */
  public static String lines(SourceProject project, String file, int start, int end)
      throws IOException {
    List<String> lines = Files.readString(javaFile(project, file)).lines().toList();
    if (start < 1 || end < start) {
      throw new Refusal(
          "start_line must be 1 or more and end_line at least start_line; got "
              + start
              + " and "
              + end
              + ". "
              + file
              + " has "
              + lines.size()
              + " lines.");
    }
    if (start > lines.size()) {
      throw new Refusal(
          "start_line "
              + start
              + " is past the end of "
              + file
              + ", which has "
              + lines.size()
              + " lines.");
    }
    int last = Math.min(end, lines.size());
    int width = String.valueOf(lines.size()).length();
    StringBuilder out = new StringBuilder();
    out.append(file)
        .append("  lines ")
        .append(start)
        .append('-')
        .append(last)
        .append(" of ")
        .append(lines.size())
        .append(":\n");
    for (int line = start; line <= last; line++) {
      appendLine(out, width, line, lines.get(line - 1));
    }
    return out.toString();
  }

  private static void appendLine(StringBuilder out, int width, int number, String text) {
    out.append(String.format("%" + width + "d", number));
    if (!text.isEmpty()) {
      out.append("  ").append(text);
    }
    out.append('\n');
  }

  /**
   * The 1-based line ranges strictly inside each body's braces, in order, for bodies with two inner
   * lines or more. Bodies inside an elided body are not visited.
   */
  private static List<int[]> bodies(CompilationUnit unit) {
    List<int[]> ranges = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(MethodDeclaration node) {
            return add(node.getBody());
          }

          @Override
          public boolean visit(Initializer node) {
            return add(node.getBody());
          }

          private boolean add(Block body) {
            if (body == null) {
              return false;
            }
            int open = unit.getLineNumber(body.getStartPosition());
            int close = unit.getLineNumber(end(body));
            if (close - open - 1 >= 2) {
              ranges.add(new int[] {open + 1, close - 1});
            }
            return false;
          }
        });
    return ranges;
  }

  private static int end(ASTNode node) {
    return node.getStartPosition() + node.getLength() - 1;
  }

  /** The path of {@code file} under the project root, or a refusal saying which files exist. */
  private static Path javaFile(SourceProject project, String file) throws IOException {
    Path root = project.root().toAbsolutePath().normalize();
    Path path = root.resolve(file).normalize();
    if (!path.startsWith(root) || !file.endsWith(".java")) {
      throw new Refusal(
          "file must be a .java file, relative to the project root "
              + root
              + "; got "
              + file
              + ".");
    }
    if (Files.isRegularFile(path)) {
      return path;
    }
    String dir = parent(file);
    List<String> siblings = new ArrayList<>();
    for (Path p : project.javaFiles()) {
      String rel = project.relative(p);
      if (parent(rel).equals(dir)) {
        siblings.add(rel.substring(dir.isEmpty() ? 0 : dir.length() + 1));
      }
    }
    String hint =
        siblings.isEmpty()
            ? " No Java files are in " + (dir.isEmpty() ? "the root" : dir) + "."
            : " Java files in "
                + (dir.isEmpty() ? "the root" : dir)
                + ": "
                + String.join(", ", siblings.stream().sorted().toList())
                + ".";
    throw new Refusal("No Java file " + file + " in the project." + hint);
  }

  private static String parent(String rel) {
    int slash = rel.lastIndexOf('/');
    return slash < 0 ? "" : rel.substring(0, slash);
  }
}
