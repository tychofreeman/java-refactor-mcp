package p;

public class Main {
  interface Step {
    int run(int n);
  }

  static String log = "";

  static int inner(Step s, int n) {
    int got = s.run(n);
    log += "inner" + got + " ";
    return got + 1;
  }

  static int middle(Step s, int n) {
    int a = inner(s, n);
    try {
      a += inner(s, a);
    } finally {
      log += "finally ";
    }
    int b = a * 2;
    return b;
  }

  static int outer(Step s) {
    Runnable r = () -> log += inner(s, 5) + " ";
    r.run();
    return middle(s, 1);
  }

  public static void main(String[] args) {
    System.out.println(outer(n -> n * 3) + " " + log);
  }
}