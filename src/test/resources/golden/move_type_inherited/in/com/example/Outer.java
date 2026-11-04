package com.example;

import com.example.base.Base;

public class Outer extends Base {
  static class Inner {
    Helper helper = new Helper();

    int value() {
      return util();
    }
  }
}
