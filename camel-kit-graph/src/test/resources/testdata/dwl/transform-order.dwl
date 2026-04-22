%dw 2.0
%input payload application/json
%output application/json

fun formatAmount(amount) = amount as String {format: "#,##0.00"}

fun isValid(order) = order.amount > 0 and order.id != null

---
{
    orderId: payload.id,
    customerName: payload.customer.name,
    totalAmount: formatAmount(payload.amount),
    items: payload.items map (item) -> {
        name: item.productName,
        qty: item.quantity,
        price: item.unitPrice
    },
    metadata: {
        processedAt: now(),
        source: vars.sourceSystem
    }
}
