package p;
public class Main {
  static int n;
  static int[] cells = new int[3];
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] args) {
    cells[next()] = twice(next());
    System.out.println(java.util.Arrays.toString(cells) + " " + n);
  }
}
