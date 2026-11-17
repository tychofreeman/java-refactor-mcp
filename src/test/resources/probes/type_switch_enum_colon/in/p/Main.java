package p;
public class Main {
  static String label(Level level) {
    switch (level) {
      case LOW:
        for (int i = 0; i < 3; i++) {
          if (i == level.ordinal() + 1) {
            return "low" + i;
          }
        }
        return "never";
      case MID:
      case HIGH:
        return level.name().toLowerCase();
      case TOP:
        throw new IllegalStateException("top");
    }
    return "after";
  }
  public static void main(String[] args) {
    for (Level l : new Level[] {Level.LOW, Level.MID, Level.HIGH, Level.TOP, null}) {
      try {
        System.out.println(label(l));
      } catch (RuntimeException e) {
        System.out.println(e.getClass().getSimpleName() + " " + (l == null ? "" : e.getMessage()));
      }
    }
  }
}
