package p;

import java.util.function.Function;

class Lib {
  static <T, R> R apply(Function<T, R> f, T t) {
    return f.apply(t);
  }
}
