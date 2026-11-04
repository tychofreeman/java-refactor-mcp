package com.example;

import com.example.base.Base;

class Inner {
  Base.Helper helper = new Base.Helper();

  int value() {
    return Base.util();
  }
}
