package p;

class Ex {
  static <T> T raise(T t) {
    return t;
  }

  static <T extends Exception> T raise(T t) throws T {
    throw t;
  }

  static String use() {
    return raise("x");
  }
}
