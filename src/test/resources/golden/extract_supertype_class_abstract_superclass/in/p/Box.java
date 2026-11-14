package p;

class Box<T> extends Base<T> {
  private T value;

  Box(T value) {
    this.value = value;
  }

  T get() {
    return value;
  }

  int size() {
    return 1;
  }
}
