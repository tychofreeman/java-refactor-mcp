package p;
import java.util.List;
public class Main {
  static String log = "";
  static boolean fails(String s) { log += s; return s.startsWith("b"); }
  public static void main(String[] args) {
    int status = 0;
    for (String s : List.of("a", "b", "c", "bb")) {
      if (fails(s)) {
        status = 2;
      }
    }
    System.out.println(status + " " + log);
  }
}
