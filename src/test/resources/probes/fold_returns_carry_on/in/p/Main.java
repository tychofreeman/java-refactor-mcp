package p;
import java.util.ArrayList;
import java.util.List;
public class Main {
  static List<Integer> evens(List<Integer> xs) {
    List<Integer> out = new ArrayList<>();
    for (int x : xs) {
      if (x % 2 == 0) {
        out.add(x);
      }
    }
    return out;
  }
  static String report(List<Integer> ys) {
    List<Integer> kept = new ArrayList<>();
    for (int y : ys) {
      if (y % 2 == 0) {
        kept.add(y);
      }
    }
    kept.add(99);
    System.out.println("kept " + kept);
    return kept.size() + " of " + ys.size();
  }
  public static void main(String[] args) {
    System.out.println(report(List.of(1, 2, 3, 4, 6)));
  }
}
