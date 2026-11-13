package shop;

class Outlet extends Shop {
  int rate(Rush order) {
    return 9;
  }

  int sale(Rush o) {
    return priceOf(o);
  }
}
