package com.example;

import static com.example.Check.isTrue;
import static java.lang.Math.max;

class ThingTest {
  void size() {
    isTrue(max(1, 2) == 2);
  }
}
