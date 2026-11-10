package p;
import java.util.ArrayList;
import java.util.List;
public class Main {
  static String log = "";
  static List<String> split(String s) { log += "x" + s; return List.of(s, s); }
  public static void main(String[] args) {
    List<String> out = new ArrayList<>();
    for (String s : List.of("a", "bb", "c")) {
      if (s.length() > 1) {
        out.addAll(split(s));
      } else {
        out.add(s.toUpperCase());
      }
    }
    System.out.println(out + " " + log);
  }
}
