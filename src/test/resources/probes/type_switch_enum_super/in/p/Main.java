package p;
public class Main {
  void show(Color c) {
    switch (c) {
      case RED -> System.out.println(super.getClass().getName());
      case GREEN -> System.out.println("green");
    }
  }
  public static void main(String[] args) {
    new Main().show(Color.RED);
  }
}
