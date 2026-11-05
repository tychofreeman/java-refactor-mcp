package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;

/**
 * Headless JDT Core parsing with bindings resolved. No workspace, no OSGi runtime. Validated by
 * spikes/jdt-headless.
 */
final class Jdt {

  static final String SOURCE_LEVEL = JavaCore.VERSION_21;

  private Jdt() {}

  /** The property of a unit parsed from a project's file that holds the file's absolute path. */
  static final String FILE = "jrefactor.file";

  /** The file {@code unit} was parsed from, or null for text parsed alone. */
  static Path fileOf(CompilationUnit unit) {
    return unit.getProperty(FILE) instanceof Path p ? p : null;
  }

  /** Parses {@code source} without resolving anything: its problems are syntax errors only. */
  static CompilationUnit parseSyntax(String source) {
    return (CompilationUnit) syntaxParser(ASTParser.K_COMPILATION_UNIT, source).createAST(null);
  }

  /** {@code content} parsed as the members of a type, syntax only. */
  static ASTNode parseMembers(String content) {
    return syntaxParser(ASTParser.K_CLASS_BODY_DECLARATIONS, content).createAST(null);
  }

  /** A parser of {@code kind} for {@code source}, with the options every parser here uses. */
  private static ASTParser syntaxParser(int kind, String source) {
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setKind(kind);
    parser.setSource(source.toCharArray());
    parser.setCompilerOptions(options());
    return parser;
  }

  /** The compiler options for {@link #SOURCE_LEVEL}: the source level, compliance and target. */
  private static Map<String, String> options() {
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(SOURCE_LEVEL, options);
    return options;
  }

  /**
   * Parses {@code source}, the text of {@code file} inside {@code project}. Types from other files
   * resolve from the project's source path; library types resolve only from the project's
   * classpath. Problems are recovered, not thrown.
   */
  static CompilationUnit parse(SourceProject project, Path file, String source) {
    ASTParser parser = parser(project);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setSource(source.toCharArray());
    parser.setUnitName(project.relative(file));
    CompilationUnit unit = (CompilationUnit) parser.createAST(null);
    unit.setProperty(FILE, file.toAbsolutePath().normalize());
    return unit;
  }

  /**
   * Parses {@code files} of {@code project} as they are on disk, in one batch that shares one
   * environment, and hands each unit to {@code each} as it is done. The same units as {@link
   * #parse} of each file, many times faster on a large project: each type is resolved once, not
   * once per file that uses it.
   */
  static void parseAll(
      SourceProject project, List<Path> files, BiConsumer<Path, CompilationUnit> each) {
    String[] paths = files.stream().map(Path::toString).toArray(String[]::new);
    String[] encodings = new String[paths.length];
    java.util.Arrays.fill(encodings, "UTF-8");
    parser(project)
        .createASTs(
            paths,
            encodings,
            new String[0],
            new FileASTRequestor() {
              @Override
              public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                ast.setProperty(FILE, Path.of(sourceFilePath).toAbsolutePath().normalize());
                each.accept(Path.of(sourceFilePath), ast);
              }
            },
            null);
  }

  /**
   * Each of {@code files} parsed as {@link #parseAll(SourceProject, List, BiConsumer)} does, in
   * their order.
   */
  static Map<Path, CompilationUnit> parseAll(SourceProject project, List<Path> files) {
    Map<Path, CompilationUnit> parsed = new java.util.HashMap<>();
    parseAll(project, files, parsed::put);
    Map<Path, CompilationUnit> ordered = new java.util.LinkedHashMap<>();
    for (Path file : files) {
      ordered.put(file, parsed.get(file));
    }
    return ordered;
  }

  /**
   * A parser for Java 21 that resolves bindings against the project's source path and classpath.
   */
  private static ASTParser parser(SourceProject project) {
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setResolveBindings(true);
    parser.setBindingsRecovery(true);
    parser.setCompilerOptions(options());
    String[] sourcepath = project.sourceRoots().stream().map(Path::toString).toArray(String[]::new);
    String[] encodings = new String[sourcepath.length];
    java.util.Arrays.fill(encodings, "UTF-8"); // one per source path entry, or JDT throws
    parser.setEnvironment(project.classpath().toArray(String[]::new), sourcepath, encodings, true);
    return parser;
  }
}
