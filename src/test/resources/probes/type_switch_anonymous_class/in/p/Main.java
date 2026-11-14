package p;
public class Main {
  static void show(Shape s) {
    switch (s) {
      case Circle c -> {
        Runnable r =
            new Runnable() {
              public void run() {
                System.out.println(c.toString());
              }

              public String toString() {
                return "anon";
              }
            };
        r.run();
      }
      case Square q -> System.out.println("square");
      default -> System.out.println("other");
    }
  }
  public static void main(String[] args) {
    show(new Circle());
  }
}
