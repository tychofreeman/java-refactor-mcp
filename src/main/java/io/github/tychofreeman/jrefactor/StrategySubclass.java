package io.github.tychofreeman.jrefactor;

import java.util.List;
import org.eclipse.jdt.core.dom.MethodDeclaration;

/**
 * The subclass subclass_to_strategy turns into a function, as rewriting its uses needs it: its
 * file, binding key, name, constructors and text.
 */
record StrategySubclass(
    Opened opened,
    String subKey,
    String subName,
    List<MethodDeclaration> subConstructors,
    String subText) {}
