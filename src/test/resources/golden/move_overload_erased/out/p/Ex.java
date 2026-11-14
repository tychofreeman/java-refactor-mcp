package p;

class Ex {
  static <T> T raise(T t) {
    return t;
  }

  static String use() {
    return raise("x");
  }
}
