package com.example;

import java.util.List;

class Use {
  int run(Source s, List<Integer> xs) {
    Integer one = s.pick(xs);
    return one + new First().pick(xs);
  }
}
