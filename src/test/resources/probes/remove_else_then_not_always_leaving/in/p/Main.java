package p;
public class Main {
  static String f(boolean c, boolean d) {
    String r = "start";
    for (int i = 0; i < 1; i++) {
      if (c) {
        if (d) {
          return "d";
        }
      } else {
        r = "else";
      }
    }
    return r;
  }
  public static void main(String[] args) {
    System.out.println(f(true, false) + " " + f(false, false) + " " + f(true, true));
  }
}
