package p;
public class Main {
  static int n;
  static int log(String s) { System.out.println(s + " " + n); return ++n; }
  static int cost(int[] item) { System.out.println("cost " + n); n += 10; return item[0] * 2; }
  static int sum(int[] a, int b, int c) { return a[0] + b + c; }
  public static void main(String[] args) {
    int[] item = {7};
    System.out.println(sum(item, cost(item), log("last")));
  }
}
