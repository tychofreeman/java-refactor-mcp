package com.example;

import java.util.function.ToIntFunction;

class Use {
  int sum(HasX h, Point p) {
    ToIntFunction<HasX> f = HasX::x;
    return h.x() + p.x() + f.applyAsInt(new Box());
  }
}
