package shop;

class Booth extends Kiosk {
  @Override
  int rate(Order order) {
    return 5;
  }
}
