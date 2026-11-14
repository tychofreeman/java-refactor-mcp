package p;

public record Relabel(String prefix) {
    String shout(String label) {
        return (prefix + label).toUpperCase();
    }
}
