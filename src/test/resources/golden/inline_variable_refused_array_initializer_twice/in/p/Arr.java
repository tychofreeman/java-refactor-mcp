package p;

class Arr {
  static String first() {
    String[] names = {"a", "b"};
    names[0] = "c";
    return names[0];
  }
}
