package io.github.tychofreeman.jrefactor;

/**
 * The method move moves, as a call's rewrite needs it: the source class's binding key and name, the
 * target class's name, the method's name without parameter types and as the model named it, whether
 * it is static, and its binding key.
 */
record MovedMethod(
    String sourceKey,
    String typeName,
    String to,
    String member,
    boolean isStatic,
    String name,
    String key) {}
