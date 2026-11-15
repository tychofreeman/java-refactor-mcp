package com.example;

import java.util.ArrayList;
import java.util.List;

public class Signatures {
  static final String PREFIX = "m:";

  static List<String> names(Walker walker) {
    List<String> out = new ArrayList<>();
    walker.walk(
        new Visitor() {
          int depth;

          @Override
          public boolean visit(String name) {
            out.add(PREFIX + name + depth);
            return true;
          }

          @Override
          public void leave(String name) {
            depth++;
          }
        });
    return out;
  }
}
