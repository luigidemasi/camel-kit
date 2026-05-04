package com.example.di;

import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;

public class OrderRoute extends RouteBuilder {

    @Inject
    OrderService orderService;

    @Override
    public void configure() throws Exception {
        from("kafka:orders")
            .routeId("processOrders")
            .bean(orderService, "process")
            .to("direct:payment");
    }
}
