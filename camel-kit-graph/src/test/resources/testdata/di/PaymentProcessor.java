package com.example.di;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("paymentProcessor")
public class PaymentProcessor {

    @Value("${payment.gateway.url}")
    String gatewayUrl;

    @Autowired
    OrderService orderService;

    public void processPayment(Object exchange) {
    }
}
