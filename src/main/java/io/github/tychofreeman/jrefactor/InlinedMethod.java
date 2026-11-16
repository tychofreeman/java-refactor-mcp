package io.github.tychofreeman.jrefactor;

import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;

/**
 * What inline knows of the method it inlines, read once from its declaration: the returned value
 * (null for a body of statements), the body, the statements before the last, the body's locals, its
 * uses of the instance, the range of the text inlined, each parameter's uses and how many, the
 * parameters, whether a subtype argument means the same for each, the method's name, and the
 * declaring file's text; and whether the body returns a value before its last statement, so that it
 * is inlined as statements, in place of a return.
 */
record InlinedMethod(
    Expression value,
    Block whole,
    List<Statement> prefix,
    Set<String> bodyLocals,
    List<ASTNode> thisUses,
    List<SimpleName> implicit,
    int bodyStart,
    int bodyEnd,
    List<InlineFunction.ParamUse> uses,
    int[] useCount,
    List<SingleVariableDeclaration> params,
    String name,
    String declaringText,
    boolean[] subtypeSafe, boolean early) {}
