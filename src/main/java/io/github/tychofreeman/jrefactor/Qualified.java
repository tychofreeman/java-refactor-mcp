package io.github.tychofreeman.jrefactor;

/**
 * A name of the form {@code Type.member}, split at the first dot: the type is top-level, so its
 * name has none, and the member may have dots in its parameter types ({@code Sub.m(Map.Entry)}).
 */
record Qualified(String type, String member) {

  /**
   * {@code name} split; refused when it has no dot. {@code shape} says what it must be, as the
   * refusal names it: "Type.field, the class and the field to encapsulate".
   */
  static Qualified of(String name, String shape) {
    int dot = name.indexOf('.');
    if (dot < 0) {
      throw new Refusal("name is " + shape + ", not " + name + ".");
    }
    return new Qualified(name.substring(0, dot), name.substring(dot + 1));
  }
}
