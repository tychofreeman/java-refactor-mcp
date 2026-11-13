package p;
public class Main {
  static int n;
  static int cost(int[] item) { return item[0] + n; }
  static int sum(int[] a) {
    n += 5;
    return Main.cost(a);
  }
  public static void main(String[] args) {
    System.out.println(sum(new int[] {1}));
  }
}
