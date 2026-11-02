import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;

/**
 * JDT spike: parse Java source with bindings resolved, headless (no workspace, no OSGi runtime).
 * Usage: java -cp "$(cat cp.txt)" Spike.java <sourceRoot> <relativeUnitPath> [extraClasspathJar...]
 */
public class Spike {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String unit = args[1];
        String[] extraCp = java.util.Arrays.copyOfRange(args, 2, args.length);

        String source = Files.readString(root.resolve(unit));

        // Classpath: JDK via includeRunningVMBootclasspath, plus any extra jars given.
        String[] classpath = extraCp;
        String[] sourcepath = { root.toString() };
        String[] encodings = { "UTF-8" };

        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_21);
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_21);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_21);

        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unit);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setCompilerOptions(options);
        parser.setEnvironment(classpath, sourcepath, encodings, true);

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        System.out.println("problems: " + cu.getProblems().length);
        for (var p : cu.getProblems()) {
            System.out.println("  problem: " + p.getMessage() + " @line " + cu.getLineNumber(p.getSourceStart()));
        }

        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                IMethodBinding m = node.resolveMethodBinding();
                int line = cu.getLineNumber(node.getStartPosition());
                if (m == null) {
                    System.out.println("  call " + node.getName() + " line " + line + ": UNRESOLVED");
                } else {
                    ITypeBinding decl = m.getDeclaringClass();
                    System.out.println("  call " + node.getName() + " line " + line
                            + ": " + (decl == null ? "?" : decl.getQualifiedName()) + "." + m.getName()
                            + " returns " + m.getReturnType().getName());
                }
                return true;
            }
        });
        System.out.println("done");
    }
}
