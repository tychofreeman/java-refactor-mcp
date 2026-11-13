package p;
public class Main {
  static int n;
  static int log(String s) { System.out.println(s + " " + n); return ++n; }
  static int cost(int[] item) { System.out.println("cost " + n); n += 10; return item[0] * 2; }
  static int sum(int a, int[] b, int c) {
    System.out.println("sum " + n);
    return a + b[0] + c;
  }
  public static void main(String[] args) {
    int[] item = {7};
    System.out.println(sum(log("first"), item, cost(item)));
    System.out.println(sum(n, item, cost((item))));
    System.out.println(n);
  }
}
