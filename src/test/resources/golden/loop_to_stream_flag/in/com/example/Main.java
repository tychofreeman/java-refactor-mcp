package com.example;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    static int run(List<Rename> renames, boolean rename, PrintStream out, PrintStream err) {
        int status = 0;
        // each rename is printed, and made when asked
        for (Rename r : renames) {
            out.println(r.from() + " -> " + r.to());
            if (rename) {
                try {
                    Files.move(r.from(), r.to());
                } catch (IOException e) {
                    err.println("could not rename " + r.from() + ": " + e);
                    status = 1;
                }
            }
        }
        return status;
    }

    record Rename(Path from, Path to) {}
}
