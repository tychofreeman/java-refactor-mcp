package p;
public class Main {
  static Box b = new Box();
  static int bump() { b.count = 100; return 1; }
  public static void main(String[] args) {
    b.count += bump();
    b.count++;
    --b.count;
    b.count *= 2 + 1;
    System.out.println(b.count);
  }
}
class Box {
  int count;
}
