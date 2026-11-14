package p;

public record Range(byte[] array, int length) {
  public int length() {
    return length;
  }
}
