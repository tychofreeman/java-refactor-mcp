package p;

import java.util.List;
import java.util.function.Function;

class Lib {
  static <T, R> R apply(Function<T, R> f, T t) {
    if (f != null) {
      return f.apply(t);
    }
    return null;
  }

  static int size(List<String> xs, int n) {
    return n;
  }

  static int use() {
    Function<String, Integer> fn = String::length;
    List<String> one = List.of("a");
    return size(one, one.size() + 1) + Lib.<String, Integer>apply(fn, "ab");
  }
}
