package p;
import java.util.function.IntSupplier;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] args) {
    IntSupplier s = () -> {
      return twice(next());
    };
    System.out.println(n + " " + s.getAsInt() + " " + s.getAsInt() + " " + n);
  }
}
