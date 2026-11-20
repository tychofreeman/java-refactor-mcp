package shop;

class Rpn {
  static String build(String text, String more) {
    String rpn = "";
    rpn = press(rpn, text);
    rpn += " " + more.trim();
    return rpn;
  }

  private static String press(String rpn, String text) {
    rpn += " " + text;
    return rpn;
  }
}
