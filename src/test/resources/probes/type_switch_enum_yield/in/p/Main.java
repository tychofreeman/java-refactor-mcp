package p;
public class Main {
  static int weight(Color c) {
    int w = switch (c) {
      case RED -> {
        int k = c.ordinal();
        switch (k) {
          case 0:
            yield 10;
          default:
            break;
        }
        int inner = switch (k) { case 1 -> 7; default -> { yield 3; } };
        yield inner + k;
      }
      case GREEN -> {
        int inner = switch (c.ordinal()) { case 1 -> 7; default -> { yield 3; } };
        yield inner;
      }
      default -> c.ordinal() * 100;
    };
    return w;
  }
  public static void main(String[] args) {
    for (Color c : Color.values()) {
      System.out.println(c + " " + weight(c));
    }
  }
}
