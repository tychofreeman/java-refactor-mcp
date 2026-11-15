package p;
public class Main {
  static int n = 5;
  static int f() {
    int n = 1;
    n++;
    return n;
  }
  public static void main(String[] args) {
    System.out.println(f());
  }
}
