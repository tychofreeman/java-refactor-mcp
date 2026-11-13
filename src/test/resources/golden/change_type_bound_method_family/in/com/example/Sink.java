package com.example;

interface Sink {
  <T> void put(T t);
}
