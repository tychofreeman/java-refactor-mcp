package p;
public class Main {
  static int n;
  static boolean ok() { return false; }
  static boolean bump() { n++; return true; }
  static boolean both(boolean x, boolean y) { return x && y; }
  public static void main(String[] a) {
    boolean r = ok() && bump();
    System.out.println(r + " " + n);
  }
}
