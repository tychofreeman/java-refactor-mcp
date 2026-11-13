package com.example;

interface Sink {
  <T extends CharSequence> void put(T t);
}
