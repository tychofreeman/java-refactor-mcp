package p;
public class Main {
  static int n;
  static int cost(int[] item) { n += 10; return item[0] * 2 + n; }
  static int sum(int[] a, int b) {
    return Main.cost(a) + b + n;
  }
  public static void main(String[] args) {
    int[] item = {7};
    System.out.println(sum(item, n) + " " + sum(item, 1) + " " + n);
  }
}
