package p;
public class Main {
  static int n;
  static int log() { n = 5; return 0; }
  static int sum(int x) { return log() + x; }
  public static void main(String[] args) {
    int r = log() + n;
    System.out.println(r + " " + n);
  }
}
