package com.example;

class Find {
  int first(int[] xs) {
    int found = -1;
    for (int i = 0; i < xs.length; i++) {
      if (xs[i] > 0) {
        found = i;
        break;
      }
    }
    return found;
  }
}
