package p;
public class Main {
  public static void main(String[] args) {
    BigCart c = new BigCart();
    c.count = 3;
    c.price = 4;
    System.out.println(c.withShipping() + " " + c.twice() + " " + c.calls);
  }
}
class Cart {
  int count;
  int price;
  int calls;
  int rate() { calls++; return price; }
  int total() { return count * rate(); }
  int withShipping() { return count * rate() + 5; }
}
class BigCart extends Cart {
  @Override
  int rate() { calls += 10; return price * 2; }
  int twice() { int t = count * rate(); return t * 2; }
}
