package p;
public class Main {
  static int[][] box = {{1}, {2}};
  static int[] next() { box[0] = new int[] {9}; return box[1]; }
  static int pick(int[] x, int[] y) { return x[0] * 10 + y[0]; }
  static int sum(int[] a) {
    return Main.pick(Main.next(), a);
  }
  public static void main(String[] args) {
    System.out.println(sum(box[0]));
  }
}
