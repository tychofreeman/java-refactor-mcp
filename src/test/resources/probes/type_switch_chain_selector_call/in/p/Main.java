package p;
import java.util.Iterator;
import java.util.List;
public class Main {
  static void show(Iterator<Shape> it) {
    if (it.next() instanceof Circle c) {
      System.out.println("circle");
    } else if (it.next() instanceof Square q) {
      System.out.println("square");
    }
  }
  public static void main(String[] args) {
    show(List.<Shape>of(new Square(), new Circle()).iterator());
  }
}
