package p;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class Main {
  public static void main(String[] args) {
    Map<Integer, List<String>> byLength = new HashMap<>();
    for (String s : List.of("aa", "b", "cc", "d", "eee")) {
      byLength.computeIfAbsent(s.length(), k -> new ArrayList<>()).add(s.toUpperCase());
    }
    byLength.get(1).add("z");
    System.out.println(byLength);
  }
}
