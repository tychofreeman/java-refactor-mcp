package p;
import java.util.List;
public class Main {
  static String log = "";
  static List<Integer> items() { log += "i"; return List.of(1, 2); }
  static void show(int x) { log += x; }
  public static void main(String[] args) {
    items().forEach(x -> show(x * 10));
    System.out.println(log);
  }
}
