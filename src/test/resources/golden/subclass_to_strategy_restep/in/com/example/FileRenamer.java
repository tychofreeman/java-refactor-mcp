package com.example;

import java.io.File;

public class FileRenamer extends Renamer {
    public FileRenamer(StringBuilder log) {
        super(log);
    }

    @Override
    boolean move(String from, String to) {
        if (from.isEmpty()) {
            return false;
        }
        return new File(from).renameTo(new File(to));
    }
}
