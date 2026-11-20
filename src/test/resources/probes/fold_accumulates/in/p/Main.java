package p;
public class Main {
  static String log = "";
  static String next(String s) {
    log += "[" + s + "]";
    return s;
  }
  static String press(String rpn, String text) {
    rpn += text;
    rpn += ",";
    return rpn;
  }
  static String run() {
    String rpn = "a";
    rpn += next("x");
    rpn += ",";
    rpn += next("y");
    rpn += ",";
    return rpn + log;
  }
  public static void main(String[] args) {
    System.out.println(run());
  }
}
