package p;
import java.util.List;
public class Main {
  static String log = "";
  static boolean keep(String x) { log += "k" + x; return !x.equals("b"); }
  static String twice(String x) { log += "t" + x; return x + x; }
  public static void main(String[] args) {
    for (String x : List.of("a", "b", "c")) {
      if (keep(x)) {
        String y = twice(x);
        log += "s" + y;
      }
    }
    System.out.println(log);
  }
}
