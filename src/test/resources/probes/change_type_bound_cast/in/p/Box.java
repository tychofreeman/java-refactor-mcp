package p;
public class Box<T> {
  @SuppressWarnings("unchecked")
  T cast(Object o) { return (T) o; }
}
