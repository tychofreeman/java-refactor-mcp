package p;
public class Main {
  public static void main(String[] args) {
    new Sub().tick();
  }
}
class Holder {
  private final Counter counter = new Counter();
  void tick() { counter.reset(); }
}
class Sub extends Holder {
  void reset() { System.out.println("sub reset"); }
}
