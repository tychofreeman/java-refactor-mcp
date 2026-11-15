package p;

record Sizes(int count) {
  static final Sizes NONE = new Sizes(0);

  public record Range(byte[] array, int length) {
    public int length() {
      return length;
    }
  }
}
