package p;
public class Main {
  static int ticks = 0;
  static int tick(int i) { ticks++; return i + 1; }
  public static void main(String[] args) {
    for (int i = 0, j = 0; i < 12; i = tick(i), j += 2) {
      if (i % 3 == 0) continue;
      if (i == 10) break;
      System.out.print(i + ":" + j + " ");
    }
    System.out.println(ticks);
  }
}
