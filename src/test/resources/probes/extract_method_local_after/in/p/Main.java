package p;
public class Main {
  static int n;
  public static void main(String[] args) {
    int x = ++n;
    n += x;
    System.out.println(x + " " + n);
  }
}
