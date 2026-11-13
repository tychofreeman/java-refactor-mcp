package p;
public class Outlet extends Shop {
  @Override
  int rate(int n) { return n * 100; }
  int sale(int n) { return priceOf(n); }
}
