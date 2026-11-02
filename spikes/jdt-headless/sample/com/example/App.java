package com.example;

import java.util.List;
import com.missing.Dep;

public class App {
    public static void main(String[] args) {
        Greeter g = new Greeter();
        List<String> names = List.of("a", "b");
        String s = g.greet(names.get(0));
        Dep d = null;
        System.out.println(s + d.toString());
    }
}
