package p;

public class Helper {
  public record Range(byte[] array, int length) {
    public int length() {
      return Math.max(0, length);
    }
  }

  static int size(Range r) {
    return r.length + r.array.length;
  }
}
