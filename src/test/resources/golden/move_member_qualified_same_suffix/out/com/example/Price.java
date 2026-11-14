package com.example;

import com.example.shop.Tax;

public class Price {
  int total() {
    return Tax.doubled(this, 3);
  }
}
