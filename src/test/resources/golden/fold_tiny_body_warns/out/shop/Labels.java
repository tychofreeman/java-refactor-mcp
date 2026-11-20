package shop;

class Labels {
  static String join(String[] names) {
    String pending = empty();
    String out = empty();
    for (String name : names) {
      out += pending + name;
      pending = ", ";
    }
    return out;
  }

  static String empty() {
    return "";
  }
}
