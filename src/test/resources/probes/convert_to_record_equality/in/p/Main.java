package p;
import java.util.HashSet;
import java.util.Set;
public class Main {
  public static void main(String[] args) {
    Set<Point> s = new HashSet<>();
    s.add(new Point(1, 2));
    s.add(new Point(1, 2));
    System.out.println(s.size());
  }
}
final class Point {
  private final int x;
  private final int y;
  Point(int x, int y) {
    this.x = x;
    this.y = y;
  }
  int x() { return x; }
  int y() { return y; }
}
