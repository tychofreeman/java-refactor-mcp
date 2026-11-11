package p;
public class Main {
  private final String a;
  private final int b;
  private final String c;
  Main(String a, int b) {
    this.a = a;
    System.out.println("between");
    this.b = b * 2;
    this.c = "c";
  }
  public static void main(String[] args) {
    Main m = new Main("x", 3);
    System.out.println(m.a + m.b + m.c);
  }
}
