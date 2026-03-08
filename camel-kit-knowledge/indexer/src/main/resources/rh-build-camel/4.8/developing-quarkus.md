## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Developing Applications with Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm140120450553920)
3. [1. Introduction to developing applications with Red Hat build of Apache Camel for Quarkus](#introduction_to_developing_applications_with_red_hat_build_of_apache_camel_for_quarkus)
4. 2. Dependency management
5. 3. Defining Camel routes
6. 4. Configuration
7. 5. Contexts and Dependency Injection (CDI) in Camel Quarkus
8. 6. Observability
9. 7. Native mode
10. 8. Kubernetes
11. 9. Quarkus CXF security guide
12. 10. Camel Security
13. [Legal Notice](#idm140120450553120)

Format Multi-page Single-page View full doc as PDF

# Developing Applications with Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.8

## Developing Applications with Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm140120450553120)

**Abstract**

This guide is for developers writing Camel applications on top of Red Hat build of Apache Camel for Quarkus.

## [Preface Copy link](#idm140120450553920)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Introduction to developing applications with Red Hat build of Apache Camel for Quarkus Copy link](#introduction_to_developing_applications_with_red_hat_build_of_apache_camel_for_quarkus)

This guide is for developers writing Camel applications on top of Red Hat build of Apache Camel for Quarkus.

Camel components which are supported in Red Hat build of Apache Camel for Quarkus have an associated Red Hat build of Apache Camel for Quarkus extension. For more information about the Red Hat build of Apache Camel for Quarkus extensions supported in this distribution, see the [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) reference guide.

## [Chapter 2. Dependency management Copy link](#camel-quarkus-extensions-dependency-management)

A specific Red Hat build of Apache Camel for Quarkus release is supposed to work only with a specific Quarkus release.

### [2.1. Quarkus tooling for starting a new project Copy link](#quarkus_tooling_for_starting_a_new_project)

The easiest and most straightforward way to get the dependency versions right in a new project is to use one of the Quarkus tools:

- [code.quarkus.redhat.com](https://code.quarkus.redhat.com/) - an online project generator,
- [Quarkus Maven plugin](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.15/html-single/getting_started_with_red_hat_build_of_quarkus/index#con-apache-maven-plug-ins-and-quarkus_quarkus-getting-started)

These tools allow you to select extensions and scaffold a new Maven project.

Tip

The universe of available extensions spans over Quarkus Core, Camel Quarkus and several other third party participating projects, such as Hazelcast, Cassandra, Kogito and OptaPlanner.

The generated `pom.xml` will look similar to the following:

```
<project>
  ...
  <properties>
    <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
    <quarkus.platform.group-id>com.redhat.quarkus.platform</quarkus.platform.group-id>
    <quarkus.platform.version>
        <!-- The latest 3.15.x version from https://maven.repository.redhat.com/ga/com/redhat/quarkus/platform/quarkus-bom -->
    </quarkus.platform.version>
    ...
  </properties>
  <dependencyManagement>
    <dependencies>
      <!-- The BOMs managing the dependency versions -->
      <dependency>
        <groupId>${quarkus.platform.group-id}</groupId>
        <artifactId>quarkus-bom</artifactId>
        <version>${quarkus.platform.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>${quarkus.platform.group-id}</groupId>
        <artifactId>quarkus-camel-bom</artifactId>
        <version>${quarkus.platform.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- The extensions you chose in the project generator tool -->
    <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-sql</artifactId>
      <!-- No explicit version required here and below -->
    </dependency>
    ...
  </dependencies>
  ...
</project>
```

Copy to Clipboard

Toggle word wrap

Note

BOM stands for "Bill of Materials" - it is a `pom.xml` whose main purpose is to manage the versions of artifacts so that end users importing the BOM in their projects do not need to care which particular versions of the artifacts are supposed to work together. In other words, having a BOM imported in the `<depependencyManagement>` section of your `pom.xml` allows you to avoid specifying versions for the dependencies managed by the given BOM.

Which particular BOMs end up in the `pom.xml` file depends on extensions you have selected in the generator tool. The generator tools take care to select a minimal consistent set.

If you choose to add an extension at a later point that is not managed by any of the BOMs in your `pom.xml` file, you do not need to search for the appropriate BOM manually.

With the `quarkus-maven-plugin` you can select the extension, and the tool adds the appropriate BOM as required. You can also use the `quarkus-maven-plugin` to upgrade the BOM versions.

The `com.redhat.quarkus.platform` BOMs are aligned with each other which means that if an artifact is managed in more than one BOM, it is always managed with the same version. This has the advantage that application developers do not need to care for the compatibility of the individual artifacts that may come from various independent projects.

### [2.2. Combining with other BOMs Copy link](#combining_with_other_boms)

When combining `camel-quarkus-bom` with any other BOM, think carefully in which order you import them, because the order of imports defines the precedence.

I.e. if `my-foo-bom` is imported before `camel-quarkus-bom` then the versions defined in `my-foo-bom` will take the precedence. This might or might not be what you want, depending on whether there are any overlaps between `my-foo-bom` and `camel-quarkus-bom` and depending on whether those versions with higher precedence work with the rest of the artifacts managed in `camel-quarkus-bom` .

## [Chapter 3. Defining Camel routes Copy link](#camel-quarkus-extensions-routes)

In Red Hat build of Apache Camel for Quarkus, you can define Camel routes using the following languages:

- [Section 3.1, "Java DSL"](#camel-quarkus-extensions-routes-java)
- [Section 3.3, "XML IO DSL"](#camel-quarkus-extensions-routes-xml-io)
- [Section 3.4, "YAML DSL"](#camel-quarkus-extensions-routes-yaml)

### [3.1. Java DSL Copy link](#camel-quarkus-extensions-routes-java)

Extending `org.apache.camel.builder.RouteBuilder` and using the fluent builder methods available there is the most common way of defining Camel Routes. Here is a simple example of a route using the timer component:

```
import org.apache.camel.builder.RouteBuilder;

public class TimerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("timer:foo?period=1000")
                .log("Hello World");
    }
}
```

Copy to Clipboard

Toggle word wrap

### [3.2. Endpoint DSL Copy link](#camel-quarkus-extensions-routes-endpoint)

Since Camel 3.0, you can use fluent builders also for defining Camel endpoints. The following is equivalent with the previous example:

```
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.timer;

public class TimerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from(timer("foo").period(1000))
                .log("Hello World");
    }
}
```

Copy to Clipboard

Toggle word wrap

Note

Builder methods for all Camel components are available via `camel-quarkus-core` , but you still need to add the given component's extension as a dependency for the route to work properly. In case of the above example, it would be `camel-quarkus-timer` .

### [3.3. XML IO DSL Copy link](#camel-quarkus-extensions-routes-xml-io)

In order to configure Camel routes, rests or templates in XML, you must add a Camel XML parser dependency to the classpath. Since Camel Quarkus 1.8.0, `link:https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/ #extensions-xml-io-dsl[camel-quarkus-xml-io-dsl]` is the best choice.

With Camel Main, you can set a property that points to the location of resources XML files such as routes, [REST DSL](https://rhaetor.github.io/rh-camel/manual/rest-dsl.html) and [Route templates](https://rhaetor.github.io/rh-camel/manual/route-template.html) :

```
camel.main.routes-include-pattern = routes/routes.xml, file:src/main/routes/rests.xml, file:src/main/rests/route-template.xml
```

Copy to Clipboard

Toggle word wrap

Note

Path globbing like `camel.main.routes-include-pattern = *./routes.xml` currently does not work in native mode.

**Route**

```
<routes xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns="http://camel.apache.org/schema/spring"
        xsi:schemaLocation="
            http://camel.apache.org/schema/spring
            http://camel.apache.org/schema/spring/camel-spring.xsd">

    <route id="xml-route">
        <from uri="timer:from-xml?period=1000"/>
        <log message="Hello XML!"/>
    </route>

</routes>
```

Copy to Clipboard

Toggle word wrap

Warning

When using XML routes with beans, it is sometime needed to refer to class name, for instance `beanType=org.apache.SomeClass` . In such cases, it might be needed to register the class for reflection in native mode. Refer to the [Native mode](#camel-quarkus-native-mode-reflection) section for more information.

Warning

Spring XML with `<beans>` or Blueprint XML with `<blueprint>` elements are not supported.

The route XML should be in the simplified version like:

**Rest DSL**

```
<rests xmlns="http://camel.apache.org/schema/spring">
    <rest id="greeting" path="/greeting">
        <get path="/hello">
            <to uri="direct:greet"/>
        </get>
    </rest>
</rests>
```

Copy to Clipboard

Toggle word wrap

**Route Templates**

```
<routeTemplates xmlns="http://camel.apache.org/schema/spring">
    <routeTemplate id="myTemplate">
        <templateParameter name="name"/>
        <templateParameter name="greeting"/>
        <templateParameter name="myPeriod" defaultValue="3s"/>
        <route>
            <from uri="timer:{{name}}?period={{myPeriod}}"/>
            <setBody><simple>{{greeting}} ${body}</simple></setBody>
            <log message="${body}"/>
        </route>
    </routeTemplate>
</routeTemplates>
```

Copy to Clipboard

Toggle word wrap

### [3.4. YAML DSL Copy link](#camel-quarkus-extensions-routes-yaml)

To configure routes with YAML, you must add the `camel-quarkus-yaml-dsl` dependency to the classpath.

With Camel Main, you can set a property that points to the location of YAML files containing routes, [REST DSL](https://rhaetor.github.io/rh-camel/manual/rest-dsl.html) and [Route templates](https://rhaetor.github.io/rh-camel/manual/route-template.html) definitions:

```
camel.main.routes-include-pattern = routes/routes.yaml, routes/rests.yaml, rests/route-template.yaml
```

Copy to Clipboard

Toggle word wrap

**Route**

```
- route : id : "my-yaml-route" from : uri : "timer:from-yaml?period=1000" steps : - set-body : constant : "Hello YAML!" - to : "log:from-yaml"
```

Copy to Clipboard

Toggle word wrap

**Rest DSL**

```
- rest : get : - path : "/greeting" to : "direct:greet"
- route : id : "rest-route" from : uri : "direct:greet" steps : - set-body : constant : "Hello YAML!"
```

Copy to Clipboard

Toggle word wrap

**Route Templates**

```
- route-template : id : "myTemplate" parameters : - name : "name" - name : "greeting" defaultValue : "Hello" - name : "myPeriod" defaultValue : "3s" from : uri : "timer:{{name}}?period={{myPeriod}}" steps : - set-body : expression : simple : "{{greeting}} ${body}" - log : "${body}"
- templated-route : route-template-ref : "myTemplate" parameters : - name : "name" value : "tick" - name : "greeting" value : "Bonjour" - name : "myPeriod" value : "5s"
```

Copy to Clipboard

Toggle word wrap

## [Chapter 4. Configuration Copy link](#camel-quarkus-extensions-configuration)

Camel Quarkus automatically configures and deploys a Camel Context bean which by default is started/stopped according to the Quarkus Application lifecycle. The configuration step happens at build time during Quarkus' augmentation phase and it is driven by the Camel Quarkus extensions which can be tuned using Camel Quarkus specific `quarkus.camel.*` properties.

Note

`quarkus.camel.*` configuration properties are documented on the individual extension pages - for example see [Camel Quarkus Core](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-core) .

After the configuration is done, a minimal Camel Runtime is assembled and started in the [RUNTIME\_INIT](https://quarkus.io/guides/writing-extensions#bootstrap-three-phases) phase.

### [4.1. Configuring Camel components Copy link](#configuring_camel_components)

#### [4.1.1. application.properties Copy link](#literal_application_properties_literal)

To configure components and other aspects of Apache Camel through properties, make sure that your application depends on `camel-quarkus-core` directly or transitively. Because most Camel Quarkus extensions depend on `camel-quarkus-core` , you typically do not need to add it explicitly.

`camel-quarkus-core` brings functionalities from Camel Main to Camel Quarkus.

In the example below, you set a specific `ExchangeFormatter` configuration on the `LogComponent` via `application.properties` :

```
camel.component.log.exchange-formatter = #class:org.apache.camel.support.processor.DefaultExchangeFormatter
camel.component.log.exchange-formatter.show-exchange-pattern = false
camel.component.log.exchange-formatter.show-body-type = false
```

Copy to Clipboard

Toggle word wrap

#### [4.1.2. CDI Copy link](#cdi)

You can also configure a component programmatically using CDI.

The recommended method is to observe the `ComponentAddEvent` and configure the component before the routes and the `CamelContext` are started:

```
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.apache.camel.quarkus.core.events.ComponentAddEvent;
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.support.processor.DefaultExchangeFormatter;

@ApplicationScoped
public static class EventHandler {
    public void onComponentAdd(@Observes ComponentAddEvent event) {
        if (event.getComponent() instanceof LogComponent) {
            /* Perform some custom configuration of the component */
            LogComponent logComponent = ((LogComponent) event.getComponent());
            DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();
            formatter.setShowExchangePattern(false);
            formatter.setShowBodyType(false);
            logComponent.setExchangeFormatter(formatter);
        }
    }
}
```

Copy to Clipboard

Toggle word wrap

##### [4.1.2.1. Producing a @Named component instance Copy link](#producing_a_literal_named_literal_component_instance)

Alternatively, you can create and configure the component yourself in a `@Named` producer method. This works as Camel uses the component URI scheme to look-up components from its registry. For example, in the case of a `LogComponent` Camel looks for a `log` named bean.

Warning

While producing a `@Named` component bean will usually work, it may cause subtle issues with some components.

Camel Quarkus extensions may do one or more of the following:

- Pass custom subtype of the default Camel component type. See the [Vert.x WebSocket extension](https://github.com/apache/camel-quarkus/blob/main/extensions/vertx-websocket/runtime/src/main/java/org/apache/camel/quarkus/component/vertx/websocket/VertxWebsocketRecorder.java#L42) example.
- Perform some Quarkus specific customization of the component. See the [JPA extension](https://github.com/apache/camel-quarkus/blob/main/extensions/jpa/runtime/src/main/java/org/apache/camel/quarkus/component/jpa/CamelJpaRecorder.java#L35) example.

These actions are not performed when you produce your own component instance, therefore, configuring components in an observer method is the recommended method.

```
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.apache.camel.component.log.LogComponent;
import org.apache.camel.support.processor.DefaultExchangeFormatter;

@ApplicationScoped
public class Configurations {
    /**
     * Produces a {@link LogComponent} instance with a custom exchange formatter set-up.
     */
    @Named("log")
```

1

```
LogComponent log() {
        DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();
        formatter.setShowExchangePattern(false);
        formatter.setShowBodyType(false);

        LogComponent component = new LogComponent();
        component.setExchangeFormatter(formatter);

        return component;
    }
}
```

Copy to Clipboard

Toggle word wrap

[1](#CO1-1) The `"log"` argument of the `@Named` annotation can be omitted if the name of the method is the same.

### [4.2. Configuration by convention Copy link](#configuration_by_convention)

In addition to support configuring Camel through properties, `camel-quarkus-core` allows you to use conventions to configure the Camel behavior. For example, if there is a single `ExchangeFormatter` instance in the CDI container, then it will automatically wire that bean to the `LogComponent` .

**Additional resources**

- [Configuring and using Metering in OpenShift Container Platform](https://docs.redhat.com/en/documentation/openshift_container_platform/3.15.x/html/metering/index)

## [Chapter 5. Contexts and Dependency Injection (CDI) in Camel Quarkus Copy link](#camel-quarkus-extensions-cdi)

CDI plays a central role in Quarkus and Camel Quarkus offers a first class support for it too.

You may use `@Inject` , `@ConfigProperty` and similar annotations e.g. to inject beans and configuration values to your Camel `RouteBuilder` , for example:

```
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
```

1

```
public class TimerRoute extends RouteBuilder {

    @ConfigProperty(name = "timer.period", defaultValue = "1000")
```

2

```
String period;

    @Inject
    Counter counter;

    @Override
    public void configure() throws Exception {
        fromF("timer:foo?period=%s", period)
                .setBody(exchange -> "Incremented the counter: " + counter.increment())
                .to("log:cdi-example?showExchangePattern=false&showBodyType=false");
    }
}
```

Copy to Clipboard

Toggle word wrap

[1](#CO2-1) The `@ApplicationScoped` annotation is required for `@Inject` and `@ConfigProperty` to work in a `RouteBuilder` . Note that the `@ApplicationScoped` beans are managed by the CDI container and their life cycle is thus a bit more complex than the one of the plain `RouteBuilder` . In other words, using `@ApplicationScoped` in `RouteBuilder` comes with some boot time penalty and you should therefore only annotate your `RouteBuilder` with `@ApplicationScoped` when you really need it. [2](#CO2-2) The value for the `timer.period` property is defined in `src/main/resources/application.properties` of the example project.

Tip

Refer to the [Quarkus Dependency Injection guide](https://quarkus.io/guides/cdi) for more details.

### [5.1. Accessing CamelContext Copy link](#accessing_literal_camelcontext_literal)

To access `CamelContext` just inject it into your bean:

```
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.stream.Collectors;
import java.util.List;
import org.apache.camel.CamelContext;

@ApplicationScoped
public class MyBean {

    @Inject
    CamelContext context;

    public List<String> listRouteIds() {
        return context.getRoutes().stream().map(Route::getId).sorted().collect(Collectors.toList());
    }
}
```

Copy to Clipboard

Toggle word wrap

### [5.2. @EndpointInject and @Produce Copy link](#literal_endpointinject_literal_and_literal_produce_literal)

If you are used to `@org.apache.camel.EndpointInject` and `@org.apache.camel.Produce` from [plain Camel](https://rhaetor.github.io/rh-camel/manual/pojo-producing.html) or from Camel on SpringBoot, you can continue using them on Quarkus too.

The following use cases are supported by `org.apache.camel.quarkus:camel-quarkus-core` :

```
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.EndpointInject;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;

@ApplicationScoped
class MyBean {

    @EndpointInject("direct:myDirect1")
    ProducerTemplate producerTemplate;

    @EndpointInject("direct:myDirect2")
    FluentProducerTemplate fluentProducerTemplate;

    @EndpointInject("direct:myDirect3")
    DirectEndpoint directEndpoint;

    @Produce("direct:myDirect4")
    ProducerTemplate produceProducer;

    @Produce("direct:myDirect5")
    FluentProducerTemplate produceProducerFluent;

}
```

Copy to Clipboard

Toggle word wrap

You can use any other Camel producer endpoint URI instead of `direct:myDirect*` .

Warning

`@EndpointInject` and `@Produce` are not supported on setter methods - see [#2579](https://github.com/apache/camel-quarkus/issues/2579)

The following use case is supported by `org.apache.camel.quarkus:camel-quarkus-bean` :

```
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Produce;

@ApplicationScoped
class MyProduceBean {

    public interface ProduceInterface {
        String sayHello(String name);
    }

    @Produce("direct:myDirect6")
    ProduceInterface produceInterface;

    void doSomething() {
        produceInterface.sayHello("Kermit")
    }

}
```

Copy to Clipboard

Toggle word wrap

### [5.3. CDI and the Camel Bean component Copy link](#cdi_and_the_camel_bean_component)

#### [5.3.1. Refer to a bean by name Copy link](#refer_to_a_bean_by_name)

To refer to a bean in a route definition by name, just annotate the bean with `@Named("myNamedBean")` and `@ApplicationScoped` (or some other [supported](https://quarkus.io/guides/cdi-reference#supported_features) scope). The `@RegisterForReflection` annotation is important for the native mode.

```
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import io.quarkus.runtime.annotations.RegisterForReflection;

@ApplicationScoped
@Named("myNamedBean")
@RegisterForReflection
public class NamedBean {
    public String hello(String name) {
        return "Hello " + name + " from the NamedBean";
    }
}
```

Copy to Clipboard

Toggle word wrap

Then you can use the `myNamedBean` name in a route definition:

```
import org.apache.camel.builder.RouteBuilder;
public class CamelRoute extends RouteBuilder {
    @Override
    public void configure() {
        from("direct:named")
                .bean("myNamedBean", "hello");
        /* ... which is an equivalent of the following: */
        from("direct:named")
                .to("bean:myNamedBean?method=hello");
    }
}
```

Copy to Clipboard

Toggle word wrap

As an alternative to `@Named` , you may also use `io.smallrye.common.annotation.Identifier` to name and identify a bean.

```
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.Identifier;

@ApplicationScoped
@Identifier("myBeanIdentifier")
@RegisterForReflection
public class MyBean {
    public String hello(String name) {
        return "Hello " + name + " from MyBean";
    }
}
```

Copy to Clipboard

Toggle word wrap

Then refer to the identifier value within the Camel route:

```
import org.apache.camel.builder.RouteBuilder;
public class CamelRoute extends RouteBuilder {
    @Override
    public void configure() {
        from("direct:start")
                .bean("myBeanIdentifier", "Camel");
    }
}
```

Copy to Clipboard

Toggle word wrap

Note

We aim at supporting all use cases listed in [Bean binding](https://rhaetor.github.io/rh-camel/manual/bean-binding.html) section of Camel documentation. Do not hesitate to [file an issue](https://github.com/apache/camel-quarkus/issues) if some bean binding scenario does not work for you.

#### [5.3.2. @Consume Copy link](#literal_consume_literal)

Since Camel Quarkus 2.0.0, the `camel-quarkus-bean` artifact brings support for `@org.apache.camel.Consume` - see the [Pojo consuming](https://rhaetor.github.io/rh-camel/manual/pojo-consuming.html) section of Camel documentation.

Declaring a class like the following

```
import org.apache.camel.Consume;
public class Foo {

  @Consume("activemq:cheese")
  public void onCheese(String name) {
    ...
  }
}
```

Copy to Clipboard

Toggle word wrap

will automatically create the following Camel route

```
from("activemq:cheese").bean("foo1234", "onCheese")
```

Copy to Clipboard

Toggle word wrap

for you. Note that Camel Quarkus will implicitly add `@jakarta.inject.Singleton` and `jakarta.inject.Named("foo1234")` to the bean class, where `1234` is a hash code obtained from the fully qualified class name. If your bean has some CDI scope (such as `@ApplicationScoped` ) or `@Named("someName")` set already, those will be honored in the auto-created route.

## [Chapter 6. Observability Copy link](#camel-quarkus-extensions-observability)

### [6.1. Health &amp; liveness checks Copy link](#camel-quarkus-extensions-health)

Health &amp; liveness checks are supported via the MicroProfile Health extension. They can be configured via the [Camel Health](https://rhaetor.github.io/rh-camel/manual/health-check.html) API or via [Quarkus MicroProfile Health](https://quarkus.io/guides/microprofile-health) .

All configured checks are available on the standard MicroProfile Health endpoint URLs:

- [http://localhost:8080/q/health](http://localhost:8080/q/health)
- [http://localhost:8080/q/health/live](http://localhost:8080/q/health/live)
- [http://localhost:8080/q/health/ready](http://localhost:8080/q/health/ready)

#### [6.1.1. Health endpoint Copy link](#health_endpoint)

Camel provides some out of the box liveness and readiness checks. To see this working, interrogate the `/q/health/live` and `/q/health/ready` endpoints on port `9000` :

```
$ curl -s localhost:9000/q/health/live
```

Copy to Clipboard

Toggle word wrap

```
$ curl -s localhost:9000/q/health/ready
```

Copy to Clipboard

Toggle word wrap

The JSON output will contain a checks for verifying whether the `CamelContext` and each individual route is in the 'Started' state.

This example project contains a custom liveness check class `CustomLivenessCheck` and custom readiness check class `CustomReadinessCheck` which leverage the Camel health API. You'll see these listed in the health JSON as 'custom-liveness-check' and 'custom-readiness-check'. On every 5th invocation of these checks, the health status of `custom-liveness-check` will be reported as DOWN.

You can also directly leverage MicroProfile Health APIs to create checks. Class `CamelUptimeHealthCheck` demonstrates how to register a readiness check.

### [6.2. Metrics Copy link](#camel-quarkus-extensions-metrics)

We provide [MicroProfile Metrics](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-microprofile-metrics) for exposing metrics.

Some basic Camel metrics are provided for you out of the box, and these can be supplemented by configuring additional metrics in your routes.

Metrics are available on the standard Quarkus metrics endpoint:

- [http://localhost:8080/q/metrics](http://localhost:8080/q/metrics)

### [6.3. Monitoring a Camel application Copy link](#monitoring-ceq-application)

With monitoring of your applications, you can collect information about how your application behaves, such as metrics, health checks and distributed tracing.

Note

This section uses the `Observability` example listed in the [Red Hat build of Quarkus examples](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.15.0-product/observability) , adding observability with `micrometer` .

Tip

Check the [Camel Quarkus User guide](https://camel.apache.org/camel-quarkus/latest/first-steps.html) for prerequisites and other general information.

#### [6.3.1. Creating a project Copy link](#creating_a_project)

1. Start in the **Development** mode
2. Run the maven `compile` command: `$ mvn clean compile quarkus:dev` Copy to Clipboard Toggle word wrap This compiles the project, starts the application and lets the Quarkus tooling watch for changes in your workspace. Any modifications in your project automatically take effect in the running application. Tip Refer to the Development mode section of [Camel Quarkus User guide](https://camel.apache.org/camel-quarkus/latest/first-steps.html#_development_mode) for more details.

#### [6.3.2. Enabling metrics Copy link](#enabling_metrics)

To enable observability features in Camel Quarkus, you must add additional dependencies to the project's pom.xml file. The most important ones are `camel-quarkus-opentelemetry` and `quarkus-micrometer-registry-prometheus` .

1. Add the dependencies to your project `pom.xml` : `<dependencies> ... <dependency> <groupId>org.apache.camel.quarkus</groupId> <artifactId>camel-quarkus-opentelemetry</artifactId> </dependency> <dependency> <groupId>io.quarkiverse.micrometer.registry</groupId> <artifactId>quarkus-micrometer-registry-prometheus</artifactId> </dependency> ... </dependencies>` Copy to Clipboard Toggle word wrap With these dependencies you benefit from both [Camel Micrometer](https://camel.apache.org/components/next/micrometer-component.html) and [Quarkus Micrometer](https://quarkus.io/guides/micrometer) .

#### [6.3.3. Creating meters Copy link](#creating_meters)

You can create meters for custom metrics in multiple ways:

- [Section 6.3.3.1, "Using Camel micrometer component"](#camel-micrometer-component)
- [Section 6.3.3.2, "Using CDI dependency injection"](#cdi-dependency-injection)
- [Section 6.3.3.3, "Using Micrometer annotations"](#micrometer-annotations)

##### [6.3.3.1. Using Camel micrometer component Copy link](#camel-micrometer-component)

With this method you use [Routes.java](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.15.0-product//observability/src/main/java/org/acme/observability/Routes.java) .

```
.to("micrometer:counter:org.acme.observability.greeting-provider?tags=type=events,purpose=example")
```

Copy to Clipboard

Toggle word wrap

Which will count each call to the `platform-http:/greeting-provider` endpoint.

##### [6.3.3.2. Using CDI dependency injection Copy link](#cdi-dependency-injection)

With this method you use CDI dependency injection of the `MeterRegistry` :

```
@Inject
MeterRegistry registry;
```

Copy to Clipboard

Toggle word wrap

Then using it directly in a Camel `Processor` method to publish metrics:

```
void countGreeting(Exchange exchange) {
    registry.counter("org.acme.observability.greeting", "type", "events", "purpose", "example").increment();
}
```

Copy to Clipboard

Toggle word wrap

```
from("platform-http:/greeting")
    .removeHeaders("*")
    .process(this::countGreeting)
```

Copy to Clipboard

Toggle word wrap

This counts each call to the `platform-http:/greeting` endpoint.

##### [6.3.3.3. Using Micrometer annotations Copy link](#micrometer-annotations)

With this method you use [Micrometer annotations](https://quarkus.io/guides/micrometer#does-micrometer-support-annotations) , by defining a bean [`TimerCounter.java`](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.15.0-product//observability/src/main/java/org/acme/observability/micrometer/TimerCounter.java) as follows:

```
@ApplicationScoped
@Named("timerCounter")
public class TimerCounter {

    @Counted(value = "org.acme.observability.timer-counter", extraTags = { "purpose", "example" })
    public void count() {
    }
}
```

Copy to Clipboard

Toggle word wrap

It can then be invoked from Camel via the bean EIP (see [`TimerRoute.java`](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.15.0-product//observability/src/main/java/org/acme/observability/TimerRoute.java) ):

```
.bean("timerCounter", "count")
```

Copy to Clipboard

Toggle word wrap

It will increment the counter metric each time the Camel timer is fired.

##### [6.3.3.4. Browsing metrics Copy link](#browsing_metrics)

Metrics are exposed on an HTTP endpoint at `/q/metrics` on port `9000` .

Note

Note we are using a different port (9000) for the management endpoint then our application (8080) is listening on. This is configured in `application.properties` via [`quarkus.management.enabled = true`](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.15.0-product//observability/src/main/resources/application.properties#L22) . See the [Quarkus management interface guide](https://quarkus.io/guides/management-interface-reference) for more information.

To view all Camel metrics do:

```
$ curl -s localhost:9000/q/metrics
```

Copy to Clipboard

Toggle word wrap

To view only our previously created metrics, use:

```
$ curl -s localhost:9000/q/metrics | grep -i 'purpose="example"'
```

Copy to Clipboard

Toggle word wrap

and you should see 3 lines of different metrics (with the same value, as they are all triggered by the timer).

Note

Maybe you've noticed the Prometheus output format. If you would rather use the JSON format, please follow the Quarkus Micrometer management interface [configuration guide](https://quarkus.io/guides/micrometer#management-interface) .

#### [6.3.4. Tracing Copy link](#tracing)

To be able to diagnose problems in Camel Quarkus applications, you can start tracing messages. We will use OpenTelemetry standard suited for cloud environments.

All you need is to add the dependencies `camel-quarkus-opentelemetry` and `quarkus-micrometer-registry-prometheus` to your project `pom.xml` :

```
<dependencies>

    ...

    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-opentelemetry</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkiverse.micrometer.registry</groupId>
        <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
    </dependency>

    ...

</dependencies>
```

Copy to Clipboard

Toggle word wrap

Then configure the OpenTelemetry exporter in `application.properties` :

```
# We are using a property placeholder to be able to test this example in convenient way in a cloud environment
quarkus.otel.exporter.otlp.traces.endpoint = http://${TELEMETRY_COLLECTOR_COLLECTOR_SERVICE_HOST:localhost}:4317
```

Copy to Clipboard

Toggle word wrap

Note

For information about other OpenTelemetry exporters, refer to the Camel Quarkus OpenTelemetry [extension documentation](https://camel.apache.org/camel-quarkus/next/reference/extensions/opentelemetry.html#extensions-opentelemetry-usage-exporters) .

To view tracing events, start a tracing server. A simple way of doing this is with Docker Compose:

```
$ docker-compose up -d
```

Copy to Clipboard

Toggle word wrap

With the server running, browse to [http://localhost:16686](http://localhost:16686/) . Then choose 'camel-quarkus-observability' from the 'Service' drop down and click the 'Find Traces' button.

The `platform-http` consumer route introduces a random delay to simulate latency, hence the overall time of each trace should be different. When viewing a trace, you should see a hierarchy of 6 spans showing the progression of the message exchange through each endpoint.

#### [6.3.5. Packaging and running the application Copy link](#packaging_and_running_the_application)

Once you are done with developing you can package and run the application.

Tip

For more details about the JVM mode and Native mode, see the "Package and run" section of the [Camel Quarkus User guide](https://camel.apache.org/camel-quarkus/latest/first-steps.html#_package_and_run_the_application)

##### [6.3.5.1. JVM mode Copy link](#jvm_mode)

```
$ mvn clean package
$ java -jar target/quarkus-app/quarkus-run.jar
...
[io.quarkus] (main) camel-quarkus-examples-... started in 1.163s. Listening on: http://0.0.0.0:8080
```

Copy to Clipboard

Toggle word wrap

##### [6.3.5.2. Native mode Copy link](#native_mode)

Important

Native mode requires having GraalVM and other tools installed. Please check the Prerequisites section of [Camel Quarkus User guide](https://camel.apache.org/camel-quarkus/latest/first-steps.html#_prerequisites) .

To prepare a native executable using GraalVM, run the following command:

```
$ mvn clean package -Pnative
$ ./target/*-runner
...
[io.quarkus] (main) camel-quarkus-examples-... started in 0.013s. Listening on: http://0.0.0.0:8080
...
```

Copy to Clipboard

Toggle word wrap

## [Chapter 7. Native mode Copy link](#camel-quarkus-native-mode)

For additional information about compiling and testing application in native mode, see [Producing a native executable](https://access.redhat.com/documentation/en-us/red_hat_build_of_quarkus/quarkus-2-7/guide/c9fdb950-554d-427d-aa49-cc3da15ae860#proc_producing-native-executable_quarkus-building-native-executable) in the *Compiling your Quarkus applications to native executables* guide.

### [7.1. Character encodings Copy link](#charsets)

By default, not all `Charsets` are available in native mode.

```
Charset.defaultCharset(), US-ASCII, ISO-8859-1, UTF-8, UTF-16BE, UTF-16LE, UTF-16
```

Copy to Clipboard

Toggle word wrap

If you expect your application to need any encoding not included in this set or if you see an `UnsupportedCharsetException` thrown in the native mode, please add the following entry to your `application.properties` :

```
quarkus.native.add-all-charsets = true
```

Copy to Clipboard

Toggle word wrap

See also [quarkus.native.add-all-charsets](https://quarkus.io/guides/all-config#quarkus-core_quarkus.native.add-all-charsets) in Quarkus documentation.

### [7.2. Locale Copy link](#locale)

By default, only the building JVM default locale is included in the native image. Quarkus provides a way to set the locale via `application.properties` , so that you do not need to rely on `LANG` and `LC_*` environement variables:

```
quarkus.native.user-country=US
quarkus.native.user-language=en
```

Copy to Clipboard

Toggle word wrap

There is also support for embedding multiple locales into the native image and for selecting the default locale via Mandrel command line options `-H:IncludeLocales=fr,en` , `H:+IncludeAllLocales` and `-H:DefaultLocale=de` . You can set those via the Quarkus `quarkus.native.additional-build-args` property.

### [7.3. Embedding resources in the native executable Copy link](#embedding-resource-in-native-executable)

Resources accessed via `Class.getResource()` , `Class.getResourceAsStream()` , `ClassLoader.getResource()` , `ClassLoader.getResourceAsStream()` , etc. at runtime need to be explicitly listed for including in the native executable.

This can be done using Quarkus `quarkus.native.resources.includes` and `quarkus.native.resources.excludes` properties in `application.properties` file as demonstrated below:

```
quarkus.native.resources.includes = docs/*,images/*
quarkus.native.resources.excludes = docs/ignored.adoc,images/ignored.png
```

Copy to Clipboard

Toggle word wrap

In the example above, resources named `docs/included.adoc` and `images/included.png` would be embedded in the native executable while `docs/ignored.adoc` and `images/ignored.png` would not.

`resources.includes` and `resources.excludes` are both lists of comma separated Ant-path style glob patterns.

Refer to [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) Reference for more details.

### [7.4. Using the onException clause in native mode Copy link](#using-onexception-clause-in-native-mode)

When using [Camel](https://rhaetor.github.io/rh-camel/manual/exception-clause.html) [`onException`](https://rhaetor.github.io/rh-camel/manual/exception-clause.html) [handling](https://rhaetor.github.io/rh-camel/manual/exception-clause.html) in native mode, it is your responsibility to register the exception classes for reflection.

For instance, having a camel context with `onException` handling:

```
onException(MyException.class).handled(true);
from("direct:route-that-could-produce-my-exception").throw(MyException.class);
```

Copy to Clipboard

Toggle word wrap

The class `mypackage.MyException` should be registered for reflection. For more information, see [Registering classes for reflection](#camel-quarkus-native-mode-reflection) .

### [7.5. Registering classes for reflection Copy link](#camel-quarkus-native-mode-reflection)

By default, dynamic reflection is not available in native mode. Classes for which reflective access is needed, have to be registered for reflection at compile time.

In many cases, application developers do not need to care because Quarkus extensions are able to detect the classes that require the reflection and register them automatically.

However, in some situations, Quarkus extensions may miss some classes and it is up to the application developer to register them. There are two ways to do that:

1. The [`@io.quarkus.runtime.annotations.RegisterForReflection`](https://quarkus.io/guides/writing-native-applications-tips#alternative-with-registerforreflection) annotation can be used to register classes on which it is used, or it can also register third party classes via its `targets` attribute. `import io.quarkus.runtime.annotations.RegisterForReflection; @RegisterForReflection class MyClassAccessedReflectively { } @RegisterForReflection( targets = { org.third-party.Class1.class, org.third-party.Class2.class } ) class ReflectionRegistrations { }` Copy to Clipboard Toggle word wrap
2. The `quarkus.camel.native.reflection` options in `application.properties` : `quarkus.camel.native.reflection.include-patterns = org.apache.commons.lang3.tuple.* quarkus.camel.native.reflection.exclude-patterns = org.apache.commons.lang3.tuple.*Triple` Copy to Clipboard Toggle word wrap For these options to work properly, the artifacts containing the selected classes must either contain a Jandex index ('META-INF/jandex.idx') or they must be registered for indexing using the 'quarkus.index-dependency.*' options in 'application.properties' - for example: `quarkus.index-dependency.commons-lang3.group-id = org.apache.commons quarkus.index-dependency.commons-lang3.artifact-id = commons-lang3` Copy to Clipboard Toggle word wrap

### [7.6. Registering classes for serialization Copy link](#serialization)

If serialization support is requested via `quarkus.camel.native.reflection.serialization-enabled` , the classes listed in [CamelSerializationProcessor.BASE\_SERIALIZATION\_CLASSES](https://github.com/apache/camel-quarkus/blob/main/extensions-core/core/deployment/src/main/java/org/apache/camel/quarkus/core/deployment/CamelSerializationProcessor.java) are automatically registered for serialization.

You can register more classes using `@RegisterForReflection(serialization = true)` .

## [Chapter 8. Kubernetes Copy link](#camel-quarkus-extensions-reference-kubernetes)

This guide describes different ways to configure and deploy a Camel Quarkus application on kubernetes. It also describes some specific use cases for Knative and Service Binding.

### [8.1. Kubernetes Copy link](#kubernetes)

Quarkus supports generating resources for vanilla Kubernetes, OpenShift and Knative. Furthermore, Quarkus can deploy the application to a target Kubernetes cluster by applying the generated manifests to the target cluster's API Server. More information in the [`Quarkus Kubernetes guide`](https://quarkus.io/guides/deploying-to-kubernetes) .

### [8.2. Knative Copy link](#knative)

The Camel Quarkus extensions whose consumers support Knative deployment are:

- [camel-quarkus-cxf-soap](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-cxf-soap)
- [camel-quarkus-grpc](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-grpc)
- [camel-quarkus-http](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#http)
- [camel-quarkus-netty-http](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#netty-http)
- [camel-quarkus-platform-http](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-platform-http)
- [camel-quarkus-rest](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-rest)
- [camel-quarkus-servlet](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-servlet)
- [camel-quarkus-vertx-websocket](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-vertx-websocket)

### [8.3. Service binding Copy link](#service_binding)

Quarkus also supports the [Service Binding Specification for Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes#service_binding) to bind services to applications.

The following Camel Quarkus extensions can be used with Service Binding:

- [camel-quarkus-kafka](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-kafka)

## [Chapter 9. Quarkus CXF security guide Copy link](#quarkus-cxf-reference-intro-quarkus-cxf-security-guide)

This chapter provides information about security when working with Quarkus CXF extensions.

### [9.1. Security guide Copy link](#security-guide-index-quarkus-cxf-security-guide)

The security guide documents various security related aspects of Quarkus CXF:

- [SSL, TLS and HTTPS](#ssl-tls-https)
- [Authentication and authorization](#authentication-authorization)
- [Authentication enforced by WS-SecurityPolicy](#ws-securitypolicy-authentication-authorization)

#### [9.1.1. SSL, TLS and HTTPS Copy link](#ssl-tls-https)

This section documents various use cases related to SSL, TLS and HTTPS.

Note

The sample code snippets used in this section come from the [WS-SecurityPolicy integration test](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/ws-security-policy) in the source tree of Quarkus CXF

##### [9.1.1.1. Client SSL configuration Copy link](#client_ssl_configuration)

If your client is going to communicate with a server whose SSL certificate is not trusted by the client's operating system, then you need to set up a custom trust store for your client.

Tools like `openssl` or Java `keytool` are commonly used for creating and maintaining truststores.

We have examples for both tools in the Quarkus CXF source tree:

- [Create truststore with Java 'keytool' (wrapped by a Maven plugin)](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/ws-security-policy/pom.xml#L185-L520)
- [Create truststore with](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/ws-security-policy/generate-certs.sh) [`openssl`](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/ws-security-policy/generate-certs.sh)

Once you have prepared the trust store, you need to configure your client to use it.

##### [9.1.1.1.1. Set the client trust store in application.properties Copy link](#set_the_client_trust_store_in_literal_application_properties_literal)

This is the easiest way to set the client trust store. The key role is played by the following properties:

- [quarkus.cxf.client."client-name".trust-store](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-trust-store)
- [quarkus.cxf.client."client-name".trust-store-type](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-trust-store-type)
- [quarkus.cxf.client."client-name".trust-store-password](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-trust-store-password)

Here is an example:

**application.properties**

```
# Client side SSL
quarkus.cxf.client.hello.client-endpoint-url = https://localhost:${quarkus.http.test-ssl-port}/services/hello
quarkus.cxf.client.hello.service-interface = io.quarkiverse.cxf.it.security.policy.HelloService
```

1

```
quarkus.cxf.client.hello.trust-store-type = pkcs12
```

2

```
quarkus.cxf.client.hello.trust-store = client-truststore.pkcs12
quarkus.cxf.client.hello.trust-store-password = client-truststore-password
```

Copy to Clipboard

Toggle word wrap

[1](#CO3-1)

`pkcs12` and `jks` are two commonly used keystore formats. PKCS12 is the [default Java keystore format](https://openjdk.org/jeps/229) since Java 9. We recommend using PKCS12 rather than JKS, because it offers stronger cryptographic algorithms, it is extensible, standardized, language-neutral and widely supported. [2](#CO3-2) The referenced `client-truststore.pkcs12` file has to be available either in the classpath or in the file system.

##### [9.1.1.2. Server SSL configuration Copy link](#server_ssl_configuration)

To make your services available over the HTTPS protocol, you need to set up server keystore in the first place. The server SSL configuration is driven by Vert.x, the HTTP layer of Quarkus. [Quarkus HTTP guide](https://quarkus.io/version/3.15/guides/http-reference#ssl) provides the information about the configuration options.

Here is a basic example:

**application.properties**

```
# Server side SSL
quarkus.tls.key-store.p12.path = localhost-keystore.pkcs12
quarkus.tls.key-store.p12.password = localhost-keystore-password
quarkus.tls.key-store.p12.alias = localhost
quarkus.tls.key-store.p12.alias-password = localhost-keystore-password
```

Copy to Clipboard

Toggle word wrap

##### [9.1.1.3. Mutual TLS (mTLS) authentication Copy link](#mtls-quarkus-cxf-security-guide)

So far, we have explained the simple or single-sided case where only the server proves its identity through an SSL certificate and the client has to be set up to trust that certificate. Mutual TLS authentication goes by letting also the client prove its identity using the same means of public key cryptography.

Hence, for the Mutual TLS (mTLS) authentication, in addition to setting up the server keystore and client truststore as described above, you need to set up the keystore on the client side and the truststore on the server side.

The tools for creating and maintaining the stores are the same and the configuration properties to use are pretty much analogous to the ones used in the Simple TLS case.

The [mTLS integration test](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/mtls) in the Quarkus CXF source tree can serve as a good starting point.

The keystores and truststores are created with [`openssl`](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/mtls/generate-certs.sh) (or alternatively with Java [Java](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/mtls/pom.xml#L140-L408) [`keytool`](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/mtls/pom.xml#L140-L408) )

Here is the `application.properties` file:

**application.properties**

```
# Server keystore for Simple TLS
quarkus.tls.localhost-pkcs12.key-store.p12.path = localhost-keystore.pkcs12
quarkus.tls.localhost-pkcs12.key-store.p12.password = localhost-keystore-password
quarkus.tls.localhost-pkcs12.key-store.p12.alias = localhost
quarkus.tls.localhost-pkcs12.key-store.p12.alias-password = localhost-keystore-password
# Server truststore for Mutual TLS
quarkus.tls.localhost-pkcs12.trust-store.p12.path = localhost-truststore.pkcs12
quarkus.tls.localhost-pkcs12.trust-store.p12.password = localhost-truststore-password
# Select localhost-pkcs12 as the TLS configuration for the HTTP server
quarkus.http.tls-configuration-name = localhost-pkcs12

# Do not allow any clients which do not prove their indentity through an SSL certificate
quarkus.http.ssl.client-auth = required

# CXF service
quarkus.cxf.endpoint."/mTls".implementor = io.quarkiverse.cxf.it.auth.mtls.MTlsHelloServiceImpl

# CXF client with a properly set certificate for mTLS
quarkus.cxf.client.mTls.client-endpoint-url = https://localhost:${quarkus.http.test-ssl-port}/services/mTls
quarkus.cxf.client.mTls.service-interface = io.quarkiverse.cxf.it.security.policy.HelloService
quarkus.cxf.client.mTls.key-store = target/classes/client-keystore.pkcs12
quarkus.cxf.client.mTls.key-store-type = pkcs12
quarkus.cxf.client.mTls.key-store-password = client-keystore-password
quarkus.cxf.client.mTls.key-password = client-keystore-password
quarkus.cxf.client.mTls.trust-store = target/classes/client-truststore.pkcs12
quarkus.cxf.client.mTls.trust-store-type = pkcs12
quarkus.cxf.client.mTls.trust-store-password = client-truststore-password

# Include the keystores in the native executable
quarkus.native.resources.includes = *.pkcs12,*.jks
```

Copy to Clipboard

Toggle word wrap

##### [9.1.1.4. Enforce SSL through WS-SecurityPolicy Copy link](#enforce_ssl_through_ws_securitypolicy)

The requirement for the clients to connect through HTTPS can be defined in a policy.

The functionality is provided by [`quarkus-cxf-rt-ws-security`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#-rt-ws-security) extension.

Here is an example of a policy file:

**https-policy.xml**

```
<?xml version="1.0" encoding="UTF-8"?>
<wsp:Policy wsp:Id="HttpsSecurityServicePolicy"
            xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy"
    xmlns:sp="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702"
    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <wsp:ExactlyOne>
        <wsp:All>
            <sp:TransportBinding>
                <wsp:Policy>
                    <sp:TransportToken>
                        <wsp:Policy>
                            <sp:HttpsToken RequireClientCertificate="false" />
                        </wsp:Policy>
                    </sp:TransportToken>
                    <sp:IncludeTimestamp />
                    <sp:AlgorithmSuite>
                        <wsp:Policy>
                            <sp:Basic128 />
                        </wsp:Policy>
                    </sp:AlgorithmSuite>
                </wsp:Policy>
            </sp:TransportBinding>
        </wsp:All>
    </wsp:ExactlyOne>
</wsp:Policy>
```

Copy to Clipboard

Toggle word wrap

The policy has to be referenced from a service endpoint interface (SEI):

**HttpsPolicyHelloService.java**

```
package io.quarkiverse.cxf.it.security.policy;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.apache.cxf.annotations.Policy;

/**
 * A service implementation with a transport policy set
 */
@WebService(serviceName = "HttpsPolicyHelloService")
@Policy(placement = Policy.Placement.BINDING, uri = "https-policy.xml")
public interface HttpsPolicyHelloService extends AbstractHelloService {

    @WebMethod
    @Override
    public String hello(String text);

}
```

Copy to Clipboard

Toggle word wrap

With this setup in place, any request delivered over HTTP will be rejected by the `PolicyVerificationInInterceptor` :

```
ERROR [org.apa.cxf.ws.pol.PolicyVerificationInInterceptor] Inbound policy verification failed: These policy alternatives can not be satisfied:
 {http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702}TransportBinding: TLS is not enabled
 ...
```

Copy to Clipboard

Toggle word wrap

#### [9.1.2. Authentication and authorization Copy link](#authentication-authorization)

Note

The sample code snippets shown in this section come from the [Client and server integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/client-server) in the source tree of Quarkus CXF. You may want to use it as a runnable example.

##### [9.1.2.1. Client HTTP basic authentication Copy link](#client-http-basic-authentication)

Use the following client configuration options provided by [`quarkus-cxf`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#) extension to pass the username and password for HTTP basic authentication:

- [quarkus.cxf.client."client-name".username](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-username)
- [quarkus.cxf.client."client-name".password](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-password)

Here is an example:

**application.properties**

```
quarkus.cxf.client.basicAuth.wsdl = http://localhost:${quarkus.http.test-port}/soap/basicAuth?wsdl
quarkus.cxf.client.basicAuth.client-endpoint-url = http://localhost:${quarkus.http.test-port}/soap/basicAuth
quarkus.cxf.client.basicAuth.username = bob
quarkus.cxf.client.basicAuth.password = bob234
```

Copy to Clipboard

Toggle word wrap

##### [9.1.2.1.1. Accessing WSDL protected by basic authentication Copy link](#accessing_wsdl_protected_by_basic_authentication)

By default, the clients created by Quarkus CXF do not send the `Authorization` header, unless you set the [`quarkus.cxf.client."client-name".secure-wsdl-access`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-secure-wsdl-access) to `true` :

**application.properties**

```
quarkus.cxf.client.basicAuthSecureWsdl.wsdl = http://localhost:${quarkus.http.test-port}/soap/basicAuth?wsdl
quarkus.cxf.client.basicAuthSecureWsdl.client-endpoint-url = http://localhost:${quarkus.http.test-port}/soap/basicAuthSecureWsdl
quarkus.cxf.client.basicAuthSecureWsdl.username = bob
quarkus.cxf.client.basicAuthSecureWsdl.password = ${client-server.bob.password}
quarkus.cxf.client.basicAuthSecureWsdl.secure-wsdl-access = true
```

Copy to Clipboard

Toggle word wrap

##### [9.1.2.2. Mutual TLS (mTLS) authentication Copy link](#mutual_tls_mtls_authentication)

See the [Mutual TLS (mTLS) authentication](#mtls-quarkus-cxf-security-guide) section in SSL, TLS and HTTPS guide.

##### [9.1.2.3. Securing service endpoints Copy link](#securing-service-endpoints-quarkus-cxf-security-guide)

The server-side authentication and authorization is driven by [Quarkus Security](https://quarkus.io/version/3.15/guides/security-overview) , especially when it comes to

- [Authentication mechanisms](https://quarkus.io/version/3.15/guides/security-authentication-mechanisms)
- [Identity providers](https://quarkus.io/version/3.15/guides/security-identity-providers)
- [Role-based access control (RBAC)](https://quarkus.io/version/3.15/guides/security-authorize-web-endpoints-reference)

There is a basic example in our [Client and server integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/client-server) . Its key parts are:

- `io.quarkus:quarkus-elytron-security-properties-file` dependency as an Identity provider
- Basic authentication enabled and users with their roles configured in `application.properties` : **application.properties** `quarkus.http.auth.basic = true quarkus.security.users.embedded.enabled = true quarkus.security.users.embedded.plain-text = true quarkus.security.users.embedded.users.alice = alice123 quarkus.security.users.embedded.roles.alice = admin quarkus.security.users.embedded.users.bob = bob234 quarkus.security.users.embedded.roles.bob = app-user` Copy to Clipboard Toggle word wrap
- Role-based access control enfoced via `@RolesAllowed` annotation:

**BasicAuthHelloServiceImpl.java**

```
package io.quarkiverse.cxf.it.auth.basic;

import jakarta.annotation.security.RolesAllowed;
import jakarta.jws.WebService;

import io.quarkiverse.cxf.it.HelloService;

@WebService(serviceName = "HelloService", targetNamespace = HelloService.NS)
@RolesAllowed("app-user")
public class BasicAuthHelloServiceImpl implements HelloService {
    @Override
    public String hello(String person) {
        return "Hello " + person + "!";
    }
}
```

Copy to Clipboard

Toggle word wrap

#### [9.1.3. Authentication enforced by WS-SecurityPolicy Copy link](#ws-securitypolicy-authentication-authorization)

You can enforce authentication through WS-SecurityPolicy, instead of [Mutual TLS](#mtls-quarkus-cxf-security-guide) and Basic HTTP authentication for [clients](#client-http-basic-authentication) and [services](#securing-service-endpoints-quarkus-cxf-security-guide) .

To enforce authentication through WS-SecurityPolicy, follow these steps:

1. Add a supporting tokens policy to an endpoint in the WSDL contract.
2. On the server side, implement an authentication callback handler and associate it with the endpoint in `application.properties` or via environment variables. Credentials received from clients are authenticated by the callback handler.
3. On the client side, provide credentials through either configuration in `application.properties` or environment variables. Alternatively, you can implement an authentication callback handler to pass the credentials.

##### [9.1.3.1. Specifying an Authentication Policy Copy link](#Auth-Policy)

If you want to enforce authentication on a service endpoint, associate a *supporting tokens* policy assertion with the relevant endpoint binding and specify one or more *token assertions* under it.

There are several different kinds of supporting tokens policy assertions, whose XML element names all end with `SupportingTokens` (for example, `SupportingTokens` , `SignedSupportingTokens` , and so on). For a complete list, see the [Supporting Tokens](https://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/ws-securitypolicy-1.2-spec-os.html#_Toc161826561) section of the WS-SecurityPolicy specification.

##### [9.1.3.2. UsernameToken policy assertion example Copy link](#literal_usernametoken_literal_policy_assertion_example)

Tip

The sample code snippets used in this section come from the [WS-SecurityPolicy integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy) in the source tree of Quarkus CXF. You may want to use it as a runnable example.

The following listing shows an example of a policy that requires a WS-Security `UsernameToken` (which contains username/password credentials) to be included in the security header.

**username-token-policy.xml**

```
<?xml version="1.0" encoding="UTF-8"?>
<wsp:Policy
        wsp:Id="UsernameTokenSecurityServicePolicy"
        xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy"
    xmlns:sp="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702"
    xmlns:sp13="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200802"
    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <wsp:ExactlyOne>
        <wsp:All>
            <sp:SupportingTokens>
                <wsp:Policy>
                    <sp:UsernameToken
                        sp:IncludeToken="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/IncludeToken/AlwaysToRecipient">
                        <wsp:Policy>
                            <sp:WssUsernameToken11 />
                            <sp13:Created />
                            <sp13:Nonce />
                        </wsp:Policy>
                    </sp:UsernameToken>
                </wsp:Policy>
            </sp:SupportingTokens>
        </wsp:All>
    </wsp:ExactlyOne>
</wsp:Policy>
```

Copy to Clipboard

Toggle word wrap

There are two ways how you can associate this policy file with a service endpoint:

- Reference the policy on the Service Endpoint Interface (SEI) like this: **UsernameTokenPolicyHelloService.java** `@WebService(serviceName = "UsernameTokenPolicyHelloService") @Policy(placement = Policy.Placement.BINDING, uri = "username-token-policy.xml") public interface UsernameTokenPolicyHelloService extends AbstractHelloService { ... }` Copy to Clipboard Toggle word wrap
- Include the policy [in your WSDL contract](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L163) and reference it via [`PolicyReference`](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L95) [element](https://github.com/quarkiverse/quarkus-cxf/blob/3.15/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L95) .

When you have the policy in place, configure the credentials on the service endpoint and the client:

**application.properties**

```
# A service with a UsernameToken policy assertion
quarkus.cxf.endpoint."/helloUsernameToken".implementor = io.quarkiverse.cxf.it.security.policy.UsernameTokenPolicyHelloServiceImpl
quarkus.cxf.endpoint."/helloUsernameToken".security.callback-handler = #usernameTokenPasswordCallback

# These properties are used in UsernameTokenPasswordCallback
# and in the configuration of the helloUsernameToken below
wss.user = cxf-user
wss.password = secret

# A client with a UsernameToken policy assertion
quarkus.cxf.client.helloUsernameToken.client-endpoint-url = https://localhost:${quarkus.http.test-ssl-port}/services/helloUsernameToken
quarkus.cxf.client.helloUsernameToken.service-interface = io.quarkiverse.cxf.it.security.policy.UsernameTokenPolicyHelloService
quarkus.cxf.client.helloUsernameToken.security.username = ${wss.user}
quarkus.cxf.client.helloUsernameToken.security.password = ${wss.password}
```

Copy to Clipboard

Toggle word wrap

In the above listing, `usernameTokenPasswordCallback` is a name of a `@jakarta.inject.Named` bean implementing `javax.security.auth.callback.CallbackHandler` . Quarkus CXF will lookup a bean with this [name](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#beanRefs) in the CDI container.

Here is an example implementation of the bean:

**UsernameTokenPasswordCallback.java**

```
package io.quarkiverse.cxf.it.security.policy;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Named("usernameTokenPasswordCallback") /* We refer to this bean by this name from application.properties */
public class UsernameTokenPasswordCallback implements CallbackHandler {

    /* These two configuration properties are set in application.properties */
    @ConfigProperty(name = "wss.password")
    String password;
    @ConfigProperty(name = "wss.user")
    String user;

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        if (callbacks.length < 1) {
            throw new IllegalStateException("Expected a " + WSPasswordCallback.class.getName()
                    + " at possition 0 of callbacks. Got array of length " + callbacks.length);
        }
        if (!(callbacks[0] instanceof WSPasswordCallback)) {
            throw new IllegalStateException(
                    "Expected a " + WSPasswordCallback.class.getName() + " at possition 0 of callbacks. Got an instance of "
                            + callbacks[0].getClass().getName() + " at possition 0");
        }
        final WSPasswordCallback pc = (WSPasswordCallback) callbacks[0];
        if (user.equals(pc.getIdentifier())) {
            pc.setPassword(password);
        } else {
            throw new IllegalStateException("Unexpected user " + user);
        }
    }

}
```

Copy to Clipboard

Toggle word wrap

To test the whole setup, you can create a simple [`@QuarkusTest`](https://quarkus.io/version/3.15/guides/getting-started-testing) :

**UsernameTokenTest.java**

```
package io.quarkiverse.cxf.it.security.policy;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class UsernameTokenTest {

    @CXFClient("helloUsernameToken")
    UsernameTokenPolicyHelloService helloUsernameToken;

    @Test
    void helloUsernameToken() {
        Assertions.assertThat(helloUsernameToken.hello("CXF")).isEqualTo("Hello CXF from UsernameToken!");
    }
}
```

Copy to Clipboard

Toggle word wrap

When running the test via `mvn test -Dtest=UsernameTokenTest` , you should see a SOAP message being logged with a `Security` header containing `Username` and `Password` :

**Log output of the UsernameTokenTest**

```
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" soap:mustUnderstand="1">
      <wsse:UsernameToken xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" wsu:Id="UsernameToken-bac4f255-147e-42a4-aeec-e0a3f5cd3587">
        <wsse:Username>cxf-user</wsse:Username>
        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">secret</wsse:Password>
        <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">3uX15dZT08jRWFWxyWmfhg==</wsse:Nonce>
        <wsu:Created>2024-10-02T17:32:10.497Z</wsu:Created>
      </wsse:UsernameToken>
    </wsse:Security>
  </soap:Header>
  <soap:Body>
    <ns2:hello xmlns:ns2="http://policy.security.it.cxf.quarkiverse.io/">
      <arg0>CXF</arg0>
    </ns2:hello>
  </soap:Body>
</soap:Envelope>
```

Copy to Clipboard

Toggle word wrap

##### [9.1.3.3. SAML v1 and v2 policy assertion examples Copy link](#saml_v1_and_v2_policy_assertion_examples)

The [WS-SecurityPolicy integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy) contains also analogous examples with SAML v1 and SAML v2 assertions.

## [Chapter 10. Camel Security Copy link](#camel-security)

This chapter provides information about Camel route security options.

### [10.1. Camel security overview Copy link](#camel-security-overview)

Camel offers several forms &amp; levels of security capabilities that can be utilized on Camel routes. These various forms of security may be used in conjunction with each other or separately.

The broad categories offered are:

- *Route Security* - Authentication and Authorization services to proceed on a route or route segment
- *Payload Security* - Data Formats that offer encryption/decryption services at the payload level
- *Endpoint Security* - Security offered by components that can be utilized by endpointUri associated with the component
- *Configuration Security* - Security offered by encrypting sensitive information from configuration files or external Secured Vault systems.

Camel offers the [JSSE Utility](https://rhaetor.github.io/rh-camel/manual/camel-configuration-utilities.html) for configuring SSL/TLS related aspects of a number of Camel components.

### [10.2. Route Security Copy link](#route_security)

Authentication and Authorization Services

Camel offers [Route Policy](https://rhaetor.github.io/rh-camel/manual/route-policy.html) driven security capabilities that may be wired into routes or route segments. A route policy in Camel utilizes a strategy pattern for applying interceptors on Camel Processors. It's offering the ability to apply cross-cutting concerns (for example. security, transactions etc) of a Camel route.

### [10.3. Payload Security Copy link](#payload_security)

Camel offers encryption/decryption services to secure payloads or selectively apply encryption/decryption capabilities on portions/sections of a payload.

The dataformats offering encryption/decryption of payloads utilizing [Marshal](https://rhaetor.github.io/rh-camel/components/4.8.x//eips/marshal-eip.html) are:

- [Crypto](https://rhaetor.github.io/rh-camel/components/4.8.x//dataformats/crypto-dataformat.html)
- [PGP](https://rhaetor.github.io/rh-camel/components/4.8.x//dataformats/pgp-dataformat.html)

### [10.4. Endpoint Security Copy link](#endpoint_security)

Some components in Camel offer an ability to secure their endpoints (using interceptors etc) and therefore ensure that they offer the ability to secure payloads as well as provide authentication/authorization capabilities at endpoints created using the components.

### [10.5. Configuration Security Copy link](#configuration_security)

Camel offers the [Properties](https://rhaetor.github.io/rh-camel/components/4.8.x//properties-component.html) component to externalize configuration values to properties files. Those values could contain sensitive information such as usernames and passwords.

Those values can be encrypted and automatic decrypted by Camel using:

- [Jasypt](https://rhaetor.github.io/rh-camel/components/4.8.x/others/jasypt.html)

Camel also support accessing the secured configuration from an external vault systems.

#### [10.5.1. Configuration Security using Vaults Copy link](#configuration_security_using_vaults)

The following *Vaults* are supported by Camel:

- [AWS Secrets Manager](https://rhaetor.github.io/rh-camel/components/4.8.x//aws-secrets-manager-component.html)
- [Google Secret Manager](https://rhaetor.github.io/rh-camel/components/4.8.x//google-secret-manager-component.html)
- [Azure Key Vault](https://rhaetor.github.io/rh-camel/components/4.8.x//azure-key-vault-component.html)
- [Hashicorp Vault](https://rhaetor.github.io/rh-camel/components/4.8.x//hashicorp-vault-component.html)

##### [10.5.1.1. Using AWS Vault Copy link](#using_aws_vault)

To use AWS Secrets Manager you need to provide *accessKey* , *secretKey* and the *region* . This can be done using environmental variables before starting the application:

```
export $CAMEL_VAULT_AWS_ACCESS_KEY = accessKey export $CAMEL_VAULT_AWS_SECRET_KEY = secretKey export $CAMEL_VAULT_AWS_REGION = region
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.aws.accessKey = accessKey
camel.vault.aws.secretKey = secretKey
camel.vault.aws.region = region
```

Copy to Clipboard

Toggle word wrap

If you want instead to use the [AWS default credentials provider](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html) , you'll need to provide the following env variables:

```
export $CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER = true export $CAMEL_VAULT_AWS_REGION = region
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.aws.defaultCredentialsProvider = true
camel.vault.aws.region = region
```

Copy to Clipboard

Toggle word wrap

It is also possible to specify a particular profile name for accessing AWS Secrets Manager

```
export $CAMEL_VAULT_AWS_USE_PROFILE_CREDENTIALS_PROVIDER = true export $CAMEL_VAULT_AWS_PROFILE_NAME = test-account export $CAMEL_VAULT_AWS_REGION = region
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.aws.profileCredentialsProvider = true
camel.vault.aws.profileName = test-account
camel.vault.aws.region = region
```

Copy to Clipboard

Toggle word wrap

At this point you'll be able to reference a property in the following way by using `aws:` as prefix in the `{{ }}` syntax:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{aws:route}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Where `route` will be the name of the secret stored in the AWS Secrets Manager Service.

You could specify a default value in case the secret is not present on AWS Secret Manager:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{aws:route:default}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist, the property will fallback to "default" as value.

Also, you are able to get particular field of the secret, if you have for example a secret named database of this form:

```
{
  "username": "admin",
  "password": "password123",
  "engine": "postgres",
  "host": "127.0.0.1",
  "port": "3128",
  "dbname": "db"
}
```

Copy to Clipboard

Toggle word wrap

You're able to do get single secret value in your route, like for example:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{aws:database/username}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Or re-use the property as part of an endpoint.

You could specify a default value in case the particular field of secret is not present on AWS Secret Manager:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{aws:database/username:admin}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist or the secret exists, but the username field is not part of the secret, the property will fallback to "admin" as value.

Note

For the moment we are not considering the rotation function, if any will be applied, but it is in the work to be done.

The only requirement is adding `camel-aws-secrets-manager` JAR to your Camel application.

##### [10.5.1.2. Using Google Secret Manager GCP Vault Copy link](#using_google_secret_manager_gcp_vault)

To use GCP Secret Manager you need to provide *serviceAccountKey* file and GCP *projectId* . This can be done using environmental variables before starting the application:

```
export $CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY = file:////path/to/service.accountkey export $CAMEL_VAULT_GCP_PROJECT_ID = projectId
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.gcp.serviceAccountKey = accessKey
camel.vault.gcp.projectId = secretKey
```

Copy to Clipboard

Toggle word wrap

If you want instead to use the [GCP default client instance](https://cloud.google.com/docs/authentication/production) , you'll need to provide the following env variables:

```
export $CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE = true export $CAMEL_VAULT_GCP_PROJECT_ID = projectId
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.gcp.useDefaultInstance = true
camel.vault.aws.projectId = region
```

Copy to Clipboard

Toggle word wrap

At this point you'll be able to reference a property in the following way by using `gcp:` as prefix in the `{{ }}` syntax:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{gcp:route}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Where `route` will be the name of the secret stored in the GCP Secret Manager Service.

You could specify a default value in case the secret is not present on GCP Secret Manager:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{gcp:route:default}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist, the property will fallback to "default" as value.

Also, you are able to get particular field of the secret, if you have for example a secret named database of this form:

```
{
  "username": "admin",
  "password": "password123",
  "engine": "postgres",
  "host": "127.0.0.1",
  "port": "3128",
  "dbname": "db"
}
```

Copy to Clipboard

Toggle word wrap

You're able to do get single secret value in your route, like for example:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{gcp:database/username}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Or re-use the property as part of an endpoint.

You could specify a default value in case the particular field of secret is not present on GCP Secret Manager:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{gcp:database/username:admin}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist or the secret exists, but the username field is not part of the secret, the property will fallback to "admin" as value.

Note

For the moment we are not considering the rotation function, if any will be applied, but it is in the work to be done.

There are only two requirements: - Adding `camel-google-secret-manager` JAR to your Camel application. - Give the service account used permissions to do operation at secret management level (for example accessing the secret payload, or being admin of secret manager service)

##### [10.5.1.3. Using Azure Key Vault Copy link](#using_azure_key_vault)

To use this function you'll need to provide credentials to Azure Key Vault Service as environment variables:

```
export $CAMEL_VAULT_AZURE_TENANT_ID = tenantId export $CAMEL_VAULT_AZURE_CLIENT_ID = clientId export $CAMEL_VAULT_AZURE_CLIENT_SECRET = clientSecret export $CAMEL_VAULT_AZURE_VAULT_NAME = vaultName
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.azure.tenantId = accessKey
camel.vault.azure.clientId = clientId
camel.vault.azure.clientSecret = clientSecret
camel.vault.azure.vaultName = vaultName
```

Copy to Clipboard

Toggle word wrap

Or you can enable the usage of Azure Identity in the following way:

```
export $CAMEL_VAULT_AZURE_IDENTITY_ENABLED = true export $CAMEL_VAULT_AZURE_VAULT_NAME = vaultName
```

Copy to Clipboard

Toggle word wrap

You can also enable the usage of Azure Identity in the `application.properties` file such as:

```
camel.vault.azure.azureIdentityEnabled = true
camel.vault.azure.vaultName = vaultName
```

Copy to Clipboard

Toggle word wrap

At this point you'll be able to reference a property in the following way:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{azure:route}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Where route will be the name of the secret stored in the Azure Key Vault Service.

You could specify a default value in case the secret is not present on Azure Key Vault Service:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{azure:route:default}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist, the property will fallback to "default" as value.

Also you are able to get particular field of the secret, if you have for example a secret named database of this form:

```
{ "username" : "admin" , "password" : "password123" , "engine" : "postgres" , "host" : "127.0.0.1" , "port" : "3128" , "dbname" : "db"
}
```

Copy to Clipboard

Toggle word wrap

You're able to do get single secret value in your route, like for example:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{azure:database/username}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Or re-use the property as part of an endpoint.

You could specify a default value in case the particular field of secret is not present on Azure Key Vault:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{azure:database/username:admin}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist or the secret exists, but the username field is not part of the secret, the property will fallback to "admin" as value.

For the moment we are not considering the rotation function, if any will be applied, but it is in the work to be done.

The only requirement is adding the camel-azure-key-vault jar to your Camel application.

##### [10.5.1.4. Using Hashicorp Vault Copy link](#using_hashicorp_vault)

To use this function, you'll need to provide credentials for Hashicorp vault as environment variables:

```
export $CAMEL_VAULT_HASHICORP_TOKEN = token export $CAMEL_VAULT_HASHICORP_HOST = host export $CAMEL_VAULT_HASHICORP_PORT = port export $CAMEL_VAULT_HASHICORP_SCHEME = http/https
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.hashicorp.token = token
camel.vault.hashicorp.host = host
camel.vault.hashicorp.port = port
camel.vault.hashicorp.scheme = scheme
```

Copy to Clipboard

Toggle word wrap

At this point, you'll be able to reference a property in the following way:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{hashicorp:secret:route}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Where route will be the name of the secret stored in the Hashicorp Vault instance, in the 'secret' engine.

You could specify a default value in case the secret is not present on Hashicorp Vault instance:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{hashicorp:secret:route:default}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case, if the secret doesn't exist in the 'secret' engine, the property will fall back to "default" as value.

Also, you are able to get a particular field of the secret, if you have, for example, a secret named database of this form:

```
{ "username" : "admin" , "password" : "password123" , "engine" : "postgres" , "host" : "127.0.0.1" , "port" : "3128" , "dbname" : "db"
}
```

Copy to Clipboard

Toggle word wrap

You're able to do get single secret value in your route, in the 'secret' engine, like for example:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{hashicorp:secret:database/username}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Or re-use the property as part of an endpoint.

You could specify a default value in case the particular field of secret is not present on Hashicorp Vault instance, in the 'secret' engine:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{hashicorp:secret:database/username:admin}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case, if the secret doesn't exist or the secret exists (in the 'secret' engine) but the username field is not part of the secret, the property will fall back to "admin" as value.

There is also the syntax to get a particular version of the secret for both the approach, with field/default value specified or only with secret:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{hashicorp:secret:route@2}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This approach will return the RAW route secret with version '2', in the 'secret' engine.

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{hashicorp:route:default@2}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This approach will return the route secret value with version '2' or default value in case the secret doesn't exist or the version doesn't exist (in the 'secret' engine).

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{hashicorp:secret:database/username:admin@2}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This approach will return the username field of the database secret with version '2' or admin in case the secret doesn't exist or the version doesn't exist (in the 'secret' engine).

##### [10.5.1.5. Automatic Camel context reloading on Secret Refresh while using AWS Secrets Manager Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_aws_secrets_manager)

Being able to reload Camel context on a Secret Refresh, could be done by specifying the usual credentials (the same used for AWS Secret Manager Property Function).

With Environment variables:

```
export $CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER = accessKey export $CAMEL_VAULT_AWS_REGION = region
```

Copy to Clipboard

Toggle word wrap

or as plain Camel main properties:

```
camel.vault.aws.useDefaultCredentialProvider = true
camel.vault.aws.region = region
```

Copy to Clipboard

Toggle word wrap

Or by specifying accessKey/SecretKey and region, instead of using the default credentials provider chain.

To enable the automatic refresh you'll need additional properties to set:

```
camel.vault.aws.refreshEnabled=true
camel.vault.aws.refreshPeriod=60000
camel.vault.aws.secrets=Secret
camel.main.context-reload-enabled = true
```

Copy to Clipboard

Toggle word wrap

where `camel.vault.aws.refreshEnabled` will enable the automatic context reload, `camel.vault.aws.refreshPeriod` is the interval of time between two different checks for update events and `camel.vault.aws.secrets` is a regex representing the secrets we want to track for updates.

Note that `camel.vault.aws.secrets` is not mandatory: if not specified the task responsible for checking updates events will take into accounts or the properties with an `aws:` prefix.

The only requirement is adding the camel-aws-secrets-manager jar to your Camel application.

##### [10.5.1.6. Automatic Camel context reloading on Secret Refresh while using AWS Secrets Manager with Eventbridge and AWS SQS Services Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_aws_secrets_manager_with_eventbridge_and_aws_sqs_services)

Another option is to use AWS EventBridge in conjunction with the AWS SQS service.

On the AWS side, the following resources need to be created:

- an AWS Couldtrail trail
- an AWS SQS Queue
- an Eventbridge rule of the following kind

```
{
  "source": ["aws.secretsmanager"],
  "detail-type": ["AWS API Call via CloudTrail"],
  "detail": {
    "eventSource": ["secretsmanager.amazonaws.com"]
  }
}
```

Copy to Clipboard

Toggle word wrap

This rule will make the event related to AWS Secrets Manager filtered

- You need to set the a Rule target to the AWS SQS Queue for Eventbridge rule
- You need to give permission to the Eventbrige rule, to write on the above SQS Queue. For doing this you'll need to define a json file like this:

```
{
    "Policy": "{\"Version\":\"2012-10-17\",\"Id\":\"<queue_arn>/SQSDefaultPolicy\",\"Statement\":[{\"Sid\": \"EventsToMyQueue\", \"Effect\": \"Allow\", \"Principal\": {\"Service\": \"events.amazonaws.com\"}, \"Action\": \"sqs:SendMessage\", \"Resource\": \"<queue_arn>\", \"Condition\": {\"ArnEquals\": {\"aws:SourceArn\": \"<eventbridge_rule_arn>\"}}}]}"
}
```

Copy to Clipboard

Toggle word wrap

Change the values for queue\_arn and eventbridge\_rule\_arn, save the file with policy.json name and run the following command with AWS CLI

```
aws sqs set-queue-attributes --queue-url < queue_url > --attributes file://policy.json
```

Copy to Clipboard

Toggle word wrap

where queue\_url is the AWS SQS Queue URL of the just created Queue.

Now you should be able to set up the configuration on the Camel side. To enable the SQS notification add the following properties:

```
camel.vault.aws.refreshEnabled=true
camel.vault.aws.refreshPeriod=60000
camel.vault.aws.secrets=Secret
camel.main.context-reload-enabled = true
camel.vault.aws.useSqsNotification=true
camel.vault.aws.sqsQueueUrl=<queue_url>
```

Copy to Clipboard

Toggle word wrap

where queue\_url is the AWS SQS Queue URL of the just created Queue.

Whenever an event of PutSecretValue for the Secret named 'Secret' will happen, a message will be enqueued in the AWS SQS Queue and consumed on the Camel side and a context reload will be triggered.

##### [10.5.1.7. Automatic Camel context reloading on Secret Refresh while using Google Secret Manager Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_google_secret_manager)

Being able to reload Camel context on a Secret Refresh, could be done by specifying the usual credentials (the same used for Google Secret Manager Property Function).

With Environment variables:

```
export $CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE = true export $CAMEL_VAULT_GCP_PROJECT_ID = projectId
```

Copy to Clipboard

Toggle word wrap

or as plain Camel main properties:

```
camel.vault.gcp.useDefaultInstance = true
camel.vault.aws.projectId = projectId
```

Copy to Clipboard

Toggle word wrap

Or by specifying a path to a service account key file, instead of using the default instance.

To enable the automatic refresh you'll need additional properties to set:

```
camel.vault.gcp.projectId= projectId
camel.vault.gcp.refreshEnabled=true
camel.vault.gcp.refreshPeriod=60000
camel.vault.gcp.secrets=hello*
camel.vault.gcp.subscriptionName=subscriptionName
camel.main.context-reload-enabled = true
```

Copy to Clipboard

Toggle word wrap

where `camel.vault.gcp.refreshEnabled` will enable the automatic context reload, `camel.vault.gcp.refreshPeriod` is the interval of time between two different checks for update events and `camel.vault.gcp.secrets` is a regex representing the secrets we want to track for updates.

Note that `camel.vault.gcp.secrets` is not mandatory: if not specified the task responsible for checking updates events will take into accounts or the properties with an `gcp:` prefix.

The `camel.vault.gcp.subscriptionName` is the subscription name created in relation to the Google PubSub topic associated with the tracked secrets.

This mechanism while make use of the notification system related to Google Secret Manager: through this feature, every secret could be associated to one up to ten Google Pubsub Topics. These topics will receive events related to life cycle of the secret.

There are only two requirements: - Adding `camel-google-secret-manager` JAR to your Camel application. - Give the service account used permissions to do operation at secret management level (for example accessing the secret payload, or being admin of secret manager service and also have permission over the Pubsub service)

##### [10.5.1.8. Automatic Camel context reloading on Secret Refresh while using Azure Key Vault Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_azure_key_vault)

Being able to reload Camel context on a Secret Refresh, could be done by specifying the usual credentials (the same used for Azure Key Vault Property Function).

With Environment variables:

```
export $CAMEL_VAULT_AZURE_TENANT_ID = tenantId export $CAMEL_VAULT_AZURE_CLIENT_ID = clientId export $CAMEL_VAULT_AZURE_CLIENT_SECRET = clientSecret export $CAMEL_VAULT_AZURE_VAULT_NAME = vaultName
```

Copy to Clipboard

Toggle word wrap

or as plain Camel main properties:

```
camel.vault.azure.tenantId = accessKey
camel.vault.azure.clientId = clientId
camel.vault.azure.clientSecret = clientSecret
camel.vault.azure.vaultName = vaultName
```

Copy to Clipboard

Toggle word wrap

If you want to use Azure Identity with environment variables, you can do in the following way:

```
export $CAMEL_VAULT_AZURE_IDENTITY_ENABLED = true export $CAMEL_VAULT_AZURE_VAULT_NAME = vaultName
```

Copy to Clipboard

Toggle word wrap

You can also enable the usage of Azure Identity in the `application.properties` file such as:

```
camel.vault.azure.azureIdentityEnabled = true
camel.vault.azure.vaultName = vaultName
```

Copy to Clipboard

Toggle word wrap

To enable the automatic refresh you'll need additional properties to set:

```
camel.vault.azure.refreshEnabled=true
camel.vault.azure.refreshPeriod=60000
camel.vault.azure.secrets=Secret
camel.vault.azure.eventhubConnectionString=eventhub_conn_string
camel.vault.azure.blobAccountName=blob_account_name
camel.vault.azure.blobContainerName=blob_container_name
camel.vault.azure.blobAccessKey=blob_access_key
camel.main.context-reload-enabled = true
```

Copy to Clipboard

Toggle word wrap

where `camel.vault.azure.refreshEnabled` will enable the automatic context reload, `camel.vault.azure.refreshPeriod` is the interval of time between two different checks for update events and `camel.vault.azure.secrets` is a regex representing the secrets we want to track for updates.

where `camel.vault.azure.eventhubConnectionString` is the eventhub connection string to get notification from, `camel.vault.azure.blobAccountName` , `camel.vault.azure.blobContainerName` and `camel.vault.azure.blobAccessKey` are the Azure Storage Blob parameters for the checkpoint store needed by Azure Eventhub.

Note that `camel.vault.azure.secrets` is not mandatory: if not specified the task responsible for checking updates events will take into accounts or the properties with an `azure:` prefix.

The only requirement is adding the camel-azure-key-vault jar to your Camel application.

## [Legal Notice Copy link](#idm140120450553120)

Copyright © 2025 Red Hat, Inc. The text of and illustrations in this document are licensed by Red Hat under a Creative Commons Attribution-Share Alike 3.0 Unported license ("CC-BY-SA"). An explanation of CC-BY-SA is available at [http://creativecommons.org/licenses/by-sa/3.0/](http://creativecommons.org/licenses/by-sa/3.0/) . In accordance with CC-BY-SA, if you distribute this document or an adaptation of it, you must provide the URL for the original version. Red Hat, as the licensor of this document, waives the right to enforce, and agrees not to assert, Section 4d of CC-BY-SA to the fullest extent permitted by applicable law. Red Hat, Red Hat Enterprise Linux, the Shadowman logo, the Red Hat logo, JBoss, OpenShift, Fedora, the Infinity logo, and RHCE are trademarks of Red Hat, Inc., registered in the United States and other countries.

Linux ® is the registered trademark of Linus Torvalds in the United States and other countries.

Java ® is a registered trademark of Oracle and/or its affiliates.

XFS ® is a trademark of Silicon Graphics International Corp. or its subsidiaries in the United States and/or other countries.

MySQL ® is a registered trademark of MySQL AB in the United States, the European Union and other countries.

Node.js ® is an official trademark of Joyent. Red Hat is not formally related to or endorsed by the official Joyent Node.js open source or commercial project. The OpenStack ® Word Mark and OpenStack logo are either registered trademarks/service marks or trademarks/service marks of the OpenStack Foundation, in the United States and other countries and are used with the OpenStack Foundation's permission. We are not affiliated with, endorsed or sponsored by the OpenStack Foundation, or the OpenStack community. All other trademarks are the property of their respective owners.

Format Multi-page Single-page View full doc as PDF

Red Hat logo

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

[Github](https://github.com/redhat-documentation)

reddit

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

[Youtube](https://www.youtube.com/@redhat) [Twitter](https://twitter.com/RedHat)

### Learn

- [Developer resources](https://developers.redhat.com/learn)
- [Cloud learning hub](/learn/learning-paths)
- [Interactive labs](https://www.redhat.com/en/interactive-labs)
- [Training and certification](https://www.redhat.com/services/training-and-certification)
- [Customer support](https://access.redhat.com/support)
- [See all documentation](/en/products)

### Try, buy, &amp; sell

- [Product trial center](https://redhat.com/en/products/trials)
- [Red Hat Ecosystem Catalog](https://catalog.redhat.com/)
- [Red Hat Store](https://www.redhat.com/en/store)
- [Buy online (Japan)](https://www.redhat.com/about/japan-buy)

### Communities

- [Customer Portal Community](https://access.redhat.com/community)
- [Events](https://www.redhat.com/events)
- [How we contribute](https://www.redhat.com/about/our-community-contributions)

### About Red Hat Documentation

We help Red Hat users innovate and achieve their goals with our products and services with content they can trust. [Explore our recent updates](https://www.redhat.com/en/blog/whats-new-docsredhatcom) .

### Making open source more inclusive

Red Hat is committed to replacing problematic language in our code, documentation, and web properties. For more details, see the [Red Hat Blog](https://www.redhat.com/en/blog/making-open-source-more-inclusive-eradicating-problematic-language) .

### About Red Hat

We deliver hardened solutions that make it easier for enterprises to work across platforms and environments, from the core datacenter to the network edge.

### Theme

- [About Red Hat](https://redhat.com/en/about/company)
- [Jobs](https://redhat.com/en/jobs)
- [Events](https://redhat.com/en/events)
- [Locations](https://redhat.com/en/about/office-locations)
- [Contact Red Hat](https://redhat.com/en/contact)
- [Red Hat Blog](https://redhat.com/en/blog)
- [Inclusion at Red Hat](https://redhat.com/en/about/our-culture/diversity-equity-inclusion)
- [Cool Stuff Store](https://coolstuff.redhat.com/)
- [Red Hat Summit](https://www.redhat.com/en/summit)

© 2026 Red Hat

- [Privacy statement](https://redhat.com/en/about/privacy-policy)
- [Terms of use](https://redhat.com/en/about/terms-use)
- [All policies and guidelines](https://redhat.com/en/about/all-policies-guidelines)
- [Digital accessibility](https://redhat.com/en/about/digital-accessibility)

Back to top