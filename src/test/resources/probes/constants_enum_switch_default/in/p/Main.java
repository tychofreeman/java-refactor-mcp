package p;
public class Main {
  static int state = Codes.OPEN;
  static String label(int s) {
    switch (s) {
      case Codes.OPEN:
        return "open";
      case 1:
        return "shipped";
      default:
        return "other";
    }
  }
  public static void main(String[] args) {
    System.out.println(label(state));
    state = Codes.SHIPPED;
    System.out.println(label(state));
    state = Codes.CLOSED;
    System.out.println(label(state) + " " + (state == Codes.CLOSED) + " " + (state != 2));
  }
}
