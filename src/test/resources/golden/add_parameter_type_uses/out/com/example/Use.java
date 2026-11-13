package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class Use {
  private Box<String> field;
  private Box<String>[] boxes = new Box[2];

  Box<String> make(Box<String> given, Object o) {
    Box<String> b = new Box<>("a");
    String s = b.get();
    List<Box<String>> all = new ArrayList<>();
    all.add(b);
    int n = new Box<String>("abc").get().length();
    var v = new Box<String>("v");
    field = new Box<>(s);
    if (o instanceof Box) {
      Box<String> cast = (Box<String>) o;
      all.add(cast);
    }
    if (o instanceof Box p) {
      all.add(p);
    }
    Class<?> c = Box.class;
    Function<Box<String>, String> get = Box::get;
    Function<String, Box<String>> make = Box::new;
    Supplier<Box<String>> lazy = () -> {
      return new Box<String>("lazy");
    };
    Box<String> anonymous =
        new Box<String>("x") {
          @Override
          public String get() {
            return "anonymous";
          }
        };
    com.example.Box<String> qualified = given;
    return n > 0 ? v : qualified;
  }
}
