package com.example;

import java.util.List;

public class Use {
  Pair<Integer, List<String>> one = new Pair<>(1);

  @SuppressWarnings({"rawtypes", "unchecked"})
  Object raw() {
    Pair raw = new Pair("r");
    Pair<Integer, List<String>> full = new Pair<Integer, List<String>>(2);
    return raw.first() == null ? full : one;
  }
}
