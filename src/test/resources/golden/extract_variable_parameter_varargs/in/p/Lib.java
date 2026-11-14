package p;

class Lib {
  static int size(java.util.List<String> xs, String... more) {
    return (xs.size() + 1) * more.length;
  }

  static int use() {
    return size(java.util.List.of("a"), "b", "c");
  }
}
