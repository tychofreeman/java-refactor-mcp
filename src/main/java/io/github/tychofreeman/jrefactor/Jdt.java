package io.github.tychofreeman.jrefactor;

import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Headless JDT Core parsing with bindings resolved. No workspace, no OSGi runtime. Validated by
 * spikes/jdt-headless.
 */
final class Jdt {

  static final String SOURCE_LEVEL = JavaCore.VERSION_21;

  private Jdt() {}

  /** Parses {@code source} without resolving anything: its problems are syntax errors only. */
  static CompilationUnit parseSyntax(String source) {
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(SOURCE_LEVEL, options);
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setSource(source.toCharArray());
    parser.setCompilerOptions(options);
    return (CompilationUnit) parser.createAST(null);
  }

  /**
   * Parses {@code source}, the text of {@code file} inside {@code project}. Types from other files
   * resolve from the project's source path; library types resolve only from the project's
   * classpath. Problems are recovered, not thrown.
   */
  static CompilationUnit parse(SourceProject project, java.nio.file.Path file, String source) {
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(SOURCE_LEVEL, options);
    options.put(JavaCore.COMPILER_SOURCE, SOURCE_LEVEL);
    options.put(JavaCore.COMPILER_COMPLIANCE, SOURCE_LEVEL);
    options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, SOURCE_LEVEL);

    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setSource(source.toCharArray());
    parser.setUnitName(project.relative(file));
    parser.setResolveBindings(true);
    parser.setBindingsRecovery(true);
    parser.setCompilerOptions(options);
    String[] sourcepath =
        project.sourceRoots().stream().map(java.nio.file.Path::toString).toArray(String[]::new);
    String[] encodings = new String[sourcepath.length];
    java.util.Arrays.fill(encodings, "UTF-8"); // one per source path entry, or JDT throws
    parser.setEnvironment(project.classpath().toArray(String[]::new), sourcepath, encodings, true);
    return (CompilationUnit) parser.createAST(null);
  }
}
