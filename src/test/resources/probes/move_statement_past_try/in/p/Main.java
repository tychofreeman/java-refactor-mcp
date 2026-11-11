package p;
public class Main {
  static boolean run(boolean fail) {
    System.out.println("start " + fail);
    try {
      if (fail) {
        throw new IllegalStateException("x");
      }
    } catch (IllegalStateException e) {
      return false;
    }
    return true;
  }
  public static void main(String[] args) {
    System.out.println(run(false) + " " + run(true));
  }
}
