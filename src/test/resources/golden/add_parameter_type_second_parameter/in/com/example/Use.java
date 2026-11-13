package com.example;

public class Use {
  Pair<Integer> one = new Pair<>(1);

  @SuppressWarnings({"rawtypes", "unchecked"})
  Object raw() {
    Pair raw = new Pair("r");
    Pair<Integer> full = new Pair<Integer>(2);
    return raw.first() == null ? full : one;
  }
}
