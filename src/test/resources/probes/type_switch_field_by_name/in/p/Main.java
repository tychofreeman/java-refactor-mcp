package p;
public class Main {
  double radius = 10;
  void show(Shape s) {
    switch (s) {
      case Circle c -> {
        for (int radius = 0; radius < 1; radius++) {}
        System.out.println(radius);
      }
      case Square q -> System.out.println(1);
    }
  }
  public static void main(String[] args) {
    new Main().show(new Circle());
  }
}
