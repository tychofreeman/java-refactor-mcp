package p;
public class Main {
  static class X { static String name() { return "x"; } }
  public static void main(String[] args) { System.out.println(Y.name() + X.name()); }
}
class Y { static String name() { return "y"; } }
