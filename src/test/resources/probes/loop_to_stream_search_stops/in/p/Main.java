package p;
import java.util.List;
public class Main {
  static String log = "";
  static boolean big(int x) { log += x; return x > 1; }
  public static void main(String[] args) {
    boolean found = false;
    for (Integer x : List.of(1, 2, 3)) {
      if (big(x)) {
        found = true;
        break;
      }
    }
    System.out.println(found + " " + log);
  }
}
