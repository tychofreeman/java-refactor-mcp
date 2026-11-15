package p;

import java.util.ArrayList;
import java.util.List;

public class Main {
  static final String PREFIX = "m:";

  abstract static class Visitor {
    abstract boolean visit(String name);

    void leave(String name) {}
  }

  static void walk(List<String> names, Visitor v) {
    for (String n : names) {
      if (v.visit(n)) {
        v.leave(n);
      }
    }
  }

  static List<String> names(List<String> in) {
    List<String> out = new ArrayList<>();
    walk(
        in,
        new Visitor() {
          int depth;

          @Override
          boolean visit(String name) {
            out.add(PREFIX + name + depth + this.getClass().isAnonymousClass());
            return !name.isEmpty();
          }

          @Override
          void leave(String name) {
            depth++;
          }
        });
    return out;
  }

  public static void main(String[] args) {
    System.out.println(names(List.of("a", "", "b", "c")));
  }
}

class Family {}
