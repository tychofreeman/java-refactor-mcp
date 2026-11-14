package shop;

class Outlet extends Shop {
  int rate(Rush r) {
    return 5;
  }

  int total(Order order, Rush rush) {
    return priceOf(order) + priceOf(rush);
  }
}
