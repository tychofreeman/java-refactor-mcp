package p;
public class Main {
  static int n;
  static boolean more() { return n < 3; }
  static boolean step() { n++; return true; }
  static boolean both(boolean a, boolean b) { return a && b; }
  public static void main(String[] args) {
    int loops = 0;
    while (both(more(), step())) {
      loops++;
    }
    System.out.println(loops + " " + n);
  }
}
