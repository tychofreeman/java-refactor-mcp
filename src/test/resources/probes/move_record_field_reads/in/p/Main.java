package p;
public class Main {
  record Range(int[] array, int length) {
    public int length() { return length; }
  }
  static int size(Range r) { return r.length + r.array.length; }
  public static void main(String[] args) {
    System.out.println(size(new Range(new int[3], 2)));
  }
}
