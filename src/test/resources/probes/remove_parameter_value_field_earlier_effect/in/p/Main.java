package p;
public class Main {
  int base = 2;
  int bump() { return ++base; }
  int sum(int a, int b, int c) { return a + b + c; }
  int run(int x) { return sum(bump(), x, x * base); }
  public static void main(String[] args) {
    System.out.println(new Main().run(5));
  }
}
