package p;
public class Main {
  static int diff(int a, int b) { return a - b; }
  public static void main(String[] args) {
    int zero = args.length;
    int[] none = {};
    try {
      System.out.println(diff(1 / zero, none[0]));
    } catch (RuntimeException e) {
      System.out.println(e.getClass().getSimpleName());
    }
  }
}
