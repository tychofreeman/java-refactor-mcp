package p;
import java.util.List;
public class Main {
  static final List<String> items = List.of("a", "b");

  static void show() {
    final List<String> others = List.of("x", "y");
    for (int i = 0; i < items.size(); i++) {
      List<String> items = others;
      System.out.println(items.get(i));
    }
  }

  public static void main(String[] args) {
    show();
  }
}
