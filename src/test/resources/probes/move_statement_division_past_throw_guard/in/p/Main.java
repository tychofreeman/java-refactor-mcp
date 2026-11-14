package p;
public class Main {
  static final RuntimeException ERR = new IllegalArgumentException("no items");
  static int average(int total, int count) {
    if (count == 0) throw ERR;
    int avg = total / count;
    return avg;
  }
  public static void main(String[] args) {
    try {
      System.out.println(average(10, 2));
      System.out.println(average(9, 0));
    } catch (RuntimeException e) {
      System.out.println(e.getClass().getSimpleName());
    }
  }
}
