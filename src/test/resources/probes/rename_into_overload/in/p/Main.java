package p;
public class Main {
  static String out = "";
  static void show(Object o) { out += "o"; }
  static void print(String s) { out += "s"; }
  public static void main(String[] args) {
    show("x");
    print("y");
    System.out.println(out);
  }
}
