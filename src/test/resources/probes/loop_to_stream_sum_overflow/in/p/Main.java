package p;
import java.util.List;
public class Main {
  public static void main(String[] args) {
    int sum = Integer.MAX_VALUE - 1;
    for (Integer x : List.of(1, 2, 3)) {
      sum += x;
    }
    System.out.println(sum);
  }
}
