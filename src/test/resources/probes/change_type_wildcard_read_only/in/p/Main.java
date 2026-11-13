package p;
import java.util.List;
public class Main {
  static int total(List<Number> xs) {
    int t = 0;
    for (Number x : xs) { t += x.intValue(); }
    return t;
  }
  static void show(Object o) { System.out.println("object " + o); }
  static void show(int n) { System.out.println("int " + n); }
  public static void main(String[] args) {
    List<Number> xs = List.of(1, 2.5, 3L);
    show(total(xs));
    System.out.println(total(xs));
  }
}
