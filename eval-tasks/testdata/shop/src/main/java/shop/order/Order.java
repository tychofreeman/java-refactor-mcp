package shop.order;

import java.util.List;

/** A customer's order: customerKind is "retail", "wholesale" or "staff". */
public record Order(
    String id,
    String customer,
    String customerKind,
    String street,
    String city,
    String postcode,
    Status status,
    List<Line> lines) {}
