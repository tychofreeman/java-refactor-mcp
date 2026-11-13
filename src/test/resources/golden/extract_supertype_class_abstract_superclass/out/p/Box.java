package p;

class Box<T> extends Sized<T> {
  private T value;

  Box(T value) {
    this.value = value;
  }

  T get() {
    return value;
  }
}
