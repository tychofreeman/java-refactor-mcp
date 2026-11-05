package p;
import java.util.function.Supplier;
public class Main {
  public String toString() { return "main"; }
  Supplier<String> who() {
    return () -> toString();
  }
  public static void main(String[] args) {
    System.out.println(new Main().who().get());
  }
}
