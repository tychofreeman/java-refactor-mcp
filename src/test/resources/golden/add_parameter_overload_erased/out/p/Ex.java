package p;

class Ex {
  static <T> T raise(T t) {
    return t;
  }

  static <T extends Throwable> T raise(T t, int k) throws T {
    throw t;
  }

  static String use() {
    return raise("x");
  }
}
