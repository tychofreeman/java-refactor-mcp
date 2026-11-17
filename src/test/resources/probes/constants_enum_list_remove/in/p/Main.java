package p;
import java.util.ArrayList;
import java.util.List;
public class Main {
  public static void main(String[] args) {
    List<String> steps = new ArrayList<>(List.of("a", "b", "c"));
    int state = Codes.CLOSED;
    steps.remove(state);
    System.out.println(steps);
  }
}
