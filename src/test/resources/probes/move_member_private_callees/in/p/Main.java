package p;
public class Main {
  int count;
  public static void main(String[] args) {
    Main m = new Main();
    m.all(3);
    System.out.println(m.count);
  }
  void all(int n) {
    for (int i = 0; i < n; i++) {
      one(i);
    }
  }
  private void one(int i) {
    count += twice(i);
  }
  private static int twice(int i) {
    return 2 * i;
  }
}
class Helper {}
