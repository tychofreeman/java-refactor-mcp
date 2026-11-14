package p;
public class Main {
  static final RuntimeException ERR = new IllegalArgumentException("zero");
  static int count;
  static int f(int n) {
    count++;
    if (n == 0) throw ERR;
    return 10 / n;
  }
  public static void main(String[] args) {
    try {
      f(2);
      f(0);
    } catch (IllegalArgumentException e) {
      System.out.print("caught ");
    }
    System.out.println(count);
  }
}
