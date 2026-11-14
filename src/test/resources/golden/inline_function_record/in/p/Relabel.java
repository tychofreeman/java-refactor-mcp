package p;

public record Relabel(String prefix) {
    String relabel(String label) {
        return prefix + label;
    }

    String shout(String label) {
        return relabel(label).toUpperCase();
    }
}
