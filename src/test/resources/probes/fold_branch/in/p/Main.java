package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int pick(boolean c, int x) { return c ? x : 0; }
  public static void main(String[] a) {
    boolean c = false;
    int r = c ? next() : 0;
    System.out.println(r + " " + n);
  }
}
