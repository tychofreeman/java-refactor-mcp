package com.example;

import java.util.List;

class First implements Source {
  @Override
  public Integer pick(List<Integer> xs) {
    return xs.get(0);
  }

  int twice(List<Integer> xs) {
    return pick(xs) * 2;
  }
}
