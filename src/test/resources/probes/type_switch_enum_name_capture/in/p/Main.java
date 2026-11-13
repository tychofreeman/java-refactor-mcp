package p;
public class Main {
  static String label(Color c) {
    String out =
        switch (c) {
          case RED -> new Helper().say();
          default -> "d";
        };
    return out;
  }
  public static void main(String[] args) {
    System.out.println(label(Color.RED) + " " + label(Color.GREEN));
  }
}
