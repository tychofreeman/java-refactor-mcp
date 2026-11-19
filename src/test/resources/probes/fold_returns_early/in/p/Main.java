package p;
import java.util.List;
public class Main {
  static int firstOver(List<Integer> xs, int limit) {
    for (int x : xs) {
      if (x > limit) {
        return x;
      }
    }
    return -1;
  }
  static int calls;
  static int limit() {
    calls++;
    return 2;
  }
  static int pick(List<Integer> ys) {
    System.out.println("pick " + ys);
    for (int y : ys) {
      if (y > limit()) {
        return y;
      }
    }
    return -1;
  }
  static int over(List<Integer> ys) {
    for (int y : ys) {
      if (y > 3) {
        return y;
      }
    }
    return -1;
  }
  public static void main(String[] args) {
    System.out.println(pick(List.of(1, 2, 3, 4)) + " " + calls);
    System.out.println(over(List.of(5, 1)));
    System.out.println(over(List.of(1, 2)));
  }
}
