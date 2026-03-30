package com.example;

public class OrderRoute extends BaseRoute {

    @Override
    public void configure() throws Exception {
        from("kafka:orders")
            .routeId("processOrders")
            .bean(OrderProcessor.class, "validate")
            .marshal().json()
            .to("direct:enrichOrder");

        from("direct:enrichOrder")
            .routeId("enrichOrder")
            .process(exchange -> {
                String body = exchange.getIn().getBody(String.class);
                exchange.getIn().setBody(body.toUpperCase());
            })
            .to("log:enriched")
            .to("seda:storeOrder");
    }
}
