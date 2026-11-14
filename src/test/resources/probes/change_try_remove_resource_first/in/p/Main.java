package p;

public class Main {
  static class Res implements AutoCloseable {
    final String name;
    final boolean failClose;

    Res(String name, boolean failClose) {
      this.name = name;
      this.failClose = failClose;
    }

    @Override
    public void close() {
      System.out.println("close " + name);
      if (failClose) {
        throw new IllegalStateException("close " + name);
      }
    }
  }

  static Res open(String name, String mode) {
    if (mode.contains("open " + name)) {
      throw new IllegalStateException("open " + name);
    }
    if (mode.contains("null " + name)) {
      return null;
    }
    System.out.println("open " + name);
    return new Res(name, mode.contains("close " + name));
  }

  static void run(String mode) {
    try (Res a = open("a", mode); Res b = open("b", mode)) {
      System.out.println("body " + (a != null) + " " + (b != null));
      if (mode.contains("body")) {
        throw new IllegalStateException("body");
      }
    } catch (RuntimeException e) {
      System.out.println("catch " + e.getMessage() + " suppressed " + e.getSuppressed().length);
    } finally {
      System.out.println("finally");
    }
    System.out.println("after");
  }

  public static void main(String[] args) {
    for (String mode : new String[] {"none", "body", "open a", "open b", "null a", "null b", "close a", "close b"}) {
      System.out.println("-- " + mode);
      run(mode);
    }
  }
}
