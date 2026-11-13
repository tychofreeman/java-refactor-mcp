package com.example;

import java.util.List;

class First implements Source {
  @Override
  public <T> T pick(List<T> xs) {
    return xs.get(0);
  }

  int twice(List<Integer> xs) {
    return pick(xs) * 2;
  }
}
