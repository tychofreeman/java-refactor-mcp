package p;
import java.util.ArrayList;
import java.util.List;
public class Main {
  static int made;
  static List<String> make() { made++; return new ArrayList<>(List.of("a")); }
  static int size(List<String> l) { return l.size() + l.size(); }
  public static void main(String[] args) {
    int r = size(make());
    System.out.println(r + " " + made);
  }
}
