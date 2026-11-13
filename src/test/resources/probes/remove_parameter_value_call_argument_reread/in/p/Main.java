package p;
public class Main {
  static int cur = 1;
  static int bump() { return cur += 5; }
  static int sum(int a, int b) { return a * 100 + b; }
  public static void main(String[] args) {
    System.out.println(sum(cur, bump() + cur));
  }
}
