package p;
public class Main {
  static int n;
  static int cost(int[] item) { n++; return item[0]; }
  static int sum(int[] a, boolean any) {
    if (any) {
      return Main.cost(a);
    }
    return 0;
  }
  public static void main(String[] args) {
    int[] item = {3};
    System.out.println(sum(item, false) + " " + n);
  }
}
