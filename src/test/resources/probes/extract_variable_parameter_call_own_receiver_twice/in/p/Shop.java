package p;
public class Shop {
  final int rate;
  Shop(int rate) { this.rate = rate; }
  int rate() { return rate; }
  int priceOf(int n) {
    return rate() * n;
  }
}
