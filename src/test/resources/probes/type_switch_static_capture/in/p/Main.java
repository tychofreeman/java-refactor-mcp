package p;
public class Main {
  static final double K = 1;
  static void show(Shape s) {
    switch (s) {
      case Circle c -> System.out.println(K);
      case Square q -> System.out.println(2);
    }
  }
  public static void main(String[] args) {
    show(new Circle());
  }
}
