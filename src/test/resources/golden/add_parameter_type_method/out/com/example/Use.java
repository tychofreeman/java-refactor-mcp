package com.example;

import java.util.List;
import java.util.function.Function;

class Use {
  int run(List<String> names) {
    String s = Lists.first(names);
    Function<List<String>, String> f = Lists::first;
    return s.length() + Lists.first(names).length() + f.apply(names).length();
  }
}
