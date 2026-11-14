package p;
public class Main {
  static final int STEP = 5;
  static long nanos(long seconds) { return seconds * 1_000_000_000; }
  public static void main(String[] args) {
    System.out.println(nanos(STEP));
  }
}
