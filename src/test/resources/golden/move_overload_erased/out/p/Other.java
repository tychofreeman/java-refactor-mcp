package p;

class Other {
  static <T extends Throwable> T raise(T t) throws T {
    throw t;
  }
}
