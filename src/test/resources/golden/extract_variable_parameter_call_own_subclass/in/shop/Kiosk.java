package shop;

class Kiosk extends Shop {
  int rung(Order o) {
    return super.priceOf(o);
  }
}
