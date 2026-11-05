package p;
public class Main {
  static int scale = 10;
  static int f(int x) { return x * scale; }
  public static void main(String[] args) {
    System.out.println(f(3));
  }
}
