package p;
public class Main {
  static boolean low(String level) {
    return "low".equals(level) || level == null;
  }
  public static void main(String[] args) {
    System.out.println(low(Levels.LOW) + " " + low(Levels.HIGH) + " " + low(null));
  }
}
