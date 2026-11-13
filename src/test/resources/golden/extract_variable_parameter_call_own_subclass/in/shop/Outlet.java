package shop;

class Outlet extends Shop {
  @Override
  int rate(Order order) {
    return 7;
  }

  int sale(Order o) {
    return priceOf(o);
  }
}
