package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * The imports an edit of a Java file needs: one for each type name the edit left unresolved, when
 * one class has that name, and none for those the edit left unused.
 */
final class Imports {

  private Imports() {}

  /**
   * The file's new text with its imports fixed. {@code lineShift} is how many lines the import
   * changes added (or, if negative, removed) above the first type, which moves every line an edit
   * reports; {@code notes} say what changed ("imported java.util.List", "removed import
   * java.util.Map").
   */
  record Fixed(String text, int lineShift, List<String> notes) {

    /** "; imported a.B; removed import c.D", or nothing. */
    String said() {
      return notes.isEmpty() ? "" : "; " + String.join("; ", notes);
    }
  }

  /**
   * Fixes the imports of {@code after}, the new text of {@code file}, against {@code before}, its
   * text before the edit. {@code chosen} are fully qualified names to use for ambiguous names.
   */
  static Fixed fix(
      SourceProject project, Path file, String before, String after, List<String> chosen)
      throws IOException {
    CompilationUnit unit = Jdt.parse(project, file, after);
    Set<String> unresolvedBefore = unresolved(Jdt.parse(project, file, before));
    Set<String> missing = unresolved(unit);
    missing.removeAll(unresolvedBefore);
    Set<String> usedBefore = names(Jdt.parseSyntax(before));
    Set<String> usedAfter = names(unit);

    Map<String, ImportDeclaration> imported = new HashMap<>();
    List<ImportDeclaration> remove = new ArrayList<>();
    for (Object o : unit.imports()) {
      ImportDeclaration i = (ImportDeclaration) o;
      if (i.isStatic() || i.isOnDemand()) {
        continue;
      }
      String simple = simpleName(i.getName().getFullyQualifiedName());
      imported.put(simple, i);
      if (usedBefore.contains(simple) && !usedAfter.contains(simple)) {
        remove.add(i);
      }
    }

    Map<String, String> picks = new HashMap<>();
    for (String name : chosen) {
      picks.put(simpleName(name), name);
    }
    Set<String> add = toImport(project, missing, imported, picks);
    // A name in a lambda is reported only once the names around it resolve, so look again with
    // the imports found so far, until no more are needed.
    Set<String> found = new TreeSet<>(add);
    while (!found.isEmpty()) {
      Set<String> still = unresolved(Jdt.parse(project, file, withImports(after, unit, add)));
      still.removeAll(unresolvedBefore);
      found = toImport(project, still, imported, picks);
      found.removeAll(add);
      add.addAll(found);
    }
    if (add.isEmpty() && remove.isEmpty()) {
      return new Fixed(after, 0, List.of());
    }

    List<ImportDeclaration> kept = new ArrayList<>();
    for (Object o : unit.imports()) {
      if (!remove.contains(o)) {
        kept.add((ImportDeclaration) o);
      }
    }
    TreeMap<Integer, StringBuilder> inserts = new TreeMap<>();
    for (String name : add) {
      int at = insertionPoint(after, unit, kept, name);
      inserts
          .computeIfAbsent(at, k -> new StringBuilder())
          .append("import ")
          .append(name)
          .append(";\n");
    }
    List<int[]> cuts = new ArrayList<>();
    for (ImportDeclaration i : remove) {
      cuts.add(
          new int[] {
            lineStart(after, i.getStartPosition()),
            lineEnd(after, i.getStartPosition() + i.getLength())
          });
    }
    if (kept.isEmpty() && add.isEmpty() && !cuts.isEmpty()) {
      int[] last = cuts.get(cuts.size() - 1);
      if (last[1] < after.length() && after.charAt(last[1]) == '\n') {
        last[1]++;
      }
    }
    // Apply from the end, so earlier offsets still hold; at one offset, cut before inserting.
    List<int[]> edits = new ArrayList<>(cuts);
    for (int at : inserts.keySet()) {
      edits.add(new int[] {at, at, -1});
    }
    edits.sort((a, b) -> b[0] != a[0] ? Integer.compare(b[0], a[0]) : Integer.compare(b[1], a[1]));
    StringBuilder text = new StringBuilder(after);
    int packageEnd = packageEnd(after, unit);
    for (int[] edit : edits) {
      if (edit.length == 3) {
        String lines = inserts.get(edit[0]).toString();
        boolean first = kept.isEmpty() && edit[0] == packageEnd && packageEnd > 0;
        text.insert(edit[0], first ? "\n" + lines : lines);
      } else {
        text.delete(edit[0], edit[1]);
      }
    }

    List<String> notes = new ArrayList<>();
    if (!add.isEmpty()) {
      notes.add("imported " + String.join(", ", add));
    }
    if (!remove.isEmpty()) {
      List<String> names = new ArrayList<>();
      for (ImportDeclaration i : remove) {
        names.add(i.getName().getFullyQualifiedName());
      }
      notes.add("removed import " + String.join(", ", names));
    }
    String fixed = text.toString();
    return new Fixed(fixed, count(fixed) - count(after), notes);
  }

  /**
   * The fully qualified names to import for the simple {@code names}: the one chosen, or the one
   * class with that name. Names already imported, or that no class has, are left out; a name that
   * several classes have is refused.
   */
  private static Set<String> toImport(
      SourceProject project,
      Set<String> names,
      Map<String, ImportDeclaration> imported,
      Map<String, String> picks)
      throws IOException {
    Set<String> add = new TreeSet<>();
    for (String name : names) {
      if (imported.containsKey(name)) {
        continue;
      }
      String pick = picks.get(name);
      if (pick == null) {
        List<String> candidates = index(project).candidates(name);
        if (candidates.isEmpty()) {
          continue;
        }
        if (candidates.size() > 1) {
          String last = candidates.get(candidates.size() - 1);
          throw new Refusal(
              name
                  + " could be "
                  + String.join(", ", candidates.subList(0, candidates.size() - 1))
                  + " or "
                  + last
                  + ". Pass imports with the one to use, as imports: [\""
                  + last
                  + "\"].");
        }
        pick = candidates.get(0);
      }
      add.add(pick);
    }
    return add;
  }

  /** {@code text} with imports of {@code names} after its package line, to see what resolves. */
  private static String withImports(String text, CompilationUnit unit, Set<String> names) {
    StringBuilder lines = new StringBuilder();
    for (String name : names) {
      lines.append("\nimport ").append(name).append(';');
    }
    int at = packageEnd(text, unit);
    return text.substring(0, at) + lines + "\n" + text.substring(at);
  }

  /** Where an import of {@code name} goes: in order among the kept non-static imports. */
  private static int insertionPoint(
      String text, CompilationUnit unit, List<ImportDeclaration> kept, String name) {
    ImportDeclaration lastPlain = null;
    for (ImportDeclaration i : kept) {
      if (i.isStatic()) {
        continue;
      }
      if (i.getName().getFullyQualifiedName().compareTo(name) > 0) {
        return lineStart(text, i.getStartPosition());
      }
      lastPlain = i;
    }
    if (lastPlain != null) {
      return lineEnd(text, lastPlain.getStartPosition() + lastPlain.getLength());
    }
    if (!kept.isEmpty()) {
      return lineStart(text, kept.get(0).getStartPosition());
    }
    return packageEnd(text, unit);
  }

  /** The offset just past the package line, or 0 with no package. */
  private static int packageEnd(String text, CompilationUnit unit) {
    PackageDeclaration p = unit.getPackage();
    return p == null ? 0 : lineEnd(text, p.getStartPosition() + p.getLength());
  }

  /** Simple type names the compiler could not resolve in {@code unit}. */
  private static Set<String> unresolved(CompilationUnit unit) {
    Set<String> names = new TreeSet<>();
    for (IProblem p : unit.getProblems()) {
      int id = p.getID();
      if (p.isError()
          && (id == IProblem.UndefinedType
              || id == IProblem.UndefinedName
              || id == IProblem.UnresolvedVariable)
          && p.getArguments().length > 0) {
        String name = p.getArguments()[0];
        if (!name.isEmpty() && Character.isUpperCase(name.charAt(0)) && !name.contains(".")) {
          names.add(name);
        }
      }
    }
    return names;
  }

  /**
   * Every simple name used in {@code unit} outside its package and import lines, Javadoc included.
   */
  private static Set<String> names(CompilationUnit unit) {
    Set<String> names = new TreeSet<>();
    unit.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(ImportDeclaration node) {
            return false;
          }

          @Override
          public boolean visit(PackageDeclaration node) {
            return false;
          }

          @Override
          public boolean visit(SimpleName node) {
            names.add(node.getIdentifier());
            return true;
          }
        });
    return names;
  }

  private static String simpleName(String qualified) {
    return qualified.substring(qualified.lastIndexOf('.') + 1);
  }

  private static int count(String text) {
    int n = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        n++;
      }
    }
    return n;
  }

  private static int lineStart(String text, int offset) {
    int i = offset;
    while (i > 0 && text.charAt(i - 1) != '\n') {
      i--;
    }
    return i;
  }

  private static int lineEnd(String text, int offset) {
    int i = text.indexOf('\n', offset);
    return i < 0 ? text.length() : i + 1;
  }

  /** Library class names by simple name, per classpath: the JDK's and the classpath's. */
  private static final Map<List<String>, Map<String, List<String>>> LIBRARIES =
      new ConcurrentHashMap<>();

  /** The classes named {@code name}, as fully qualified names, sorted, discouraged ones dropped. */
  private record Index(Map<String, List<String>> libraries, Map<String, List<String>> sources) {
    List<String> candidates(String name) {
      Set<String> all = new TreeSet<>();
      for (String c : libraries.getOrDefault(name, List.of())) {
        // A library's internal packages are not for its users (and the JDK's are not exported).
        if (!c.contains(".internal.")) {
          all.add(c);
        }
      }
      all.addAll(sources.getOrDefault(name, List.of()));
      List<String> preferred = new ArrayList<>();
      for (String c : all) {
        if (!discouraged(c)) {
          preferred.add(c);
        }
      }
      return preferred.isEmpty() ? new ArrayList<>(all) : preferred;
    }
  }

  private static boolean discouraged(String qualified) {
    return qualified.startsWith("java.awt.")
        || qualified.startsWith("javax.swing.")
        || qualified.startsWith("sun.")
        || qualified.startsWith("com.sun.")
        || qualified.startsWith("jdk.");
  }

  /** The index for {@code project}: libraries kept per classpath, sources read each time. */
  private static Index index(SourceProject project) throws IOException {
    Map<String, List<String>> libraries = LIBRARIES.get(project.classpath());
    if (libraries == null) {
      libraries = new HashMap<>();
      jdk(libraries);
      for (String entry : project.classpath()) {
        classpathEntry(Path.of(entry), libraries);
      }
      LIBRARIES.put(project.classpath(), libraries);
    }
    Map<String, List<String>> sources = new HashMap<>();
    for (Path source : project.javaFiles()) {
      String pkg = project.packageOf(source);
      String file = source.getFileName().toString();
      String simple = file.substring(0, file.length() - ".java".length());
      put(sources, pkg == null || pkg.isEmpty() ? simple : pkg + "." + simple);
    }
    return new Index(libraries, sources);
  }

  private static void jdk(Map<String, List<String>> into) throws IOException {
    FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
    try (Stream<Path> files = Files.walk(jrt.getPath("/modules"))) {
      files.forEach(p -> {
        // /modules/<module>/<package path>/<Name>.class
        if (p.getNameCount() > 3) {
          String cls = className(p.subpath(2, p.getNameCount()).toString());
          if (cls != null) {
            put(into, cls);
          }
        }
      });
    }
  }

  private static void classpathEntry(Path entry, Map<String, List<String>> into)
      throws IOException {
    if (Files.isDirectory(entry)) {
      try (Stream<Path> files = Files.walk(entry)) {
        files.forEach(p -> {
          String cls = className(entry.relativize(p).toString());
          if (cls != null) {
            put(into, cls);
          }
        });
      }
    } else if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
      try (ZipFile jar = new ZipFile(entry.toFile())) {
        jar.stream()
            .forEach(e -> {
              String cls = className(e.getName());
              if (cls != null) {
                put(into, cls);
              }
            });
      }
    }
  }

  /** "java/util/List.class" to "java.util.List"; null for nested, module and package files. */
  private static String className(String path) {
    if (!path.endsWith(".class") || path.contains("$") || path.contains("-")) {
      return null;
    }
    String dotted = path.substring(0, path.length() - ".class".length()).replace('/', '.');
    return dotted.contains(".") ? dotted : null;
  }

  private static void put(Map<String, List<String>> into, String qualified) {
    into.computeIfAbsent(simpleName(qualified), k -> new ArrayList<>()).add(qualified);
  }
}
