import org.apache.camel.builder.RouteBuilder

class GroovyRoute extends RouteBuilder {

    @Override
    void configure() {
        from("timer:groovyTick?period=5000")
            .routeId("groovyTimer")
            .setBody(constant("Hello from Groovy"))
            .script("groovy", "request.body = request.body.toUpperCase()")
            .to("direct:enrichOrder")
    }
}
