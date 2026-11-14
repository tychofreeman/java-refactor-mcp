package p;

class Use {
  static <R, E extends Throwable> R call(Task<R, E> task) throws E {
    return task.call();
  }

  static String name() {
    return "x";
  }

  static String run() throws Exception {
    return call(Use::name);
  }
}
