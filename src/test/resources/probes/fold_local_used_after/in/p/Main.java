package p;
import java.util.ArrayList;
import java.util.List;
public class Main {
  static void put(List<Integer> out, int w, int h) {
    int area = w * h;
    out.add(area);
  }
  public static void main(String[] args) {
    List<Integer> sink = new ArrayList<>();
    int x = 3, y = 4;
    int area = x * y;
    sink.add(area);
    System.out.println(sink + " " + area);
  }
}
