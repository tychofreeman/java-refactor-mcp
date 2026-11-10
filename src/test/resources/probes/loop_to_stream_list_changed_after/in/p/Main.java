package p;
import java.util.ArrayList;
import java.util.List;
public class Main {
  public static void main(String[] args) {
    List<String> out = new ArrayList<>();
    for (String s : List.of("a", "b")) {
      out.add(s.toUpperCase());
    }
    out.add("C");
    System.out.println(out);
  }
}
