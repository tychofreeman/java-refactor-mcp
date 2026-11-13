package p;
public class Main {
  static String label(Color c) {
    String out =
        switch (c) {
          case RED -> "r" + 1;
          case GREEN -> null;
          default -> "cool";
        };
    return out;
  }
  public static void main(String[] args) {
    for (Color c : Color.values()) {
      System.out.println(c + " " + c.ordinal() + " " + label(c) + " " + Color.valueOf(c.name()));
    }
  }
}
