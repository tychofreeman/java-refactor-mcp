package p;

public class Options {

  record Debug(boolean on) implements Option {}

  record Level(int n) implements Option {}

  static String show(Option o) {
    return o.toString();
  }
}
