package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int price(int days, int rate) {
    int litres = next() * 2;
    int extra = days > 3 ? next() : 0;
    int total = litres * days + extra + next();
    return total * rate;
  }
  public static void main(String[] args) {
    System.out.println(price(4, 3) + " " + n);
  }
}
