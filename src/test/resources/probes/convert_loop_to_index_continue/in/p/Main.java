package p;
import java.util.List;
public class Main {
  public static void main(String[] args) {
    List<Integer> values = List.of(3, 4, 5, 6);
    int sum = 0;
    for (int v : values) if (v % 2 == 0) sum += v; else continue;
    int[] squares = {1, 4, 9};
    for (int s : squares) {
      if (s == 4) {
        continue;
      }
      sum += s;
    }
    System.out.println(sum);
  }
}
