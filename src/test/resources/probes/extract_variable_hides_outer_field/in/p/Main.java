package p;
public class Main {
  int total = 100;
  public static void main(String[] args) {
    System.out.println(new Main().new Inner().price(3));
  }
  class Inner {
    int price(int a) {
      int b = a * 2;
      return b + total;
    }
  }
}
