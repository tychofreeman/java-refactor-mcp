package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

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
    outline(lines, elided, out, width, 1, lines.size());
    return out.toString();
  }

  /**
   * Appends lines {@code from} to {@code to} of {@code lines} to {@code out}, numbered, each elided
   * range shown as its line span.
   */
  private static void outline(
      List<String> lines, List<int[]> elided, StringBuilder out, int width, int from, int to) {
    int next = 0;
    while (next < elided.size() && elided.get(next)[0] < from) {
      next++;
    }
    for (int line = from; line <= to; line++) {
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

  /** The most matching lines a pattern shows, over all files. */
  static final int MAX_MATCHES = 100;

  /**
   * The lines of {@code file} that {@code regex} matches somewhere in, numbered as {@link #lines}
   * numbers them; with no file, those of every Java file of the project, file by file. At most
   * {@link #MAX_MATCHES} lines, then how many more there are.
   */
  public static String pattern(SourceProject project, String file, String regex)
      throws IOException {
    Pattern p;
    try {
      p = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new Refusal(
          regex
              + " is not a regular expression: "
              + e.getDescription()
              + ". Escape ( [ { . * + ? with a backslash to match them as text.");
    }
    List<Path> files =
        file == null
            ? project.javaFiles().stream().sorted().toList()
            : List.of(javaFile(project, file));
    StringBuilder out = new StringBuilder();
    int shown = 0;
    int more = 0;
    for (Path f : files) {
      List<String> lines = Files.readString(f).lines().toList();
      List<Integer> hits = new ArrayList<>();
      for (int i = 0; i < lines.size(); i++) {
        if (p.matcher(lines.get(i)).find()) {
          hits.add(i + 1);
        }
      }
      if (hits.isEmpty()) {
        continue;
      }
      int room = MAX_MATCHES - shown;
      if (room <= 0) {
        more += hits.size();
        continue;
      }
      List<Integer> show = hits.subList(0, Math.min(room, hits.size()));
      more += hits.size() - show.size();
      out.append(project.relative(f))
          .append("  ")
          .append(hits.size())
          .append(hits.size() == 1 ? " line matches /" : " lines match /")
          .append(regex)
          .append("/:\n");
      int width = String.valueOf(show.get(show.size() - 1)).length();
      for (int line : show) {
        appendLine(out, width, line, lines.get(line - 1));
      }
      shown += show.size();
    }
    if (shown == 0) {
      return "no line of "
          + (file == null ? "a Java file in the project" : file)
          + " matches /"
          + regex
          + "/.";
    }
    if (more > 0) {
      out.append("... and ").append(more).append(" more; narrow the pattern, or give file.\n");
    }
    return out.toString();
  }

  /**
   * The declarations {@code names} name, found anywhere in the project: "Type" for a type, its
   * bodies elided as {@link #file} shows them; "Type.member" for each method of that name (every
   * overload), a field, an enum constant, in full, or a nested type, its bodies elided.
   * "Outer.Inner" names a nested type as a member of Outer.
   */
  public static String names(SourceProject project, List<String> names) throws IOException {
    StringBuilder out = new StringBuilder();
    for (String name : names) {
      int dot = name.lastIndexOf('.');
      String type = dot > 0 ? name.substring(0, dot) : name;
      String member = dot > 0 ? name.substring(dot + 1) : null;
      String simple = type.substring(type.lastIndexOf('.') + 1);
      boolean typeFound = false;
      int before = out.length();
      for (Path file : project.javaFiles()) {
        String text = Files.readString(file);
        if (!text.contains(simple)) {
          continue;
        }
        CompilationUnit unit = Jdt.parseSyntax(text);
        List<String> lines = text.lines().toList();
        String rel = project.relative(file).toString();
        for (AbstractTypeDeclaration t : Nodes.all(unit, AbstractTypeDeclaration.class)) {
          String chain = Names.typeLabelAt(t);
          if (!chain.equals(type) && !chain.endsWith("." + type)) {
            continue;
          }
          typeFound = true;
          if (member == null) {
            appendType(out, rel, lines, unit, t, name);
            continue;
          }
          for (Object o : t.bodyDeclarations()) {
            if (o instanceof MethodDeclaration m && m.getName().getIdentifier().equals(member)) {
              appendWhole(out, rel, lines, unit, m, name);
            } else if (o instanceof FieldDeclaration f
                && ((List<?>) f.fragments())
                    .stream()
                        .anyMatch(v ->
                            ((VariableDeclarationFragment) v)
                                .getName()
                                .getIdentifier()
                                .equals(member))) {
              appendWhole(out, rel, lines, unit, f, name);
            } else if (o instanceof AbstractTypeDeclaration nested
                && nested.getName().getIdentifier().equals(member)) {
              appendType(out, rel, lines, unit, nested, name);
            }
          }
          if (t instanceof EnumDeclaration en) {
            for (Object o : en.enumConstants()) {
              if (((EnumConstantDeclaration) o).getName().getIdentifier().equals(member)) {
                appendWhole(out, rel, lines, unit, (BodyDeclaration) o, name);
              }
            }
          }
        }
      }
      if (!typeFound) {
        throw new Refusal(
            "no type "
                + type
                + " in the project. Name a type as Type or Outer.Inner, and a member as"
                + " Type.member.");
      }
      if (out.length() == before) {
        throw new Refusal(
            type
                + " has no member "
                + member
                + ". inspect it by name (names: [\""
                + type
                + "\"]) to see what it declares.");
      }
    }
    return out.toString();
  }

  /** Appends {@code node}'s lines in full, under a header naming them. */
  private static void appendWhole(
      StringBuilder out,
      String rel,
      List<String> lines,
      CompilationUnit unit,
      ASTNode node,
      String label) {
    int first = unit.getLineNumber(node.getStartPosition());
    int last = unit.getLineNumber(Nodes.end(node) - 1);
    header(out, rel, first, last, label);
    int width = String.valueOf(last).length();
    for (int line = first; line <= last; line++) {
      appendLine(out, width, line, lines.get(line - 1));
    }
  }

  /** Appends type {@code t}'s lines with its bodies elided, under a header naming them. */
  private static void appendType(
      StringBuilder out,
      String rel,
      List<String> lines,
      CompilationUnit unit,
      AbstractTypeDeclaration t,
      String label) {
    int first = unit.getLineNumber(t.getStartPosition());
    int last = unit.getLineNumber(Nodes.end(t) - 1);
    header(out, rel, first, last, label + ", bodies elided");
    outline(lines, bodies(unit), out, String.valueOf(last).length(), first, last);
  }

  private static void header(StringBuilder out, String rel, int first, int last, String label) {
    out.append(rel)
        .append(first == last ? "  line " + first : "  lines " + first + "-" + last)
        .append(" (")
        .append(label)
        .append("):\n");
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

  /**
   * The path of {@code file} under the project root, or a refusal saying which files exist. Unlike
   * {@link SourceProject#javaFile}, any {@code .java} file under the root will do: reading one
   * needs no package, so a file outside the source roots (an example, a fixture) can be read too.
   */
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
    throw new Refusal("No Java file " + file + " in the project." + project.javaFilesHint(file));
  }

  /**
   * The project at a glance: its source roots, and in each its packages with their files' line
   * counts, the totals, why the classpath is empty when it is, and how to read further.
   */
  static String overview(SourceProject project) throws IOException {
    StringBuilder out =
        new StringBuilder(
            "Source roots, and in each its packages, one a line, with their files' line counts:\n");
    int packages = 0;
    int files = 0;
    int total = 0;
    List<Path> all = project.javaFiles();
    for (Path root : project.sourceRoots()) {
      String rel = project.relative(root);
      out.append(rel.isEmpty() || rel.equals(".") ? ". (the project root)" : rel).append('\n');
      Map<String, List<String>> byPackage = new TreeMap<>();
      Map<String, Integer> lines = new TreeMap<>();
      for (Path f : all) {
        if (!f.startsWith(root)) {
          continue;
        }
        String pkg = project.packageOf(f);
        if (pkg.isEmpty()) {
          pkg = "(the default package)";
        }
        int n = (int) Files.readString(f).lines().count();
        byPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add(f.getFileName() + " " + n);
        lines.merge(pkg, n, Integer::sum);
        files++;
        total += n;
      }
      for (Map.Entry<String, List<String>> e : byPackage.entrySet()) {
        out.append("  ")
            .append(e.getKey())
            .append(" (")
            .append(lines.get(e.getKey()))
            .append(" lines): ")
            .append(String.join(", ", e.getValue()))
            .append('\n');
        packages++;
      }
    }
    out.append('\n')
        .append(packages)
        .append(packages == 1 ? " package, " : " packages, ")
        .append(files)
        .append(files == 1 ? " file, " : " files, ")
        .append(total)
        .append(" lines\n");
    if (project.classpathProblem() != null) {
      out.append("\nThe classpath is empty, so library types do not resolve: ")
          .append(project.classpathProblem())
          .append('\n');
    }
    return out.append(
            "\nRead code with inspect: file for an outline (start_line and end_line for lines),"
                + " pattern to find lines, names for declarations by name.")
        .toString();
  }
}
