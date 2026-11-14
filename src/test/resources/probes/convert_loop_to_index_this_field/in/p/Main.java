package p;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
public class Main {
  private final List<String> items = new ArrayList<>(List.of("a", "", "", "b"));

  void prune() {
    try {
      for (String s : items) {
        if (s.isEmpty()) {
          this.items.remove(s);
        }
      }
    } catch (ConcurrentModificationException e) {
      System.out.println("CME");
    }
    System.out.println(items);
  }

  public static void main(String[] args) {
    new Main().prune();
  }
}
