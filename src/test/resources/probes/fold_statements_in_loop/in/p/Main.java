package p;
import java.util.ArrayList;
import java.util.List;
public class Main {
  static void step(List<Integer> out, int v) {
    out.add(v);
    out.add(v + 1);
  }
  public static void main(String[] args) {
    List<Integer> out = new ArrayList<>();
    int i = 0;
    while (i < 3) {
      out.add(i++);
      out.add(i++ + 1);
    }
    System.out.println(out);
  }
}
