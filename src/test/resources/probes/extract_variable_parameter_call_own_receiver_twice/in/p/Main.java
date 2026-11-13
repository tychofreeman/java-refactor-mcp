package p;
public class Main {
  Shop shop = new Shop(1);
  int swap() { shop = new Shop(2); return 3; }
  public static void main(String[] args) {
    Main m = new Main();
    System.out.println(m.shop.priceOf(m.swap()));
  }
}
