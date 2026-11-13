package p;
public class Main {
  static int[][] box = {{1}, {2}};
  static int[] next() { box[0] = new int[] {9}; return box[1]; }
  static int first(int[] x) { return x[0]; }
  static int sum(int[] a, int[] b) {
    return Main.first(a) * 10 + b[0];
  }
  public static void main(String[] args) {
    System.out.println(sum(box[0], next()));
  }
}
