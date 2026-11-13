package p;
public enum Color {
  RED, GREEN, BLUE {
    @Override public String toString() { return "blue!"; }
  }
}
