package shop;

class Names {
  static String join(String first, String last) {
    String pending = push(last, first);
    return pending;
  }

  static String push(String held, String operator) {
    return operator + held;
  }
}
