package com.example;

class Use {
  Base make() {
    return new Base(1) {
      @Override
      void run() {}
    };
  }
}
