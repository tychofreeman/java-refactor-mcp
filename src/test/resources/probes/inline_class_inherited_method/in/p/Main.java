package p;
public class Main {
  public static void main(String[] args) {
    Base b = new Holder();
    b.reset();
    ((Holder) b).tick();
  }
}
class Base {
  void reset() { System.out.println("base reset"); }
}
class Holder extends Base {
  private final Counter counter = new Counter();
  void tick() { counter.reset(); }
}
