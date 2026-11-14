package p;
public class Main {
  static final int OUT = 1;
  static int f(int x) { return x; }
  public static void main(String[] args) {
    System.out.println(f(Config.OUT) + OUT);
  }
}
class Config {
  static final int OUT = 2;
}
