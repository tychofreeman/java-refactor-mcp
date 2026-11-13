package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class Use {
  private Box field;
  private Box[] boxes = new Box[2];

  Box make(Box given, Object o) {
    Box b = new Box("a");
    String s = b.get();
    List<Box> all = new ArrayList<>();
    all.add(b);
    int n = new Box("abc").get().length();
    var v = new Box("v");
    field = new Box(s);
    if (o instanceof Box) {
      Box cast = (Box) o;
      all.add(cast);
    }
    if (o instanceof Box p) {
      all.add(p);
    }
    Class<?> c = Box.class;
    Function<Box, String> get = Box::get;
    Function<String, Box> make = Box::new;
    Supplier<Box> lazy = () -> {
      return new Box("lazy");
    };
    Box anonymous =
        new Box("x") {
          @Override
          public String get() {
            return "anonymous";
          }
        };
    com.example.Box qualified = given;
    return n > 0 ? v : qualified;
  }
}
