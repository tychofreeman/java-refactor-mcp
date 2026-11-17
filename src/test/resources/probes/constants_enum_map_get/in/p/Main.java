package p;
import java.util.HashMap;
import java.util.Map;
public class Main {
  public static void main(String[] args) {
    Map<Integer, String> names = new HashMap<>();
    names.put(1, "shipped");
    int state = Codes.SHIPPED;
    System.out.println(names.get(state));
  }
}
