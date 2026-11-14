package p;

abstract class Base<T> {
  final String made;

  Base(Object o) {
    made = "object " + o;
    System.out.println("Base(Object) " + o);
  }

  Base(String s) {
    made = "string " + s;
    System.out.println("Base(String) " + s);
  }

  abstract T get();
}

class Box<T> extends Base<T> {
  private final T value;

  Box(T value, String label) {
    super(label);
    this.value = value;
  }

  Box(T value) {
    super(value);
    this.value = value;
  }

  T get() {
    return value;
  }

  int size() {
    return made.length();
  }
}

public class Main {
  public static void main(String[] args) {
    Box<Integer> a = new Box<>(7, "seven");
    Box<String> b = new Box<>("eight");
    System.out.println(a.get() + " " + a.size() + " " + a.made);
    System.out.println(b.get() + " " + b.size() + " " + b.made);
  }
}
