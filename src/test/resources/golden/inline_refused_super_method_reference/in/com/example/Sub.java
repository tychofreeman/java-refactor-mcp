package com.example;

import java.util.function.Supplier;

public class Sub extends Base {
  @Override
  String name() {
    return "sub";
  }

  Supplier<String> up() {
    return super::name;
  }
}
