package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.BodyDeclaration;

/** Where new members go: after {@code member}, before it, or (null) at the type's end. */
record Place(BodyDeclaration member, boolean before) {}
