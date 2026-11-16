package freight;

/** What is shipped: its reference and its weight in kilograms. */
public record Order(String ref, int weight) {}