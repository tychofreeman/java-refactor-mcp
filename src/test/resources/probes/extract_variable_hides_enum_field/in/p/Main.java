package p;
public enum Main {
  EARTH(6.0);
  final double mass;
  Main(double mass) {
    this.mass = mass;
  }
  double g(double r) {
    double k = r * r;
    return mass / k;
  }
  public static void main(String[] args) {
    System.out.println(EARTH.g(2.0));
  }
}
