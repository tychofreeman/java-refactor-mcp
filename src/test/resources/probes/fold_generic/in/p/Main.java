package p;
import java.util.List;
public class Main {
  static <T> T first(List<T> l) { return l.get(0); }
  public static void main(String[] args) {
    List<String> names = List.of("a", "b");
    String s = names.get(0);
    System.out.println(s.length() + s);
  }
}
