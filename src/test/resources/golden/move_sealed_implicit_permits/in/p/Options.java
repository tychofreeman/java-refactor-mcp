package p;

public class Options {
  sealed interface Option {}

  record Debug(boolean on) implements Option {}

  private record Level(int n) implements Option {}

  static String show(Option o) {
    return o.toString();
  }
}
