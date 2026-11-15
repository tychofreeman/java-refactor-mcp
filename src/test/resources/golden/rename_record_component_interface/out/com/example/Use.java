package com.example;

import java.util.function.ToIntFunction;

class Use {
  int sum(HasX h, Point p) {
    ToIntFunction<HasX> f = HasX::left;
    return h.left() + p.left() + f.applyAsInt(new Box());
  }
}
