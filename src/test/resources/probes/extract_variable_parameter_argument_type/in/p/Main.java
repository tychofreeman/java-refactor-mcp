package p;
public class Main {
  static long scale(long x) {
    return x * 1000000;
  }
  public static void main(String[] args) {
    int n = 5000;
    System.out.println(scale(n));
  }
}
