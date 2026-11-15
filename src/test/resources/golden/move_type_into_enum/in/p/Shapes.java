package p;

class Shapes {
  private enum Kind {
    ROUND,
    SQUARE
  }

  static Kind kindOf(String name) {
    return Kind.valueOf(name);
  }
}
