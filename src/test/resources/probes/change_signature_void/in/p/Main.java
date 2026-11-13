package p;

public class Main {
  interface Step {
    int run(int n);
  }

  static String log = "";

  static int note(int n) {
    log += "note" + n + " ";
    return n;
  }

  static int twice(int n) {
    if (n > 2) {
      return note(n * 2);
    }
    switch (n) {
      case 1:
        return note(1);
      default:
        log += "d ";
    }
    if (n == 0) return n + 1;
    log += "end" + n + " ";
    return n;
  }

  public static void main(String[] args) {
    Step[] steps = {
      n -> note(n + 10),
      n -> n * 3,
      n -> {
        if (n == 2) {
          return note(-n);
        }
        log += "block" + n + " ";
        return 0;
      },
      Main::twice
    };
    for (Step s : steps) {
      for (int n = 0; n < 4; n++) {
        s.run(n);
      }
    }
    System.out.println(log);
  }
}