package p;

public class Main {
  static class Res implements AutoCloseable {
    final String name;

    Res(String name) {
      this.name = name;
    }

    @Override
    public void close() {
      System.out.println("close " + name);
    }
  }

  static Res open(String name, boolean nil) {
    System.out.println("open " + name);
    return nil ? null : new Res(name);
  }

  static void run(boolean nil, boolean fail) {
    Res c = open("c", nil);
    try (Res a = open("a", nil); c) {
      System.out.println("body");
      if (fail) {
        throw new IllegalStateException("body");
      }
    }
    System.out.println("after");
  }

  public static void main(String[] args) {
    for (boolean nil : new boolean[] {false, true}) {
      for (boolean fail : new boolean[] {false, true}) {
        try {
          run(nil, fail);
        } catch (RuntimeException e) {
          System.out.println("caught " + e.getMessage());
        }
      }
    }
  }
}
