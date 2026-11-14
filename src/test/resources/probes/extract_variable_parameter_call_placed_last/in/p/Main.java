package p;
public class Main {
  static int cur = 1;
  static int bump() { return cur += 5; }
  static int sum(int a, int k) {
    return (a + bump()) * 100 + k;
  }
  public static void main(String[] args) {
    System.out.println(sum(cur, cur));
  }
}
