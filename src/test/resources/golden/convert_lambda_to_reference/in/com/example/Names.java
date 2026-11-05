package com.example;

import java.util.function.Function;

public class Names {
  int length(String name) {
    Function<String, Integer> f = s -> s.length();
    return f.apply(name);
  }
}
