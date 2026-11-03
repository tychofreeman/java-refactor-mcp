package com.example;

class Registry {
  int size;

  static final Handler RUN = (name, count) -> Signatures.run(name, count);
}
