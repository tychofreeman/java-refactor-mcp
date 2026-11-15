package com.example;

class Use {
  int run() {
    Source s = new FileSource();
    return s.then(Source.EMPTY).next().length();
  }
}
