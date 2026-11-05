package p;
import java.util.function.IntSupplier;
public class Main {
  public static void main(String[] args) {
    IntSupplier s =
        new IntSupplier() {
          int calls;
          public int getAsInt() {
            return ++calls;
          }
        };
    System.out.println(s.getAsInt() + " " + s.getAsInt());
  }
}
