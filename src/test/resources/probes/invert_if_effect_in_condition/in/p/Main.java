package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  public static void main(String[] args) {
    String r;
    if (next() > 1) {
      r = "big";
    } else {
      r = "small";
    }
    System.out.println(r + " " + n);
  }
}
