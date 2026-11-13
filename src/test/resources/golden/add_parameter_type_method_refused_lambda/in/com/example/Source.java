package com.example;

import java.util.List;

interface Source {
  Integer pick(List<Integer> xs);

  static Source first() {
    return xs -> xs.get(0);
  }
}
