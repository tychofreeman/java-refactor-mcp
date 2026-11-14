package p;
public class Main {
  static void show(Shape s) {
    switch (s) {
      case Circle c -> {
        double radius = 2;
        System.out.println(c.radius * radius);
      }
      case Square q -> System.out.println(1);
    }
  }
  public static void main(String[] args) {
    show(new Circle());
  }
}
