package com.example;

import java.util.function.Function;

public class Names {
  int length(String name) {
    Function<String, Integer> f = s -> s.length(), g = s -> s.length() + 1;
    return f.apply(name) + g.apply(name);
  }
}
