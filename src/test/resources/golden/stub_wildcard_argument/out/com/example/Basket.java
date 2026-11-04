package com.example;

import java.util.List;

class Basket {
  void run(List<? extends Number> xs) {
    process(xs);
  }

  void process(List<? extends Number> xs) {
    throw new UnsupportedOperationException("not implemented");
  }
}
