package p;
public class Main {
  public static void main(String[] args) {
    Object a = new Point(1, 2);
    System.out.println(a.equals(new Point(1, 2)));
  }
}
final class Point {
  private final int x;
  private final int y;
  Point(int x, int y) {
    this.x = x;
    this.y = y;
  }
  public boolean equals(Point p) { return p == this; }
  @Override public int hashCode() { return x; }
  @Override public String toString() { return "Point"; }
}
