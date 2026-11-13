package p;
public class Main {
  static String label(Color c) {
    String out =
        switch (c) {
          case RED -> "r";
          case GREEN -> "g";
        };
    return out;
  }
  public static void main(String[] args) {
    System.out.println(label(Color.GREEN));
    try {
      System.out.println(label(null));
    } catch (RuntimeException e) {
      System.out.println(e.getClass().getName());
    }
  }
}
