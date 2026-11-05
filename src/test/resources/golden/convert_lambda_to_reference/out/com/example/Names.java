package com.example;

import java.util.function.Function;

public class Names {
  int length(String name) {
    Function<String, Integer> f = String::length;
    return f.apply(name);
  }
}
