package p;

public class Main {
  static int calls;

  static void run(boolean a, int[] xs) {
    calls = 0;
    if (a) calls += 100;
    else for (int x : xs) if (x > 1) calls++;
  }

  public static void main(String[] args) {
    run(true, new int[] {1, 2, 3});
    System.out.println(calls);
    run(false, new int[] {1, 2, 3});
    System.out.println(calls);
  }
}
