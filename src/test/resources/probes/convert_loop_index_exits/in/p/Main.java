package p;
import java.util.List;
public class Main {
  static String log = "";
  static String seen(String s) { log += s; return s; }
  public static void main(String[] args) {
    List<String> names = List.of("a", "", "b", "stop", "c");
    String out = "";
    outer:
    for (int i = 0; i < names.size(); i++) {
      String n = seen(names.get(i));
      if (n.isEmpty()) continue;
      if (n.equals("stop")) break outer;
      out += n + names.get(i);
    }
    System.out.println(out + " " + log);
  }
}
