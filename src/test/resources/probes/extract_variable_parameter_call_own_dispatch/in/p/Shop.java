package p;
public class Shop {
  int priceOf(int n) {
    return rate(n) + n;
  }
  int rate(int n) { return n * 10; }
}
