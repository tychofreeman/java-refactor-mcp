package depot.orders;

/** A customer's account, which invoices are billed to. */
public record Account(String number, Address billing) {}
