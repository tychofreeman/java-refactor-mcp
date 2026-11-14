package depot.orders;

/** What a customer's account is billed for an order. */
public record Invoice(String orderId, String accountNumber, Address billTo, int amountCents) {}
