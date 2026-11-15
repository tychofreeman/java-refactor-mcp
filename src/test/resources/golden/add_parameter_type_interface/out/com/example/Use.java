package com.example;

class Use {
  int run() {
    Source<String> s = new FileSource();
    return s.then(Source.EMPTY).next().length();
  }
}
