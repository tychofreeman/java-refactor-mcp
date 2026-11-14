package p;
public class Main {
  static String name = "field";

  static void show(boolean done) {
    if (done) {
      return;
    } else {
      String name = "local";
      System.out.println("else " + name);
    }
    System.out.println("after " + name);
  }

  public static void main(String[] args) {
    show(false);
  }
}
