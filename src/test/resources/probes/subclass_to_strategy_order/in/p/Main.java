package p;

public class Main {
  static String id(String s) {
    Speaker.log += "arg(" + s + ")";
    return s;
  }

  public static void main(String[] args) {
    Speaker plain = new Speaker(id("a"));
    Loud loud = new Loud(id("b"));
    Speaker anon = new Loud();
    Speaker quiet = new Speaker();
    System.out.println(plain.twice("x") + loud.twice("y") + anon.twice("z") + quiet.twice("w"));
    System.out.println(Speaker.log);
  }
}