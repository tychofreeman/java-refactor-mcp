package p;
import java.util.List;
public class Main {
  static List<String> names = List.of("a", "b", "c");
  static void reset() { names = List.of("x"); }
  public static void main(String[] args) {
    String out = "";
    int n = 0;
    for (int i = 0; i < names.size(); i++) {
      out += names.get(i);
      if (n++ == 0) reset();
    }
    System.out.println(out);
  }
}
