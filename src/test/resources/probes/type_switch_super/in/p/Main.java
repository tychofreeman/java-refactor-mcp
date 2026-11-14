package p;
public class Main extends Base {
  void show(Shape s) {
    switch (s) {
      case Circle c -> System.out.println(super.describe());
      case Square q -> System.out.println("square");
    }
  }
  public static void main(String[] args) {
    new Main().show(new Circle());
  }
}
