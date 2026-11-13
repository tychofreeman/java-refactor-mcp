package p;
public class Main {
  static int sum(int a, int b, int c) { return a + b + c; }
  public static void main(String[] args) {
    int x = 1;
    System.out.println(sum(x, x++, x * 2));
  }
}
