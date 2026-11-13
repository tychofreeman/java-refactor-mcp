package p;

import java.util.List;
import java.util.function.Function;

class Lib {
  static <T, R> R apply(Function<T, R> f, boolean has, T t) {
    if (has) {
      return f.apply(t);
    }
    return null;
  }

  static int size(List<String> xs) {
    return xs.size() + 1;
  }

  static int use() {
    Function<String, Integer> fn = String::length;
    return size(List.of("a")) + Lib.<String, Integer>apply(fn, fn != null, "ab");
  }
}
