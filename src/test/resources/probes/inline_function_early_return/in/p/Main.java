package p;
public class Main {
  static int calls;
  static String next(String s) {
    calls++;
    System.out.println("next " + s);
    return s + calls;
  }
  static String tail(String code) {
    try {
      return last(next(code), 4);
    } finally {
      System.out.println("done " + calls);
    }
  }
  static String last(String s, int n) {
    if (s.length() <= n) {
      return s;
    }
    return s.substring(s.length() - n);
  }
  public static void main(String[] args) {
    System.out.println(tail("ab"));
    System.out.println(tail("RD-NORTH-004"));
  }
}
