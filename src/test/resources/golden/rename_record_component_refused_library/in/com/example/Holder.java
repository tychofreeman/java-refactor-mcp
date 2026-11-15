package com.example;

import java.util.function.Supplier;

record Holder(String get) implements Supplier<String> {}
