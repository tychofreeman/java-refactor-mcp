package p;
public class Main {
  public static void main(String[] args) {
    new Main().new Inner().run("m");
    System.out.println();
  }
  void log(String s) {
    System.out.print("[" + s + "]");
  }
  class Inner {
    void run(String m) {
      log("start");
      System.out.print(m);
    }
  }
}
