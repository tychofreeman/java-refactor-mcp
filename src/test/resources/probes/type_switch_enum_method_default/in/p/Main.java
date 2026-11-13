package p;
public class Main {
  static String log = "";
  static void announce(Level level) {
    switch (level) {
      case HIGH -> {
        Main.log += "page:" + level.ordinal();
        Main.log += ";";
      }
      case MID -> System.out.println("mid " + level.name());
      default -> Main.log += level.name().toLowerCase() + ";";
    }
  }
  public static void main(String[] args) {
    for (Level l : Level.values()) {
      announce(l);
    }
    System.out.println(log);
  }
}
