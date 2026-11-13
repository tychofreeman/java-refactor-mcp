package p;
public class Main {
  public static void main(String[] args) {
    Shop s = new Outlet();
    Outlet o = new Outlet();
    Shop plain = new Shop();
    System.out.println(s.priceOf(1) + " " + o.sale(2) + " " + plain.priceOf(3));
  }
}
