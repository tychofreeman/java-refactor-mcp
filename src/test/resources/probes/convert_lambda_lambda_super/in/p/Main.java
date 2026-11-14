package p;
import java.util.function.Supplier;
public class Main {
  Supplier<String> who() {
    return () -> super.getClass().getName();
  }
  public static void main(String[] args) {
    System.out.println(new Main().who().get());
  }
}
