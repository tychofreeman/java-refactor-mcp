package com.example.app;

import com.example.Shop;

class Piles {
  Object take() {
    return new Shop.Stack();
  }
}
