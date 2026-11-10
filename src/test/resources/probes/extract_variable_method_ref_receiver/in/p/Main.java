package p;
import java.util.List;
public class Main {
  static String log = "";
  static List<Integer> items() { log += "i"; return List.of(1, 2); }
  static StringBuilder sink() { log += "s"; return new StringBuilder(); }
  public static void main(String[] args) {
    items().forEach(sink()::append);
    System.out.println(log);
  }
}
