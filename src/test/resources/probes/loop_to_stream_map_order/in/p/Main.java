package p;
import java.util.LinkedHashMap;
import java.util.Map;
public class Main {
  public static void main(String[] args) {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("b", 2);
    m.put("a", 1);
    m.put("c", 3);
    StringBuilder log = new StringBuilder();
    for (Map.Entry<String, Integer> e : m.entrySet()) {
      log.append(e.getKey()).append(e.getValue());
    }
    System.out.println(log);
  }
}
