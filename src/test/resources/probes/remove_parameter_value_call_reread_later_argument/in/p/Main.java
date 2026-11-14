package p;
public class Main {
  static int cur = 1;
  static int bump() { return cur += 5; }
  static int sum(int b, int a) { return b * 100 + a; }
  public static void main(String[] args) {
    System.out.println(sum(cur + bump(), cur));
  }
}
