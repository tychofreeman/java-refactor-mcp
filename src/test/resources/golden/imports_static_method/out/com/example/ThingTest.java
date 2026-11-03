package com.example;

import static com.example.Check.isFalse;
import static com.example.Check.isTrue;
import static java.lang.Math.max;
import static java.lang.Math.min;

class ThingTest {
  void size() {
    isTrue(max(1, 2) == 2);
  }

  void bounds() {
    isFalse(min(1, 2) > 1);
  }
}
