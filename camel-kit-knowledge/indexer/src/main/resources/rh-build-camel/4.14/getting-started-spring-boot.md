## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Getting Started with Red Hat build of Apache Camel for Spring Boot
2. [Preface](#idm139912568696864)
3. [1. Access integrated open source capabilities](#camel-integrations)
4. 2. About tooling
5. 3. Getting Started with Red Hat build of Apache Camel for Spring Boot
6. 4. Setting up Maven locally
7. 5. Sample applications
8. 6. Monitoring Camel Spring Boot integrations
9. 7. Using Agroal database connection pool
10. 8. Using Camel with Spring XML
11. 9. XML IO DSL
12. [Legal Notice](#idm139912566249248)

Format Multi-page Single-page View full doc as PDF

# Getting Started with Red Hat build of Apache Camel for Spring Boot

Red Hat build of Apache Camel 4.14

## 

[Legal Notice](#idm139912566249248)

**Abstract**

This guide introduces Red Hat build of Apache Camel and explains the various ways to create and deploy an application using Red Hat build of Apache Camel.

## [Preface Copy link](#idm139912568696864)

### Making open source more inclusive

Red Hat is committed to replacing problematic language in our code, documentation, and web properties. We are beginning with these four terms: master, slave, blacklist, and whitelist. Because of the enormity of this endeavor, these changes will be implemented gradually over several upcoming releases. For more details, see [our CTO Chris Wright's message](https://www.redhat.com/en/blog/making-open-source-more-inclusive-eradicating-problematic-language) .

## [Chapter 1. Access integrated open source capabilities Copy link](#camel-integrations)

Red Hat build of Apache Camel is certified and supported in a large variety of environments, combining the best of open source integration projects into a powerful, enterprise-ready toolkit, designed to simplify and accelerate cloud-native integration for modern businesses.

These integrations include:

- **Apache Camel integration framework** : which implements enterprise integration patterns and offers hundreds of prebuilt components and connectors.
- **Kaoto visual designer** : for Apache Camel.
- **HawtIO modular web console** : for troubleshooting and remote management of integrations.
- **Apache CXF** : for developing and consuming Simple Object Access Protocol (SOAP) web services.
- **Camel CLI** : for iterative integration prototyping.
- **VS Code development tools** : for code assistance and debugging.
- **Extra Camel components** : requiring licensed libraries.
- **Camel golden path templates** : for Backstage.
- **Monitoring and tracing** : via Prometheus and OpenTelemetry.
- **Narayana** : transaction manager.
- **Quarkus and Spring Boot** : runtimes.
- **Member of Quarkus Platform** : with simultaneous security updates.

## [Chapter 2. About tooling Copy link](#introduction-to-tooling)

### [2.1. Languages Copy link](#languages)

In Red Hat build of Apache Camel for Spring Boot, you can define Camel routes using the following languages:

- Java DSL
- YAML
- XML IO

### [2.2. HawtIO Diagnostic Console Copy link](#hawtio_diagnostic_console)

HawtIO Diagnostic Console is a pluggable Web diagnostic console for Red Hat build of Apache Camel built with modern Web technologies such as React and PatternFly. HawtIO provides a central interface to examine and manage the details of one or more deployed HawtIO-enabled containers, depending on your enabled plugins. You can monitor HawtIO and system resources, perform updates, and start or stop services.

For more information, see the [HawtIO Diagnostic Console](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/hawtio_diagnostic_console_guide/index) documentation.

### [2.3. Kaoto Copy link](#kaoto)

Kaoto (Kamel Orchestration Tool) is a low and no code integration designer based on Apache Camel that allows you to create and edit integrations. Kaoto is extendable, flexible, and adaptable to different use cases.

For more information, see the [Creating Camel routes](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/kaoto/index#creating-camel-routes-kaoto) section in the [Kaoto documentation](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/kaoto/index) .

### [2.4. Camel CLI Copy link](#camel_cli)

Camel CLI is a Camel application based on JBang that you can use to create and run Camel routes.

For more information, see the [Creating and running Camel routes](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/tooling_guide_for_red_hat_build_of_apache_camel/index#camel-jbang-running-camel-routes-sb) section in the [Red Hat build of Apache Camel for Spring Boot Tooling Guide](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/tooling_guide_for_red_hat_build_of_apache_camel/index#camel-tooling-guide) documentation.

### [2.5. IDE plugins Copy link](#ide_plugins)

red Hat build of Apache Camel for Spring Boot has plugins for most of the popular development IDEs which provide language support, code/configuration completion, project creation wizards and much more. The plugins are available at each respective IDE marketplace.

- VS Code:
- Eclipse: Eclipse plugin (currently not supported)
- Jetbrains: IntelliJ plugin (currently not supported)

Check the plugin documentation to discover how to create projects for your preferred IDE.

### [2.6. Camel content assist Copy link](#camel_content_assist)

The following plugins provide support for content assist when editing Camel routes and application.properties:

- Eclipse:
- JetBrains: Apache Camel IDEA plugin (not always up to date)
- VS Code:
- Other: `If you are using any other IDE that supports Language Server Protocol you can install and configure Camel Language Server manually` Copy to Clipboard Toggle word wrap Tip `For more information about Tooling in Red Hat build of Apache Camel, see link:https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/tooling_guide_for_red_hat_build_of_apache_camel/index[Tooling Guide]. For more information about scope of development support, see link:https://access.redhat.com/support/offerings/developer/soc[Development Support Scope of Coverage] in the Red Hat Support Portal (requires login).` Copy to Clipboard Toggle word wrap

## [Chapter 3. Getting Started with Red Hat build of Apache Camel for Spring Boot Copy link](#getting-started-with-camel-spring-boot_csb)

This guide introduces Red Hat build of Apache Camel for Spring Boot and demonstrates how to get started building an application using Red Hat build of Apache Camel for Spring Boot.

### [3.1. Red Hat build of Apache Camel for Spring Boot starters Copy link](#camel-spring-boot-starters)

Camel support for Spring Boot provides auto-configuration of the Camel and starters for many Camel [components](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.0/html-single/getting_started_with_red_hat_build_of_apache_camel_for_spring_boot/index#camel-spring-boot-list) . The opinionated auto-configuration of the Camel context auto-detects Camel routes available in the Spring context and registers the key Camel utilities (such as producer template, consumer template and the type converter) as beans.

Note

For information about using a Maven archtype to generate a Camel for Spring Boot application see [Generating a Camel for Spring Boot application using Maven](#generating-a-csb-application-using-maven) .

To get started, you must add the Camel Spring Boot BOM to your Maven `pom.xml` file.

```
<dependencyManagement>

    <dependencies>
        <!-- Camel BOM -->
        <dependency>
            <groupId>com.redhat.camel.springboot.platform</groupId>
            <artifactId>camel-spring-boot-bom</artifactId>
            <version>4.14.2.redhat-00018</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- ... other BOMs or dependencies ... -->
    </dependencies>

</dependencyManagement>
```

Copy to Clipboard

Toggle word wrap

The `camel-spring-boot-bom` is a basic BOM that contains the list of Camel Spring Boot starter JARs.

Next, add the [Camel Spring Boot starter](#camel-spring-boot) to startup the [Camel Context](https://camel.apache.org/manual/camelcontext.html) .

```
<dependencies>
        <!-- Camel Starter -->
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-spring-boot-starter</artifactId>
        </dependency>
        <!-- ... other dependencies ... -->
    </dependencies>
```

Copy to Clipboard

Toggle word wrap

You must also add the [component starters](#camel-spring-boot-list) that your Spring Boot application requires. The following example shows how to add the [auto-configuration starter](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.0/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#spring_boot_auto_configuration_69) to the [MQTT5 component](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.0/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-paho-mqtt5-component-starter) .

```
<dependencies>
        <!-- ... other dependencies ... -->
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-paho-mqtt5</artifactId>
        </dependency>
    </dependencies>
```

Copy to Clipboard

Toggle word wrap

#### [3.1.1. Spring Boot configuration support Copy link](#spring_boot_configuration_support)

Each [starter](#camel-spring-boot-list) lists configuration parameters you can configure in the standard `application.properties` or `application.yml` files. These parameters have the form of `camel.component.[component-name].[parameter]` . For example to configure the URL of the MQTT5 broker you can set:

```
camel.component.paho-mqtt5.broker-url=tcp://localhost:61616
```

Copy to Clipboard

Toggle word wrap

#### [3.1.2. Adding Camel routes Copy link](#adding_camel_routes)

Camel [routes](https://camel.apache.org/manual/routes.html) are detected in the Spring application context, for example a route annotated with `org.springframework.stereotype.Component` will be loaded, added to the Camel context and run.

```
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class MyRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("...")
            .to("...");
    }

}
```

Copy to Clipboard

Toggle word wrap

#### [3.1.3. Using Domain Specific Languages Copy link](#csb-choosing-dsl)

Apache Camel uses a Java Domain Specific Language or DSL for creating Enterprise Integration Patterns or Routes in a variety of domain-specific languages (DSL) as listed below:

- Java DSL: Java based DSL using the fluent builder style.
- XML DSL: XML based DSL in Camel XML files only.
- Yaml DSL for creating routes using YAML format.

##### [3.1.3.1. Advantages of DSLs Copy link](#advantages_of_dsls)

The advantages of using a DSL over general-purpose languages are the following:

- Easier to learn and easier to work with. You can see where the main logic begins and ends.
- Safer code. DSL in Apache Camel has the solid building blocks which binds all the steps together.
- Errors are domain-specific. In case of failures, error descriptions are more explicit and explanatory. Simpler code also means less error-prone code.
- DSLs are designed to be platform-independent. In case of code changes, its impact is delegated to lower layers.

##### [3.1.3.2. Comparing different DSLs Copy link](#comparing_different_dsls)

Following section describes the differences between the DSLs and different scenarios where you may use these DSLs.

Expand

|                                                       | Java DSL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | XML DSL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | YAML DSL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
|-------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Developer tools                                       | - You can use every IDE with Java support. - Red Hat provides the Extension Pack for Apache Camel in VS Code. This pack contains all the necessary extensions to work with Red Hat build of Apache Camel in VS Code. This includes language support for Camel K Java standalone, support for Camel URI completion and diagnostics, and running and debugging Camel routes from the source editor. - Language support and basic Camel textual route debugging. - It provides code assistance and offers a route debugger. | - You can use every IDE with XML support. - Red Hat provides the Extension Pack for Apache Camel in VS Code. This pack contains all the necessary extensions to work with Red Hat build of Apache Camel in VS Code. This includes language support for Camel K Java standalone, support for Camel URI completion and diagnostics, and running and debugging Camel routes from the source editor. - Language support and basic Camel textual route debugging - It provides code assistance and offers a route debugger. | - You can use every IDE with YAML support. - Red Hat provides the Extension Pack for Apache Camel in VS Code. This pack contains all the necessary extensions to work with Red Hat build of Apache Camel in VS Code. This includes language support for Camel K Java standalone, support for Camel URI completion and diagnostics, and running and debugging Camel routes from the source editor. - Language support and basic Camel textual route debugging. - It provides code assistance and offers a route debugger. - It also includes the Kaoto VS Code extension, which offers a visual integration designer. |
| Hawtio / Fuse Console                                 | Hawtio retrieves the routes from the runtime as XML and display the routes regardless of which DSL was used to create the routes.                                                                                                                                                                                                                                                                                                                                                                                        | Hawtio retrieves the routes from the runtime as XML and display the routes regardless of which DSL was used to create the routes.                                                                                                                                                                                                                                                                                                                                                                                      | Hawtio retrieves the routes from the runtime as XML and display the routes regardless of which DSL was used to create the routes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| Software development model                            | The DSL adopts a fluent builder API.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | - Modeling development approach with graphical editor is possible (Eclipse Desktop). - Allows drag-and-drop based development. - Textual-based development is also possible with very mature IDE support.                                                                                                                                                                                                                                                                                                              | Harder to write from scratch. A modelling development approach with a graphical editor is possible.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Debugging code                                        | - There are IDE plug-ins that provide step by step DSL debugging over the EIPs. You can step into the RouteBuilder, but it is called only at startup and not during processing. - Breakpoints can be put in Java code of the core Camel classes. - It is possible to add temporary Processors and use the Java debugger.                                                                                                                                                                                                 | - There are IDE plug-ins that provide step by step DSL debugging over the EIPs. - Breakpoints can be put in Java code of the core Camel classes.                                                                                                                                                                                                                                                                                                                                                                       | - There are IDE plug-ins that provide step by step DSL debugging over the EIPs. - Breakpoints can be put in Java code of the core Camel classes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| Integration with dependency injection (DI) frameworks | Easier to integrate with any DI framework.                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | While it is possible to refer to existing beans from DI frameworks in XML DSL, declaring new beans in XML makes these beans exclusive to Camel itself, and not part of the DI framework (for example, Quarkus or Spring Boot).                                                                                                                                                                                                                                                                                         | While it is possible to refer to existing beans from DI frameworks in YAML DSL, declaring new beans in YAML makes these beans exclusive to Camel itself, and not part of the DI framework (for example, Quarkus or Spring Boot).                                                                                                                                                                                                                                                                                                                                                                                     |
| Team size                                             | More flexible, but harder to read code. Good for small co-located teams that work and support code for a long period.                                                                                                                                                                                                                                                                                                                                                                                                    | - Beneficial for large and disparate teams. - Less flexible, making it challenging to create complicated routes.                                                                                                                                                                                                                                                                                                                                                                                                       | - Beneficial for large and disparate teams. - Less flexible, making it challenging to create complicated routes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| Team structure                                        | Requires the team to have Java developers for developing Camel integrations. Other team members also required to understand Java in order to read the integration flow.                                                                                                                                                                                                                                                                                                                                                  | - XML is a widespread language, and all developers can reuse existing skills when developing with Camel. - It offers a higher level of abstraction and makes it easy to communicate with business developers and support teams.                                                                                                                                                                                                                                                                                        | - YAML is a widespread language, and all developers can reuse existing skills when developing with Camel. - It offers a higher level of abstraction and makes it easy to communicate with business developers and support teams.                                                                                                                                                                                                                                                                                                                                                                                     |
| Developer experience and preference                   | - More suited to experienced developers as Java is more concise than XML, with inner classes and functional aspects. - Java developers tend to prefer pure Java and annotations rather than XML.                                                                                                                                                                                                                                                                                                                         | Ideal for new users, as it offers a graphical approach for designing routes.                                                                                                                                                                                                                                                                                                                                                                                                                                           | Ideal for new users, as it offers a graphical approach for designing routes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |

Show more

### [3.2. Spring Boot Copy link](#camel-spring-boot)

Spring Boot automatically configures Camel for you. The opinionated autoconfiguration of the Camel context auto-detects Camel routes available in the Spring context and registers the key Camel utilities (like producer template, consumer template and the type converter) as beans.

Maven users will need to add the following dependency to their `pom.xml` in order to use this component:

```
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-spring-boot</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

`camel-spring-boot` jar comes with the `spring.factories` file, so as soon as you add that dependency into your classpath, Spring Boot will automatically auto-configure Camel for you.

#### [3.2.1. Camel Spring Boot Starter Copy link](#camel_spring_boot_starter)

Apache Camel ships a [Spring Boot Starter](https://github.com/spring-projects/spring-boot/tree/main/starter) module that allows you to develop Spring Boot applications using starters.

There is also a [sample application](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/spring-boot) available in the examples repository.

To use the starter, add the following to your spring boot pom.xml file:

```
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-spring-boot-starter</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

Then you can just add classes with your Camel routes such as:

```
package com.example;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class MyRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("timer:foo").to("log:bar");
    }
}
```

Copy to Clipboard

Toggle word wrap

Then these routes will be started automatically.

You can customize the Camel application in the `application.properties` or `application.yml` file.

#### [3.2.2. Spring Boot Auto-configuration Copy link](#camel-spring-boot-auto-configuration)

When using spring-boot with Spring Boot make sure to use the following Maven dependency to have support for auto configuration:

```
<dependency>
  <groupId>org.apache.camel.springboot</groupId>
  <artifactId>camel-spring-boot-starter</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

#### [3.2.3. Auto-configured Camel context Copy link](#auto_configured_camel_context)

The most important piece of functionality provided by the Camel auto-configuration is the `CamelContext` instance. Camel auto-configuration creates a `SpringCamelContext` for you and takes care of the proper initialization and shutdown of that context. The created Camel context is also registered in the Spring application context (under the `camelContext` bean name), so you can access it like any other Spring bean.

```
@Configuration
public class MyAppConfig {

  @Autowired
  CamelContext camelContext;

  @Bean
  MyService myService() {
    return new DefaultMyService(camelContext);
  }

}
```

Copy to Clipboard

Toggle word wrap

#### [3.2.4. Auto-detecting Camel routes Copy link](#auto_detecting_camel_routes)

Camel auto-configuration collects all the `RouteBuilder` instances from the Spring context and automatically injects them into the provided `CamelContext` . This means that creating new Camel routes with the Spring Boot starter is as simple as adding the `@Component` annotated class to your classpath:

```
@Component
public class MyRouter extends RouteBuilder {

  @Override
  public void configure() throws Exception {
    from("jms:invoices").to("file:/invoices");
  }

}
```

Copy to Clipboard

Toggle word wrap

Or creating a new route `RouteBuilder` bean in your `@Configuration` class:

```
@Configuration
public class MyRouterConfiguration {

  @Bean
  RoutesBuilder myRouter() {
    return new RouteBuilder() {

      @Override
      public void configure() throws Exception {
        from("jms:invoices").to("file:/invoices");
      }

    };
  }

}
```

Copy to Clipboard

Toggle word wrap

#### [3.2.5. Camel properties Copy link](#camel_properties)

Spring Boot auto-configuration automatically connects to [Spring Boot external configuration](http://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config) (which may contain properties placeholders, OS environment variables or system properties) with the Camel properties support. It basically means that any property defined in `application.properties` file:

```
route.from = jms:invoices
```

Copy to Clipboard

Toggle word wrap

Or set via system property:

```
java -Droute.to=jms:processed.invoices -jar mySpringApp.jar
```

Copy to Clipboard

Toggle word wrap

can be used as placeholders in Camel route:

```
@Component
public class MyRouter extends RouteBuilder {

  @Override
  public void configure() throws Exception {
    from("{{route.from}}").to("{{route.to}}");
  }

}
```

Copy to Clipboard

Toggle word wrap

#### [3.2.6. Custom Camel context configuration Copy link](#custom_camel_context_configuration)

If you want to perform some operations on `CamelContext` bean created by Camel auto-configuration, register `CamelContextConfiguration` instance in your Spring context:

```
@Configuration
public class MyAppConfig {

  @Bean
  CamelContextConfiguration contextConfiguration() {
    return new CamelContextConfiguration() {
      @Override
      void beforeApplicationStart(CamelContext context) {
        // your custom configuration goes here
      }
    };
  }

}
```

Copy to Clipboard

Toggle word wrap

The method `beforeApplicationStart` will be called just before the Spring context is started, so the `CamelContext` instance passed to this callback is fully auto-configured. If you add multiple instances of `CamelContextConfiguration` into your Spring context, each instance is executed.

Tip

For a sample application, see the [Metrics](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/monitoring-micrometrics-grafana-prometheus/) example in the [camel-spring-boot-examples](https://github.com/jboss-fuse/camel-spring-boot-examples/) repository.

#### [3.2.7. Auto-configured consumer and producer templates Copy link](#auto_configured_consumer_and_producer_templates)

Camel auto-configuration provides pre-configured `ConsumerTemplate` and `ProducerTemplate` instances. You can simply inject them into your Spring-managed beans:

```
@Component
public class InvoiceProcessor {

  @Autowired
  private ProducerTemplate producerTemplate;

  @Autowired
  private ConsumerTemplate consumerTemplate;

  public void processNextInvoice() {
    Invoice invoice = consumerTemplate.receiveBody("jms:invoices", Invoice.class);
    ...
    producerTemplate.sendBody("netty-http:http://invoicing.com/received/" + invoice.id());
  }

}
```

Copy to Clipboard

Toggle word wrap

By default, consumer templates and producer templates come with the endpoint cache sizes set to 1000. You can change these values by modifying the following Spring properties:

```
camel.springboot.consumer-template-cache-size = 100
camel.springboot.producer-template-cache-size = 200
```

Copy to Clipboard

Toggle word wrap

#### [3.2.8. Auto-configured TypeConverter Copy link](#typeconverter)

Camel auto-configuration registers a `TypeConverter` instance named `typeConverter` in the Spring context.

```
@Component
public class InvoiceProcessor {

  @Autowired
  private TypeConverter typeConverter;

  public long parseInvoiceValue(Invoice invoice) {
    String invoiceValue = invoice.grossValue();
    return typeConverter.convertTo(Long.class, invoiceValue);
  }

}
```

Copy to Clipboard

Toggle word wrap

##### [3.2.8.1. Spring type conversion API bridge Copy link](#spring_type_conversion_api_bridge)

Spring comes with the powerful [type conversion API](http://docs.spring.io/spring/docs/current/spring-framework-reference/html/validation.html#core-convert) . The Spring API is similar to the Camel type converter API. As both APIs are so similar, Camel Spring Boot automatically registers a bridge converter ( `SpringTypeConverter` ) that delegates to the Spring conversion API. This means that out-of-the-box Camel will treat Spring Converters like Camel ones. With this approach you can use both Camel and Spring converters accessed via Camel `TypeConverter` API:

```
@Component
public class InvoiceProcessor {

  @Autowired
  private TypeConverter typeConverter;

  public UUID parseInvoiceId(Invoice invoice) {
    // Using Spring's StringToUUIDConverter
    UUID id = invoice.typeConverter.convertTo(UUID.class, invoice.getId());
  }

}
```

Copy to Clipboard

Toggle word wrap

Under the hood Camel Spring Boot delegates conversion to the Spring's `ConversionService` instances available in the application context. If no `ConversionService` instance is available, Camel Spring Boot auto-configuration will create one for you.

Tip

For a sample application, see the [Type converter](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/type-converter/) example in the [camel-spring-boot-examples](https://github.com/jboss-fuse/camel-spring-boot-examples/) repository.

#### [3.2.9. Keeping the application alive Copy link](#keeping_the_application_alive)

Camel applications which have this feature enabled launch a new thread on startup for the sole purpose of keeping the application alive by preventing JVM termination. This means that after you start a Camel application with Spring Boot, your application waits for a `Ctrl+C` signal and does not exit immediately.

The controller thread can be activated using the `camel.springboot.main-run-controller` to `true` .

```
camel.springboot.main-run-controller = true
```

Copy to Clipboard

Toggle word wrap

Applications using web modules (for example, applications that import the `org.springframework.boot:spring-boot-web-starter` module), usually don't need to use this feature because the application is kept alive by the presence of other non-daemon threads.

Tip

For a sample application, see the [POJO](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/pojo/) example in the [camel-spring-boot-examples](https://github.com/jboss-fuse/camel-spring-boot-examples/) repository.

#### [3.2.10. Adding XML routes Copy link](#adding_xml_routes)

By default, you can put Camel XML routes in the classpath under the directory camel, which camel-spring-boot will auto-detect and include. You can configure the directory name or turn this off using the configuration option:

```
# turn off
camel.springboot.routes-include-pattern = false
```

Copy to Clipboard

Toggle word wrap

```
# scan only in the com/foo/routes classpath
camel.springboot.routes-include-pattern = classpath:com/foo/routes/*.xml
```

Copy to Clipboard

Toggle word wrap

The XML files should be Camel XML routes ( **not** `<CamelContext>` ) such as:

```
<routes xmlns="http://camel.apache.org/schema/spring">
    <route id="test">
        <from uri="timer://trigger"/>
        <transform>
            <simple>ref:myBean</simple>
        </transform>
        <to uri="log:out"/>
    </route>
</routes>
```

Copy to Clipboard

Toggle word wrap

Tip

For a sample application, see the [XML](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/xml/) example in the [camel-spring-boot-examples](https://github.com/jboss-fuse/camel-spring-boot-examples/) repository.

#### [3.2.11. Testing the JUnit 5 way Copy link](#testing_the_junit_5_way)

For testing, Maven users will need to add the following dependencies to their `pom.xml` :

```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <version>3.5.9</version> <!-- Use the same version as your Spring Boot version -->
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-test-spring-junit5</artifactId>
    <version>4.14.2.redhat-00019</version> <!-- use the same version as your Camel core version -->
    <scope>test</scope>
</dependency>
```

Copy to Clipboard

Toggle word wrap

To test a Camel Spring Boot application, annotate your test class(es) with `@CamelSpringBootTest` . This brings Camel's Spring Test support to your application, so that you can write tests using [Spring Boot test conventions](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-testing.html) .

To get the `CamelContext` or `ProducerTemplate` , you can inject them into the class in the normal Spring manner, using `@Autowired` .

You can also use [camel-test-spring-junit5](https://camel.apache.org/components/4.10.x/others/test-spring-junit5.html) to configure tests declaratively. This example uses the `@MockEndpoints` annotation to auto-mock an endpoint:

```
@CamelSpringBootTest
@SpringBootApplication
@MockEndpoints("direct:end")
public class MyApplicationTest {

    @Autowired
    private ProducerTemplate template;

    @EndpointInject("mock:direct:end")
    private MockEndpoint mock;

    @Test
    public void testReceive() throws Exception {
        mock.expectedBodiesReceived("Hello");
        template.sendBody("direct:start", "Hello");
        mock.assertIsSatisfied();
    }

}
```

Copy to Clipboard

Toggle word wrap

Tip

For a sample application, see the [Infinispan](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/infinispan/) example in the [camel-spring-boot-examples](https://github.com/jboss-fuse/camel-spring-boot-examples/) repository.

The `@CamelSpringBootTest` extends `@SpringBootTest` functionality, including features, plus: - Provides route testing utilities - Includes CamelContext, route advisors, and mock endpoints - Enables Camel test annotations - Works with @MockEndpoints, @UseAdviceWith, etc.

An example of `@UseAdviceWith` is when a route modification is applied at runtime:

```
@CamelSpringBootTest
@SpringBootApplication
public class MySpringBootApplicationTest {

	@Autowired
	private CamelContext camelContext;

	@Autowired
	private ProducerTemplate producerTemplate;

	public static void main(String[] args) {
		SpringApplication.run(MySpringBootApplicationTest.class, args);
	}

	// Spring context fixtures
	@Configuration
	static class TestConfig {

		@Bean
		RoutesBuilder route() {
			return new RouteBuilder() {
				@Override
				public void configure() throws Exception {
					from("timer:hello1?period={{timer.period}}").routeId("hello1")
							.transform().method("myBean", "saySomething")
							.filter(simple("${body} contains 'foo'"))
							.to("log:foo")
							.end()
							.to("stream:out");
				}
			};
		}
	}

	@Test
	public void test() throws Exception {
		// Apply advice before getting the mock endpoint
		AdviceWith.adviceWith(camelContext, "hello1",
				// intercepting an exchange on route
				r -> {
					// replacing consumer with direct component
					r.replaceFromWith("direct:start");
					// mocking producer
					r.mockEndpoints("stream*");
				}
		);

		// Start the context after applying advice
		camelContext.start();

		// Get mock endpoint after advice is applied
		MockEndpoint mock = camelContext.getEndpoint("mock:stream:out", MockEndpoint.class);

		// setting expectations
		mock.expectedMessageCount(1);
		mock.expectedBodiesReceived("Hello World");

		// invoking consumer
		producerTemplate.sendBody("direct:start", null);

		// asserting mock is satisfied
		mock.assertIsSatisfied();
	}
}
```

Copy to Clipboard

Toggle word wrap

In some case we need to declare both, a combination of `@CamelSpringBootTest` and `@SpringBootTest` , when an applicationContext is strictly required, just adding `@SpringBootTest(classes = DocTest.TestApplication.class)` to specify which configuration class to use:

```
@CamelSpringBootTest
@SpringBootTest(classes = MySpringBootApplicationTest.TestConfig.class)
@SpringBootApplication
@MockEndpoints("direct:end")
public class MySpringBootApplicationTest {

	@Autowired
	private ProducerTemplate template;

	@EndpointInject("mock:direct:end")
	private MockEndpoint mock;

	@Configuration
	static class TestConfig {

		@Bean
		RoutesBuilder route() {
			return new RouteBuilder() {
				@Override
				public void configure() throws Exception {
					from("direct:start").routeId("hello2")
							.setBody(simple("Hello World"))
							.log("Received: ${body}")
							.to("direct:end");

					from("direct:end")
							.log("${body}");
				}
			};
		}
	}

	@Test
	public void testReceive() throws Exception {
		mock.expectedMessageCount(1);
		mock.expectedBodiesReceived("Hello World");
		template.sendBody("direct:start", null);
		mock.assertIsSatisfied();
	}

}
```

Copy to Clipboard

Toggle word wrap

Another approach is to test classic Spring XML context setup. In this case, we need to use `@ContextConfiguration` to specify the XML file, retrieving the Main Camel runtime context through dependency injection:

```
@Autowired
protected CamelContext camelContext;
```

Copy to Clipboard

Toggle word wrap

Then, to reloads the Spring ApplicationContext after each test method we can use:

```
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
```

Copy to Clipboard

Toggle word wrap

Alternative modes: `BEFORE_CLASS` , `AFTER_CLASS` , `BEFORE_EACH_TEST_METHOD`

A full example is described here:

```
package com.redhat.plain;
 .....
@CamelSpringBootTest
@ContextConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MySpringBootApplicationPlainTest {

	@Autowired
	protected CamelContext camelContext;

	@EndpointInject("mock:a")
	protected MockEndpoint mockA;

	@Produce("direct:start")
	protected ProducerTemplate start;

	@Test
	public void testPositive() throws Exception {
		assertEquals(ServiceStatus.Started, camelContext.getStatus());
		start.sendBody("Hello");
		MockEndpoint.assertIsSatisfied(camelContext);
		mockA.expectedBodiesReceived("Hello");
	}
}
```

Copy to Clipboard

Toggle word wrap

Where Spring XML context requires to be placed under resources at declared class package `com.redhat.plain` using the naming pattern className-context.xml

**Project Structure**

```
src/
├── main/
│   ├── java/
│   │   └── com/redhat/
│   │       ├── Application.java
│   │       └── route/
│   │           └── MyRoute.java
│   └── resources/
│       └── application.yml
└── test/
    ├── java/
    │   └── com/redhat/
    └── resources/
        └── MySpringBootApplicationPlainTest-context.yml
```

Copy to Clipboard

Toggle word wrap

XML declaration for the example above:

```
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
		xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		xsi:schemaLocation="
       http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
       http://camel.apache.org/schema/spring http://camel.apache.org/schema/spring/camel-spring.xsd
    ">

	<!-- Camel Context configuration -->
	<camelContext id="camelContext" xmlns="http://camel.apache.org/schema/spring">

		<route xmlns="http://camel.apache.org/schema/spring" id="helloX">
			<from id="from1" uri="direct:start"/>
			<filter id="filter1">
				<simple>${body} contains 'foo'</simple>
				<to id="to1" uri="log:foo"/>
			</filter>
			<to id="to2" uri="stream:out"/>
		</route>

	</camelContext>

</beans>
```

Copy to Clipboard

Toggle word wrap

### [3.3. Component Starters Copy link](#camel-spring-boot-list)

Camel Spring Boot supports the following Camel artifacts as Spring Boot Starters:

- [Table 3.1, "Camel Components"](#csb-components)
- [Table 3.2, "Camel Data Formats"](#csb-data-formats)
- [Table 3.3, "Camel Languages"](#csb-languages)
- [Table 3.4, "Miscellaneous Extensions"](#csb-miscellaneous)

Note

The BOM for Red Hat build of Apache Camel for Camel Spring Boot lists both supported and unsupported components. See [Component Starters](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#camel-spring-boot-list) for the latest list of supported components.

Expand

| Component                                                                                                                                                                                                                                                  | Artifact                             | Description                                                                                                                                                                                                       | Support on IBM Power and IBM Z   |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| [AMQP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-amqp-component-starter)                                                             | camel-amqp-starter                   | Messaging with AMQP protocol using Apache QPid Client.                                                                                                                                                            | Yes                              |
| [AWS Cloudwatch](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-aws2-cw-component-starter)                                                | camel-aws2-cw-starter                | Sending metrics to AWS CloudWatch using AWS SDK version 2.x.                                                                                                                                                      | Yes                              |
| [AWS DynamoDB](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-aws2-ddb-component-starter)                                                 | camel-aws2-ddb-starter               | Store and retrieve data from AWS DynamoDB service using AWS SDK version 2.x.                                                                                                                                      | Yes                              |
| [AWS Kinesis](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-aws2-kinesis-component-starter)                                              | camel-aws2-kinesis-starter           | Consume and produce records from and to AWS Kinesis Streams using AWS SDK version 2.x.                                                                                                                            | Yes                              |
| [AWS Lambda](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-aws2-lambda-component-starter)                                                | camel-aws2-lambda-starter            | Manage and invoke AWS Lambda functions using AWS SDK version 2.x.                                                                                                                                                 | Yes                              |
| [AWS S3 Storage Service](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-aws2-s3-component-starter)                                        | camel-aws2-s3-starter                | Store and retrieve objects from AWS S3 Storage Service using AWS SDK version 2.x.                                                                                                                                 | Yes                              |
| [AWS Simple Notification System (SNS)](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-aws2-sns-component-starter)                         | camel-aws2-sns-starter               | Send messages to an AWS Simple Notification Topic using AWS SDK version 2.x.                                                                                                                                      | Yes                              |
| [AWS Simple Queue Service (SQS)](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-aws2-sqs-component-starter)                               | camel-aws2-sqs-starter               | Send and receive messages to/from AWS SQS service using AWS SDK version 2.x.                                                                                                                                      | Yes                              |
| [AWS Secrets Manager](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-aws-secrets-manager-component-starter)                               | camel-aws-secrets-manager-starter    | Manage secrets using AWS Secrets Manager.                                                                                                                                                                         | Yes                              |
| [Azure Key Vault](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-azure-key-vault-component-starter)                                       | camel-azure-key-vault-starter        | Manage secrets and keys in Azure Key Vault Service                                                                                                                                                                | Yes                              |
| [Azure ServiceBus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-azure-servicebus-component-starter)                                     | camel-azure-servicebus-starter       | Send and receive messages to/from Azure Event Bus.                                                                                                                                                                | Yes                              |
| [Azure Storage Blob Service](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-azure-storage-blob-component-starter)                         | camel-azure-storage-blob-starter     | Store and retrieve blobs from Azure Storage Blob Service using SDK v12.                                                                                                                                           | Yes                              |
| [Azure Storage Data Lake Service](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-azure-storage-data-lake-service-component-starter)       | camel-azure-storage-datalake-starter | Sends and receives files to/from Azure Data Lake Storage.                                                                                                                                                         | Yes                              |
| [Azure Storage Queue Service](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-azure-storage-queue-component-starter)                       | camel-azure-storage-queue-starter    | The azure-storage-queue component is used for storing and retrieving the messages to/from Azure Storage Queue using Azure SDK v12.                                                                                | Yes                              |
| [Bean](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-bean-component-starter)                                                             | camel-bean-starter                   | Invoke methods of Java beans stored in Camel registry.                                                                                                                                                            | Yes                              |
| [Bean Validator](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-bean-validator-component-starter)                                         | camel-bean-validator-starter         | Validate the message body using the Java Bean Validation API.                                                                                                                                                     | Yes                              |
| [Browse](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-browse-component-starter)                                                         | camel-browse-starter                 | Inspect the messages received on endpoints supporting BrowsableEndpoint.                                                                                                                                          | Yes                              |
| [Cassandra CQL](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-cassandra-cql-component-starter)                                           | camel-cassandraql-starter            | Integrate with Cassandra 2.0 using the CQL3 API (not the Thrift API). Based on Cassandra Java Driver provided by DataStax.                                                                                        | Yes                              |
| [CICS](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-cics-component-starter)                                                             | camel-cics-starter                   | Interact with CICS® general-purpose transaction processing subsystem.                                                                                                                                             | No                               |
| [Control Bus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-control-bus-component-starter)                                               | camel-controlbus-starter             | Manage and monitor Camel routes.                                                                                                                                                                                  | Yes                              |
| [Cron](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-cron-component-starter)                                                             | camel-cron-starter                   | A generic interface for triggering events at times specified through the Unix cron syntax.                                                                                                                        | Yes                              |
| [Crypto (JCE)](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-crypto-jce-component)                                                       | camel-crypto-starter                 | Sign and verify exchanges using the Signature Service of the Java Cryptographic Extension (JCE).                                                                                                                  | Yes                              |
| [CXF](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-cxf-component-starter)                                                               | camel-cxf-soap-starter               | Expose SOAP WebServices using Apache CXF or connect to external WebServices using CXF WS client.                                                                                                                  | Yes                              |
| [CXF-RS](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-cxf-rs-component-starter)                                                         | camel-cxf-rest-starter               | Expose JAX-RS REST services using Apache CXF or connect to external REST services using CXF REST client.                                                                                                          | Yes                              |
| [Data Format](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-dataformat-component-starter)                                                | camel-dataformat-starter             | Use a Camel Data Format as a regular Camel Component.                                                                                                                                                             | Yes                              |
| [Dataset](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-dataset-component-starter)                                                       | camel-dataset-starter                | Provide data for load and soak testing of your Camel application.                                                                                                                                                 | Yes                              |
| [Direct](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-direct-component-starter)                                                         | camel-direct-starter                 | Call another endpoint from the same Camel Context synchronously.                                                                                                                                                  | Yes                              |
| [Elastic Search](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-elasticsearch-component-starter)                                          | camel-elasticsearch-starter          | Send requests to ElasticSearch via Java Client API.                                                                                                                                                               | Yes                              |
| [FHIR](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-fhir-component-starter)                                                             | camel-fhir-starter                   | Exchange information in the healthcare domain using the FHIR (Fast Healthcare Interoperability Resources) standard.                                                                                               | No                               |
| [File](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-file-component-starter)                                                             | camel-file-starter                   | Read and write files.                                                                                                                                                                                             | Yes                              |
| [Flink](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-flink-component-starter)                                                           | camel-flink-starter                  | Send DataSet jobs to an Apache Flink cluster.                                                                                                                                                                     | Yes                              |
| [FTP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-ftp-component-starter)                                                               | camel-ftp-starter                    | Upload and download files to/from FTP servers.                                                                                                                                                                    | Yes                              |
| [Google BigQuery](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-google-bigquery-component-starter)                                       | camel-google-bigquery-starter        | Google BigQuery data warehouse for analytics.                                                                                                                                                                     | Yes                              |
| [Google Pubsub](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-google-pubsub-component-starter)                                           | camel-google-pubsub-starter          | Send and receive messages to/from Google Cloud Platform PubSub Service.                                                                                                                                           | Yes                              |
| [Google Secret Manager](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-google-secret-manager-component-starter)                           | camel-google-secret-manager-starter  | Manage Google Secret Manager Secrets                                                                                                                                                                              | Yes                              |
| [GraphQL](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-graphql-component-starter)                                                       | camel-graphql-starter                | Send GraphQL queries and mutations to external systems.                                                                                                                                                           | Yes                              |
| [gRPC](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-grpc-component-starter)                                                             | camel-grpc-starter                   | Expose gRPC endpoints and access external gRPC endpoints.                                                                                                                                                         | Yes                              |
| [Hashicorp Vault](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-hashicorp-vault-component-starter)                                       | camel-hashicorp-starter              | Manage secrets in Hashicorp Vault Service.                                                                                                                                                                        | Yes                              |
| [HTTP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-http-component-starter)                                                             | camel-http-starter                   | Send requests to external HTTP servers using Apache HTTP Client 4.x.                                                                                                                                              | Yes                              |
| [Infinispan](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-infinispan-component-starter)                                                 | camel-infinispan-starter             | Read and write from/to Infinispan distributed key/value store and data grid.                                                                                                                                      | No                               |
| [Infinispan Embedded](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-infinispan-embedded-component)                                       | camel-infinispan-embedded-starter    | Read and write from/to Infinispan distributed key/value store and data grid.                                                                                                                                      | Yes                              |
| [JDBC](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jdbc-component-starter)                                                             | camel-jdbc-starter                   | Access databases through SQL and JDBC.                                                                                                                                                                            | Yes                              |
| [Jira](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jira-component-starter)                                                             | camel-jira-starter                   | Interact with JIRA issue tracker.                                                                                                                                                                                 | Yes                              |
| [JMS](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jms-component-starter)                                                               | camel-jms-starter                    | Sent and receive messages to/from a JMS Queue or Topic.                                                                                                                                                           | Yes                              |
| [Jolokia](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jolokia-component-starter)                                                       | camel-jolokia-starter                | integrates the Jolokia agent configuration in Spring Boot.                                                                                                                                                        | Yes                              |
| [JPA](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jpa-component-starter)                                                               | camel-jpa-starter                    | Store and retrieve Java objects from databases using Java Persistence API (JPA).                                                                                                                                  | Yes                              |
| [JSLT](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jslt-component-starter)                                                             | camel-jslt-starter                   | Query or transform JSON payloads using an JSLT.                                                                                                                                                                   | Yes                              |
| [Kafka](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kafka-component-starter)                                                           | camel-kafka-starter                  | Sent and receive messages to/from an Apache Kafka broker.                                                                                                                                                         | Yes                              |
| [Kamelet](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kamelet-component-starter)                                                       | camel-kamelet-starter                | To call Kamelets                                                                                                                                                                                                  | Yes                              |
| [Kubernetes ConfigMap](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-configmap-component-starter)                             | camel-kubernetes-starter             | Perform operations on Kubernetes ConfigMaps and get notified on ConfigMaps changes.                                                                                                                               | Yes                              |
| [Kubernetes Custom Resources](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-custom-resources-component-starter)               | camel-kubernetes-starter             | Perform operations on Kubernetes Custom Resources and get notified on Deployment changes.                                                                                                                         | Yes                              |
| [Kubernetes Deployments](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-deployments-component-starter)                         | camel-kubernetes-starter             | Perform operations on Kubernetes Deployments and get notified on Deployment changes.                                                                                                                              | Yes                              |
| [Kubernetes Event](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-event-component-starter)                                     | camel-kubernetes-starter             | Perform operations on Kubernetes Events and get notified on Events changes.                                                                                                                                       | Yes                              |
| [Kubernetes HPA](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-hpa-component-starter)                                         | camel-kubernetes-starter             | Perform operations on Kubernetes Horizontal Pod Autoscalers (HPA) and get notified on HPA changes.                                                                                                                | Yes                              |
| [Kubernetes Job](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-job-component-starter)                                         | camel-kubernetes-starter             | Perform operations on Kubernetes Jobs.                                                                                                                                                                            | Yes                              |
| [Kubernetes Namespaces](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-namespaces-component-starter)                           | camel-kubernetes-starter             | Perform operations on Kubernetes Namespaces and get notified on Namespace changes.                                                                                                                                | Yes                              |
| [Kubernetes Nodes](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-nodes-component-starter)                                     | camel-kubernetes-starter             | Perform operations on Kubernetes Nodes and get notified on Node changes.                                                                                                                                          | Yes                              |
| [Kubernetes Persistent Volume](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-persistent-volume-component-starter)             | camel-kubernetes-starter             | Perform operations on Kubernetes Persistent Volumes and get notified on Persistent Volume changes.                                                                                                                | Yes                              |
| [Kubernetes Persistent Volume Claim](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-persistent-volume-claim-component-starter) | camel-kubernetes-starter             | Perform operations on Kubernetes Persistent Volumes Claims and get notified on Persistent Volumes Claim changes.                                                                                                  | Yes                              |
| [Kubernetes Pods](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-pods-component-starter)                                       | camel-kubernetes-starter             | Perform operations on Kubernetes Pods and get notified on Pod changes.                                                                                                                                            | Yes                              |
| [Kubernetes Replication Controller](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-replication-controller-component-starter)   | camel-kubernetes-starter             | Yes Perform operations on Kubernetes Replication Controllers and get notified on Replication Controllers changes.                                                                                                 | Yes                              |
| [Kubernetes Resources Quota](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-resources-quota-component-starter)                 | camel-kubernetes-starter             | Perform operations on Kubernetes Resources Quotas.                                                                                                                                                                | Yes                              |
| [Kubernetes Secrets](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-secrets-component-starter)                                 | camel-kubernetes-starter             | Perform operations on Kubernetes Secrets.                                                                                                                                                                         | Yes                              |
| [Kubernetes Service Account](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-service-account-component-starter)                 | camel-kubernetes-starter             | Perform operations on Kubernetes Service Accounts.                                                                                                                                                                | Yes                              |
| [Kubernetes Services](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-services-component-starter)                               | camel-kubernetes-starter             | Perform operations on Kubernetes Services and get notified on Service changes.                                                                                                                                    | Yes                              |
| [Kudu](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kudu-component-starter)                                                             | camel-kudu-starter                   | Interact with Apache Kudu, a free and open source column-oriented data store of the Apache Hadoop ecosystem.                                                                                                      | No                               |
| [Language](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-language-component-starter)                                                     | camel-language-starter               | Execute scripts in any of the languages supported by Camel.                                                                                                                                                       | Yes                              |
| [LDAP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-ldap-component-starter)                                                             | camel-ldap-starter                   | Perform searches on LDAP servers.                                                                                                                                                                                 | Yes                              |
| [Log](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-log-component-starter)                                                               | camel-log-starter                    | Log messages to the underlying logging mechanism.                                                                                                                                                                 | Yes                              |
| [LRA](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-lra-component-starter)                                                               | camel-lra-starter                    | Camel saga binding for Long-Running-Action framework.                                                                                                                                                             | Yes                              |
| [Mail](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-mail-component-starter)                                                             | camel-mail-starter                   | Send and receive emails using imap, pop3 and smtp protocols.                                                                                                                                                      | Yes                              |
| [Mail Microsoft OAuth](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-mail-microsoft-oauth-component-starter)                             | camel-mail-microsoft-oauth-starter   | Camel Mail OAuth2 Authenticator for Microsoft Exchange Online.                                                                                                                                                    | Yes                              |
| [MapStruct](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-mapstruct-component-starter)                                                   | camel-mapstruct-starter              | Type Conversion using Mapstruct.                                                                                                                                                                                  | Yes                              |
| [Master](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-master-component-starter)                                                         | camel-master-starter                 | Have only a single consumer in a cluster consuming from a given endpoint; with automatic failover if the JVM dies.                                                                                                | Yes                              |
| [Micrometer](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-micrometer-component-starter)                                                 | camel-micrometer-starter             | Collect various metrics directly from Camel routes using the Micrometer library.                                                                                                                                  | Yes                              |
| [Minio](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-minio-starter)                                                                     | camel-minio-starter                  | Store and retrieve objects from Minio Storage Service using Minio SDK.                                                                                                                                            | Yes                              |
| [MLLP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-mllp-component-starter)                                                             | camel-mllp-starter                   | Communicate with external systems using the MLLP protocol.                                                                                                                                                        | Yes                              |
| [Mock](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-mock-component-starter)                                                             | camel-mock-starter                   | Test routes and mediation rules using mocks.                                                                                                                                                                      | Yes                              |
| [MongoDB](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-mongodb-component-starter)                                                       | camel-mongodb-starter                | Perform operations on MongoDB documents and collections.                                                                                                                                                          | Yes                              |
| [MyBatis](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-mybatis-component)                                                               | camel-mybatis-starter                | Performs a query, poll, insert, update or delete in a relational database using MyBatis.                                                                                                                          | Yes                              |
| [Netty](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-netty-component-starter)                                                           | camel-netty-starter                  | Socket level networking using TCP or UDP with Netty 4.x.                                                                                                                                                          | Yes                              |
| [Observability Services](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-observability-services-component-starter)                         | camel-observability-services         | Camel Observability Services                                                                                                                                                                                      | Yes                              |
| [Olingo4](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-olingo4-component-starter)                                                       | camel-olingo4-starter                | Communicate with OData 4.0 services using Apache Olingo OData API.                                                                                                                                                | Yes                              |
| [OpenSearch](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-opensearch-component-starter)                                                 | camel-opensearch-starter             | Send requests to OpenSearch via Java Client API.                                                                                                                                                                  | Yes                              |
| [Openshift Builds](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-openshift-builds-component-starter)                          | camel-kubernetes-starter             | Perform operations on OpenShift Builds.                                                                                                                                                                           | Yes                              |
| [Openshift Deployment Configs](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kubernetes-openshift-deploymentconfigs-component-starter)   | camel-kubernetes-starter             | Perform operations on Openshift Deployment Configs and get notified on Deployment Config changes.                                                                                                                 | Yes                              |
| [Netty HTTP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-netty-http-component-starter)                                                 | camel-netty-http-starter             | Netty HTTP server and client using the Netty 4.x.                                                                                                                                                                 | Yes                              |
| [Paho](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-paho-component-starter)                                                             | camel-paho-starter                   | Communicate with MQTT message brokers using Eclipse Paho MQTT Client.                                                                                                                                             | Yes                              |
| [Paho MQTT 5](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-paho-mqtt5-component-starter)                                                | camel-paho-mqtt5-starter             | Communicate with MQTT message brokers using Eclipse Paho MQTT v5 Client.                                                                                                                                          | Yes                              |
| [Platform HTTP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-platform-http-component-starter)                                           | camel-platform-http-starter          | Expose HTTP endpoints using the HTTP server available in the current platform.                                                                                                                                    | Yes                              |
| [Quartz](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-quartz-component-starter)                                                         | camel-quartz-starter                 | Schedule sending of messages using the Quartz 2.x scheduler.                                                                                                                                                      | Yes                              |
| [Ref](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-ref-component-starter)                                                               | camel-ref-starter                    | Route messages to an endpoint looked up dynamically by name in the Camel Registry.                                                                                                                                | Yes                              |
| [REST](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-rest-component-starter)                                                             | camel-rest-starter                   | Expose REST services or call external REST services.                                                                                                                                                              | Yes                              |
| [Saga](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-saga-component-starter)                                                             | camel-saga-starter                   | Execute custom actions within a route using the Saga EIP.                                                                                                                                                         | Yes                              |
| [Salesforce](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-salesforce-component-starter)                                                 | camel-salesforce-starter             | Communicate with Salesforce using Java DTOs.                                                                                                                                                                      | Yes                              |
| [SAP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-sap-component-starter)                                                               | camel-sap-starter                    | Uses the SAP Java Connector (SAP JCo) library to facilitate bidirectional communication with SAP and the SAP IDoc library to facilitate the transmission of documents in the Intermediate Document (IDoc) format. | Yes                              |
| [Scheduler](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-scheduler-component-starter)                                                   | camel-scheduler-starter              | Generate messages in specified intervals using java.util.concurrent.ScheduledExecutorService.                                                                                                                     | Yes                              |
| [SEDA](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-seda-component-starter)                                                             | camel-seda-starter                   | Asynchronously call another endpoint from any Camel Context in the same JVM.                                                                                                                                      | Yes                              |
| [Servlet](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-servlet-component-starter)                                                       | camel-servlet-starter                | Serve HTTP requests by a Servlet.                                                                                                                                                                                 | Yes                              |
| [Slack](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-slack-component-starter)                                                           | camel-slack-starter                  | Send and receive messages to/from Slack.                                                                                                                                                                          | Yes                              |
| [SMB](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-smb-component-starter)                                                               | camel-smb-starter                    | Receive files from SMB (Server Message Block) shares.                                                                                                                                                             | Yes                              |
| [Smooks](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-smooks-dataformat-starter)                                                        | camel-smooks-starter                 | Transform and bind XML as well as non-XML data, including EDI, CSV, JSON, and YAML using Smooks.                                                                                                                  | Yes                              |
| [SNMP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-snmp-component-starter)                                                             | camel-snmp-starter                   | Receive traps and poll SNMP (Simple Network Management Protocol) capable devices.                                                                                                                                 | Yes                              |
| [Splunk](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-splunk-component-starter)                                                         | camel-splunk-starter                 | Publish or search for events in Splunk.                                                                                                                                                                           | No                               |
| [Spring Batch](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-spring-batch-component-starter)                                             | camel-spring-batch-starter           | Send messages to Spring Batch for further processing.                                                                                                                                                             | Yes                              |
| [Spring JDBC](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-spring-JDBC-component-starter)                                               | camel-spring-jdbc-starter            | Access databases through SQL and JDBC with Spring Transaction support.                                                                                                                                            | Yes                              |
| [Spring LDAP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-spring-ldap-component-starter)                                               | camel-spring-ldap-starter            | Perform searches in LDAP servers using filters as the message payload.                                                                                                                                            | Yes                              |
| [Spring RabbitMQ](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-spring-rabbitMQ-component-starter)                                       | camel-spring-rabbitmq-starter        | Send and receive messages from RabbitMQ using Spring RabbitMQ client.                                                                                                                                             | Yes                              |
| [Spring Redis](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-spring-redis-component-starter)                                             | camel-spring-redis-starter           | Send and receive messages from Redis.                                                                                                                                                                             | Yes                              |
| [Spring Webservice](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-spring-webservice-component-starter)                                   | camel-spring-ws-starter              | You can use this component to integrate with Spring Web Services. It offers client-side support for accessing web services and server-side support for creating your contract-first web services.                 | Yes                              |
| [SQL](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-sql-component-starter)                                                               | camel-sql-starter                    | Perform SQL queries using Spring JDBC.                                                                                                                                                                            | Yes                              |
| [SQL Stored Procedure](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-sql-stored-component-starter)                                       | camel-sql-starter                    | Perform SQL queries as a JDBC Stored Procedures using Spring JDBC.                                                                                                                                                | Yes                              |
| [SSH](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-ssh-component-starter)                                                               | camel-ssh-starter                    | Execute commands on remote hosts using SSH.                                                                                                                                                                       | Yes                              |
| [Stub](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-stub-component-starter)                                                             | camel-stub-starter                   | Stub out any physical endpoints while in development or testing.                                                                                                                                                  | Yes                              |
| [Telegram](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-telegram-component-starter)                                                     | camel-telegram-starter               | Send and receive messages acting as a Telegram Bot Telegram Bot API.                                                                                                                                              | Yes                              |
| [Timer](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-timer-component-starter)                                                           | camel-timer-starter                  | Generate messages in specified intervals using java.util.Timer.                                                                                                                                                   | Yes                              |
| [Validator](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-validator-component-starter)                                                   | camel-validator-starter              | Validate the payload using XML Schema and JAXP Validation.                                                                                                                                                        | Yes                              |
| [Velocity](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-velocity-component-starter)                                                     | camel-velocity-starter               | Transform messages using a Velocity template.                                                                                                                                                                     | Yes                              |
| [Vert.x HTTP Client](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-vertx-http-component-starter)                                         | camel-vertx-http-starter             | Send requests to external HTTP servers using Vert.x.                                                                                                                                                              | Yes                              |
| [Vert.x WebSocket](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-vertx-websocket-component-starter)                                      | camel-vertx-websocket-starter        | Expose WebSocket endpoints and connect to remote WebSocket servers using Vert.x.                                                                                                                                  | Yes                              |
| [Webhook](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-webhook-component-starter)                                                       | camel-webhook-starter                | Expose webhook endpoints to receive push notifications for other Camel components.                                                                                                                                | Yes                              |
| [XJ](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-xj-component-starter)                                                                 | camel-xj-starter                     | Transform JSON and XML message using a XSLT.                                                                                                                                                                      | Yes                              |
| [XSLT](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-xslt-component-starter)                                                             | camel-xslt-starter                   | Transforms XML payload using an XSLT template.                                                                                                                                                                    | Yes                              |
| [XSLT Saxon](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-xslt-saxon-component-starter)                                                 | camel-xslt-saxon-starter             | Transform XML payloads using an XSLT template using Saxon.                                                                                                                                                        | Yes                              |

Show more

Expand

| Component                                                                                                                                                                                                               | Artifact                       | Description                                                                                                | Support on IBM Power and IBM Z   |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|------------------------------------------------------------------------------------------------------------|----------------------------------|
| [Avro](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-avro-dataformat-starter)                         | camel-avro-starter             | Serialize and deserialize messages using Apache Avro binary data format.                                   | Yes                              |
| [Avro Jackson](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-avro-jackson-dataformat-starter)         | camel-jackson-avro-starter     | Marshal POJOs to Avro and back using Jackson.                                                              | Yes                              |
| [Bindy](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-bindy-dataformat-starter)                       | camel-bindy-starter            | Marshal and unmarshal between POJOs and key-value pair (KVP) format using Camel Bindy.                     | Yes                              |
| [BeanIO](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-beanio-dataformat-starter)                     | camel-beanio-starter           | Marshal and unmarshal Java beans to and from flat files (such as CSV, delimited, or fixed length formats). | Yes                              |
| [HL7](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-hl7-dataformat-starter)                           | camel-hl7-starter              | Marshal and unmarshal HL7 (Health Care) model objects using the HL7 MLLP codec.                            | Yes                              |
| [JacksonXML](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jacksonxml-dataformat-starter)             | camel-jacksonxml-starter       | Unmarshal a XML payloads to POJOs and back using XMLMapper extension of Jackson.                           | Yes                              |
| [JAXB](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jaxb-dataformat-component-starter)               | camel-jaxb-starter             | Unmarshal XML payloads to POJOs and back using JAXB2 XML marshalling standard.                             | Yes                              |
| [JSON Gson](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-json-gson-dataformat-starter)               | camel-gson-starter             | Marshal POJOs to JSON and back using Gson                                                                  | Yes                              |
| [JSON Jackson](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-json-jackson-dataformat-starter)         | camel-jackson-starter          | Marshal POJOs to JSON and back using Jackson                                                               | Yes                              |
| [Protobuf Jackson](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-protobuf-jackson-dataformat-starter) | camel-jackson-protobuf-starter | Marshal POJOs to Protobuf and back using Jackson.                                                          | Yes                              |
| [SOAP](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-soap-dataformat-starter)                         | camel-soap-starter             | Marshal Java objects to SOAP messages and back.                                                            | Yes                              |
| [Zip File](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-zipfile-dataformat-starter)                  | camel-zipfile-starter          | Compression and decompress streams using java.util.zip.ZipStream.                                          | Yes                              |

Show more

Expand

| Language                                                                                                                                                                                                              | Artifact               | Description                                                  | Support on IBM Power and IBM Z   |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|--------------------------------------------------------------|----------------------------------|
| [Constant](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-constant-language-starter)                 | camel-core-starter     | A fixed value set only once during the route startup.        | Yes                              |
| [CSimple](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-csimple-language-starter)                   | camel-core-starter     | Evaluate a compiled simple expression.                       | Yes                              |
| [ExchangeProperty](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-exchangeproperty-language-starter) | camel-core-starter     | Gets a property from the Exchange.                           | Yes                              |
| [File](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-file-language-starter)                         | camel-core-starter     | File related capabilities for the Simple language.           | Yes                              |
| [Groovy](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-groovy-language-starter)                     | camel-groovy-starter   | Evaluates a Groovy script.                                   | Yes                              |
| [Header](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-header-language-starter)                     | camel-core-starter     | Gets a header from the Exchange.                             | Yes                              |
| [JQ](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jq-language-component-starter)                   | camel-jq-starter       | Evaluates a JQ expression against a JSON message body.       | Yes                              |
| [JSONPath](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jsonpath-language-starter)                 | camel-jsonpath-starter | Evaluates a JSONPath expression against a JSON message body. | Yes                              |
| [Ref](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-ref-language-starter)                           | camel-core-starter     | Uses an existing expression from the registry.               | Yes                              |
| [Simple](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-simple-language-starter)                     | camel-core-starter     | Evaluates a Camel simple expression.                         | Yes                              |
| [Tokenize](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-tokenize-language-starter)                 | camel-core-starter     | Tokenize text payloads using delimiter patterns.             | Yes                              |
| [XML Tokenize](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-xml-tokenize-language-starter)         | camel-xml-jaxp-starter | Tokenize XML payloads.                                       | Yes                              |
| [XPath](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-xpath-language-starter)                       | camel-xpath-starter    | Evaluates an XPath expression against an XML payload.        | Yes                              |
| [XQuery](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-saxon-language-starter)                      | camel-saxon-starter    | Query and/or transform XML payloads using XQuery and Saxon.  | Yes                              |

Show more

Expand

| Extensions                                                                                                                                                                                                       | Artifact                       | Description                             | Support on IBM Power and IBM Z                                                                                                                                                                                       |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|-----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [Jasypt](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-jasypt-component-starter)               | camel-jasypt-starter           | Security using Jasypt                   | Yes                                                                                                                                                                                                                  |
| [Kamelet Main](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-kamelet-main-component-starter)   | camel-kamelet-main-starter     | Main to run Kamelet standalone          | Yes                                                                                                                                                                                                                  |
| [Openapi Java](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-openapi-java-starter)             | camel-openapi-java-starter     | Rest-dsl support for using openapi doc  | Yes                                                                                                                                                                                                                  |
| [OpenTelemetry](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-opentelemetry-component-starter) | camel-opentelemetry-starter    | Distributed tracing using OpenTelemetry | Yes                                                                                                                                                                                                                  |
| [Resilience4j](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-resilience4j-component-starter)   | camel-resilience4j-starter     | Circuit Breaker EIP using Resilience4j  | [Spring Security](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-spring-security-component-starter) |
| camel-spring-security-starter                                                                                                                                                                                    | Security using Spring Security | Yes                                     | [YAML DSL](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index#csb-camel-yaml-dsl-component-starter)               |

Show more

### [3.4. Starter Configuration Copy link](#camel-spring-boot-starter-configuration)

Clear and accessible configuration is a crucial part of any application. Camel [starters](#camel-spring-boot-list) fully support Spring Boot's [external configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config) mechanism. You can also configure them through Spring [Beans](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans-definition) for more complex use cases.

#### [3.4.1. Using External Configuration Copy link](#using_external_configuration)

Internally, every [starter](#camel-spring-boot-list) is configured through Spring Boot's [ConfigurationProperties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties.java-bean-binding) . Each configuration parameter can be set in various [ways](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config) ( `application.[properties|json|yaml]` files, command line arguments, environments variables etc.). Parameters have the form of `camel.[component|language|dataformat].[name].[parameter]`

For example to configure the URL of the MQTT5 broker you can set:

```
camel.component.paho-mqtt5.broker-url=tcp://localhost:61616
```

Copy to Clipboard

Toggle word wrap

Or to configure the `delimeter` of the CSV dataformat to be a semicolon(;) you can set:

```
camel.dataformat.csv.delimiter=;
```

Copy to Clipboard

Toggle word wrap

Camel will use the [Type Converter](#typeconverter) mechanism when setting properties to the desired type.

You can refer to beans in the Registry using the `#bean:name` :

```
camel.component.jms.transactionManager=#bean:myjtaTransactionManager
```

Copy to Clipboard

Toggle word wrap

The `Bean` would be typically created in Java:

```
@Bean("myjtaTransactionManager")
public JmsTransactionManager myjtaTransactionManager(PooledConnectionFactory pool) {
    JmsTransactionManager manager = new JmsTransactionManager(pool);
    manager.setDefaultTimeout(45);
    return manager;
}
```

Copy to Clipboard

Toggle word wrap

Beans can also be created in [configuration files](https://camel.apache.org/components/4.10.x/others/main.html#_specifying_custom_beans) but this is not recommended for complex use cases.

#### [3.4.2. Using Beans Copy link](#using_beans)

Starters can also be created and configured via Spring [Beans](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans-definition) . Before creating a starter , Camel will first lookup it up in the Registry by it's name if it already exists. For example to configure a Kafka component:

```
@Bean("kafka")
public KafkaComponent kafka(KafkaConfiguration kafkaconfiguration){
    return ComponentsBuilderFactory.kafka()
                        .brokers("{{kafka.host}}:{{kafka.port}}")
                        .build();
}
```

Copy to Clipboard

Toggle word wrap

The `Bean` name has to be equal to that of the Component, Dataformat or Language that you are configuring. If the `Bean` name isn't specified in the annotation it will be set to the method name.

Typical Camel Spring Boot projects will use a combination of external configuration and Beans to configure an application. For more examples on how to configure your Camel Spring Boot project, see the [examples repository](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/) .

### [3.5. Generating a Camel for Spring Boot application using Maven Copy link](#generating-a-csb-application-using-maven)

You can generate a Red Hat build of Apache Camel for Spring Boot application using the Maven archetype `org.apache.camel.archetypes:camel-archetype-spring-boot:4.14.2.redhat-00018` .

**Procedure**

1. Run the following command: `mvn archetype:generate \ -DarchetypeGroupId=org.apache.camel.archetypes \ -DarchetypeArtifactId=camel-archetype-spring-boot \ -DarchetypeVersion=4.14.2.redhat-00018 \ -DgroupId=com.redhat \ -DartifactId=csb-app \ -Dversion=1.0-SNAPSHOT \ -DinteractiveMode=false` Copy to Clipboard Toggle word wrap
2. Build the application: `mvn package -f csb-app/pom.xml` Copy to Clipboard Toggle word wrap
3. Run the application: `java -jar csb-app/target/csb-app-1.0-SNAPSHOT.jar` Copy to Clipboard Toggle word wrap
4. Verify that the application is running by examining the console log for the *Hello World* output which is generated by the application. `com.redhat.MySpringBootApplication : Started MySpringBootApplication in 3.514 seconds (JVM running for 4.006) Hello World Hello World` Copy to Clipboard Toggle word wrap

### [3.6. Upgrading Red Hat build of Apache Camel on Spring Boot version with Camel update recipes Copy link](#csb-camel-upgrade-recipes)

Migrating the code for Apache Camel on Spring Boot often involves adapting to new Camel API changes and renaming the classes. To address this, you can use Camel update recipes based on OpenRewrite. These recipes assists with manual migrations and make it more efficient.

You can update the Red Hat build of Apache Camel on Spring Boot application using Camel JBang. Camel JBang provides the `camel update` command. It has two main operations:

- `run` : Executes the actual update process

The update process uses the [Apache Camel Open Rewrite recipes](https://github.com/apache/camel-upgrade-recipes) . It supports following application types:

- Plain Camel (camel-main)
- Camel Quarkus
- Camel Spring Boot

Here, Camel and Camel Spring Boot updates mainly use the `camel-upgrade-recipes` , while Camel Quarkus updates involve both the Quarkus runtime (via [Rewrite Quarkus](https://github.com/openrewrite/rewrite-quarkus) ) and Apache Camel recipes.

#### [3.6.1. Running Camel Updates Copy link](#running_camel_updates)

To perform the update to this version, use:

```
$ camel update run { camelSpringBootVersion } --runtime = spring-boot
```

Copy to Clipboard

Toggle word wrap

Note

The update commands must be executed in the project directory that contains the `pom.xml` file.

##### [3.6.1.1. Configuration Options Copy link](#configuration_options)

You can customize the update process with several options that are available:

- `--runtime` : Specifies the application type:
- `--repos` : Additional Maven repositories to use during the update
- `--dry-run` : Preview the changes without applying them
- `--extraActiveRecipes` : Comma-separated list of additional recipe names to apply
- `--extraRecipeArtifactCoordinates` : Comma-separated list of Maven coordinates for extra recipes (format: groupId:artifactId:version)
- `--help` : To see all available options.

##### [3.6.1.2. Updating a plain Camel application Copy link](#updating_a_plain_camel_application)

Following example shows how to update a plain Camel application.

```
$ camel update run 4.14 .2.redhat-00014 --runtime = camel-main --repos = https://myMaven/repo --extraActiveRecipes = my.first.Recipe,my.second.Recipe --extraRecipeArtifactCoordinates = ex.my.org:recipes:1.0.0
```

Copy to Clipboard

Toggle word wrap

##### [3.6.1.3. Updating a Spring Boot application Copy link](#updating_a_spring_boot_application)

You can use the following command to update a Spring Boot application. You can use the `-extraActiveRecipes` option to run the extra Spring Boot upgrade.

```
$ camel update run 4.14 .2.redhat-00014 --runtime = spring-boot --extraActiveRecipes = org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3 --extraRecipeArtifactCoordinates = org.openrewrite.recipe:rewrite-spring:6.0.2
```

Copy to Clipboard

Toggle word wrap

### [3.7. Deploying a Camel Spring Boot application to OpenShift Copy link](#deploying-camel-spring-boot-application-to-openshift)

This guide demonstrates how to deploy a Camel Spring Boot application to OpenShift.

**Prerequisites**

- You have access to the OpenShift cluster.
- The OpenShift `oc` CLI client is installed or you have access to the OpenShift Container Platform web console.

Note

The certified OpenShift Container platforms are listed in the [Camel for Spring Boot Supported Configurations](https://access.redhat.com/articles/6970899) . The Red Hat OpenJDK 11 (ubi8/openjdk-11) container image is used in the following example.

**Procedure**

1. Generate a Camel for Spring Boot application using Maven by following the instructions in section 1.5 [Generating a Camel for Spring Boot application using Maven](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/getting_started_with_red_hat_build_of_apache_camel_for_spring_boot/index#generating-a-csb-application-using-maven) of this guide.
2. Under the directory which the modified pom.xml exists, execute the following command. `mvn clean -DskipTests oc:deploy -Popenshift` Copy to Clipboard Toggle word wrap
3. Verify that the CSB application is running on the pod. `oc logs -f dc/csb-app` Copy to Clipboard Toggle word wrap

### [3.8. Applying patch to Red Hat build of Apache Camel for Spring Boot Copy link](#maven-patch-to-camel-spring-boot-application)

Using the new `patch-maven-plugin` mechanism, you can apply a patch to your Red Hat Red Hat build of Apache Camel for Spring Boot application. This mechanism allows you to change the individual versions provided by different Red Hat application BOMS, for example, `camel-spring-boot-bom` .

The purpose of the **patch-maven-plugin** is to update the versions of the dependencies listed in the Camel on Spring Boot BOM to the versions specified in the patch metadata that you wish to apply to your applications.

The patch-maven-plugin performs the following operations:

- Retrieve the patch metadata related to current Red Hat application BOMs.
- Apply the version changes to &lt;dependencyManagement&gt; imported from the BOMs.

After the `patch-maven-plugin` fetches the metadata, it iterates through all managed and direct dependencies of the project where the plugin was declared and replaces the dependency versions (if they match) using CVE/patch metadata. After the versions are replaced, the Maven build continues and progresses through standard Maven project stages.

**Procedure**

The following procedure explains how to apply the patch to your application.

1. Add `patch-maven-plugin` to your project's `pom.xml` file. The version of the `patch-maven-plugin` must be the same as the version of the Camel on Spring Boot BOM. `<build> <plugins> <<plugin> <groupId>com.redhat.camel.springboot.platform</groupId> <artifactId>patch-maven-plugin</artifactId> <version>${camel-spring-boot-version}</version> <extensions>true</extensions> </plugin> </plugins> </build>` Copy to Clipboard Toggle word wrap
2. When you run any of the `mvn clean deploy` , `mvn validate` , or `mvn dependency:tree` commands, the plugin searches through the project modules to check if the modules use the Red Hat Red Hat build of Apache Camel for Spring Boot BOM. Only the following is the supported BOM:
3. If the plugin does not find the above BOM, the plugin displays the following messages: `$ mvn clean install [INFO] Scanning for projects... [INFO] ========== Red Hat Maven patching ========== [INFO] [PATCH] No project in the reactor uses Camel on Spring Boot product BOM. Skipping patch processing. [INFO] [PATCH] Done in 7ms =================================================` Copy to Clipboard Toggle word wrap
4. If the correct BOM is used, the patch metadata is found, but without any patches. `$ mvn clean install [INFO] Scanning for projects... [INFO] ========== Red Hat Maven patching ========== [INFO] [PATCH] Reading patch metadata and artifacts from 2 project repositories [INFO] [PATCH] - redhat-ga-repository: http://maven.repository.redhat.com/ga/ [INFO] [PATCH] - central: https://repo.maven.apache.org/maven2 Downloading from redhat-ga-repository: http://maven.repository.redhat.com/ga/com/redhat/camel/springboot/platform/redhat-camel-spring-boot-patch-metadata/maven-metadata.xml Downloading from central: https://repo.maven.apache.org/maven2/com/redhat/camel/springboot/platform/redhat-camel-spring-boot-patch-metadata/maven-metadata.xml [INFO] [PATCH] Resolved patch descriptor: /path/to/.m2/repository/com/redhat/camel/springboot/platform/redhat-camel-spring-boot-patch-metadata/3.20.1.redhat-00043/redhat-camel-spring-boot-patch-metadata-3.20.1.redhat-00043.xml [INFO] [PATCH] Patch metadata found for com.redhat.camel.springboot.platform/camel-spring-boot-bom/[3.20,3.21) [INFO] [PATCH] Done in 938ms =================================================` Copy to Clipboard Toggle word wrap
5. The `patch-maven-plugin` attempts to fetch this Maven metadata.
6. The `patch-maven-plugin` parses the metadata to select the version which applies to the current project. This action is possible only for the Maven projects using Camel on Spring Boot BOM with the specific version. Only the metadata that matches the version range or later is applicable, and it fetches only the latest version of the metadata.
7. The `patch-maven-plugin` collects a list of remote Maven repositories for downloading the patch metadata identified by `groupId` , `artifactId` , and `version` found in previous steps. These Maven repositories are listed in the project's `<repositories>` elements in the active profiles, and also the repositories from the `settings.xml` file. `$ mvn clean install [INFO] Scanning for projects... [INFO] ========== Red Hat Maven patching ========== [INFO] [PATCH] Reading patch metadata and artifacts from 2 project repositories [INFO] [PATCH] - MRRC-GA: https://maven.repository.redhat.com/ga [INFO] [PATCH] - central: https://repo.maven.apache.org/maven2` Copy to Clipboard Toggle word wrap
8. Whether the metadata comes from a remote repository, local repository, or ZIP file, it is analyzed by the `patch-maven-plugin` . The fetched metadata contains a list of CVEs, and for each CVE, we have a list of the affected Maven artifacts (specified by glob patterns and version ranges) together with a version that contains a fix for a given CVE. For example, `<?xml version="1.0" encoding="UTF-8" ?> <<metadata xmlns="urn:redhat:patch-metadata:1"> <product-bom groupId="com.redhat.camel.springboot.platform" artifactId="camel-spring-boot-bom" versions="[3.20,3.21)" /> <cves> </cves> <fixes> <fix id="HF0-1" description="logback-classic (Example) - Version Bump"> <affects groupId="ch.qos.logback" artifactId="logback-classic" versions="[1.0,1.3.0)" fix="1.3.0" /> </fix> </fixes> </metadata>` Copy to Clipboard Toggle word wrap
9. Finally a list of fixes specified in patch metadata is consulted when iterating over all managed dependencies in the current project. These dependencies (and managed dependencies) that match are changed to fixed versions. For example: `$ mvn dependency:tree [INFO] Scanning for projects... [INFO] ========== Red Hat Maven patching ========== [INFO] [PATCH] Reading patch metadata and artifacts from 3 project repositories [INFO] [PATCH] - redhat-ga-repository: http://maven.repository.redhat.com/ga/ [INFO] [PATCH] - local: file:///path/to/.m2/repository [INFO] [PATCH] - central: https://repo.maven.apache.org/maven2 [INFO] [PATCH] Resolved patch descriptor:/path/to/.m2/repository/com/redhat/camel/springboot/platform/redhat-camel-spring-boot-patch-metadata/3.20.1.redhat-00043/redhat-camel-spring-boot-patch-metadata-3.20.1.redhat-00043.xml [INFO] [PATCH] Patch metadata found for com.redhat.camel.springboot.platform/camel-spring-boot-bom/[3.20,3.21) [INFO] [PATCH] - patch contains 1 patch fix [INFO] [PATCH] Processing managed dependencies to apply patch fixes... [INFO] [PATCH] - HF0-1: logback-classic (Example) - Version Bump [INFO] [PATCH] Applying change ch.qos.logback/logback-classic/[1.0,1.3.0) -> 1.3.0 [INFO] [PATCH] Project com.test:yaml-routes [INFO] [PATCH] - managed dependency: ch.qos.logback/logback-classic/1.2.11 -> 1.3.0 [INFO] [PATCH] Done in 39ms =================================================` Copy to Clipboard Toggle word wrap

**Skipping the patch**

If you do not wish to apply a specific patch to your project, the `patch-maven-plugin` provides a `skip` option. Assuming that you have already added the `patch-maven-plugin` to the project's `pom.xml` file, and you do not wish to alter the versions, you can use one of the following method to skip the patch.

- Add the skip option to your project's `pom.xml` file as follows.

```
<build>
    <plugins>
        <plugin>
            <groupId>com.redhat.camel.springboot.platform</groupId>
            <artifactId>patch-maven-plugin</artifactId>
            <version>${camel-spring-boot-version}</version>
            <extensions>true</extensions>
            <configuration>
                <skip>true</skip>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Copy to Clipboard

Toggle word wrap

- Or use the `-DskipPatch` option when running the `mvn` command as follows.

```
$ mvn clean install -DskipPatch
[INFO] Scanning for projects...
[INFO]
[INFO] -------------------------< com.example:test-csb >-------------------------
[INFO] Building A Camel Spring Boot Route 1.0-SNAPSHOT
...
```

Copy to Clipboard

Toggle word wrap

As shown in the above output, the `patch-maven-plugin` was not invoked, which resulted in the patch not being applied to the application.

### [3.9. Camel REST DSL OpenApi Maven Plugin Copy link](#camel-rest-dsl-openapi-maven-plugin)

The Camel REST DSL OpenApi Maven Plugin supports the following goals.

- camel-restdsl-openapi:generate - To generate consumer REST DSL RouteBuilder source code from OpenApi specification
- camel-restdsl-openapi:generate-with-dto - To generate consumer REST DSL RouteBuilder source code from OpenApi specification and with DTO model classes generated via the swagger-codegen-maven-plugin.
- camel-restdsl-openapi:generate-xml - To generate consumer REST DSL XML source code from OpenApi specification
- camel-restdsl-openapi:generate-xml-with-dto - To generate consumer REST DSL XML source code from OpenApi specification and with DTO model classes generated via the swagger-codegen-maven-plugin.
- camel-restdsl-openapi:generate-yaml - To generate consumer REST DSL YAML source code from OpenApi specification
- camel-restdsl-openapi:generate-yaml-with-dto - To generate consumer REST DSL YAML source code from OpenApi specification and with DTO model classes generated via the swagger-codegen-maven-plugin.

#### [3.9.1. Adding plugin to Maven pom.xml Copy link](#adding_plugin_to_maven_pom_xml)

This plugin can be added to your Maven `pom.xml` file by adding it to the `plugins` section, for example in a Spring Boot application:

```
<build>
  <plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>

    <plugin>
      <groupId>org.apache.camel</groupId>
      <artifactId>camel-restdsl-openapi-plugin</artifactId>
      <version>{CamelCommunityVersion}</version>
    </plugin>

  </plugins>
</build>
```

Copy to Clipboard

Toggle word wrap

The plugin can then be executed using its prefix `camel-restdsl-openapi` as shown below.

```
$mvn camel-restdsl-openapi:generate
```

Copy to Clipboard

Toggle word wrap

#### [3.9.2. camel-restdsl-openapi:generate Copy link](#camel_restdsl_openapigenerate)

The goal of the Camel REST DSL OpenApi Maven Plugin is used to generate REST DSL RouteBuilder implementation source code from Maven.

##### [3.9.2.1. Options Copy link](#options)

The plugin supports the following options which can be configured from the command line (use `-D` syntax), or defined in the `pom.xml` file in the `configuration` tag.

Expand

| Parameter                       | Default Value                                      | Description                                                                                                                                                                                                                                            |
|---------------------------------|----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` skip ```                    | ``` false ```                                      | Set to  ``` true ```  to skip code generation                                                                                                                                                                                                          |
| ``` filterOperation ```         |                                                    | Used for including only the operation ids specified. Multiple ids can be separated by comma. Wildcards can be used, eg  ``` find* ```  to include all operations starting with  ``` find ```  .                                                        |
| ``` specificationUri ```        | ``` src/spec/openapi.json ```                      | URI of the OpenApi specification, supports filesystem paths, HTTP and classpath resources, by default  ``` src/spec/openapi.json ```  within the project directory. Supports JSON and YAML.                                                            |
| ``` auth ```                    |                                                    | Adds authorization headers when fetching the OpenApi specification definitions remotely. Pass in a URL-encoded string of name:header with a comma separating multiple values.                                                                          |
| ``` className ```               | from  ``` title ```  or  ``` RestDslRoute ```      | Name of the generated class, taken from the OpenApi specification title or set to  ``` RestDslRoute ```  by default                                                                                                                                    |
| ``` packageName ```             | from  ``` host ```  or  ``` rest.dsl.generated ``` | Name of the package for the generated class, taken from the OpenApi specification host value or  ``` rest.dsl.generated ```  by default                                                                                                                |
| ``` indent ```                  | ``` " " ```                                        | Which indenting character(s) to use, by default four spaces, you can use  ``` \t ```  to signify tab character                                                                                                                                         |
| ``` outputDirectory ```         | ``` generated-sources/restdsl-openapi ```          | Where to place the generated source file, by default  ``` generated-sources/restdsl-openapi ```  within the project directory                                                                                                                          |
| ``` destinationGenerator ```    |                                                    | Fully qualified class name of the class that implements  ``` org.apache.camel.generator.openapi.DestinationGenerator ```  interface for customizing destination endpoint                                                                               |
| ``` destinationToSyntax ```     | ``` direct:${operationId} ```                      | The default to syntax for the to uri, which is to use the direct component.                                                                                                                                                                            |
| ``` restConfiguration ```       | ``` true ```                                       | Whether to include generation of the rest configuration with detected rest component to be used.                                                                                                                                                       |
| ``` apiContextPath ```          |                                                    | Define openapi endpoint path if  ``` restConfiguration ```  is set to true.                                                                                                                                                                            |
| ``` clientRequestValidation ``` | ``` false ```                                      | Whether to enable request validation.                                                                                                                                                                                                                  |
| ``` basePath ```                |                                                    | Overrides the api base path as defined in the OpenAPI specification.                                                                                                                                                                                   |
| ``` requestMappingValues ```    | ``` /** ```                                        | Allows generation of custom  **RequestMapping**  mapping values. Multiple mapping values can be passed as:  ``` <requestMappingValues> <param>/my-api-path/ ```  **``` </param> <param>/my-other-path/ ```**  ``` </param> </requestMappingValues> ``` |

Show more

#### [3.9.3. Spring Boot Project with Servlet component Copy link](#spring_boot_project_with_servlet_component)

If the Maven project is a Spring Boot project and `restConfiguration` is enabled and the servlet component is being used as REST component, then this plugin will autodetect the package name (if packageName has not been explicitly configured) where the `@SpringBootApplication` main class is located, and use the same package name for generating Rest DSL source code and a needed `CamelRestController` support class.

#### [3.9.4. camel-restdsl-openapi:generate-with-dto Copy link](#camel_restdsl_openapigenerate_with_dto)

Works as `generate` goal but also generates DTO model classes by automatic executing the swagger-codegen-maven-plugin to generate java source code of the DTO model classes from the OpenApi specification.

This plugin has been scoped and limited to only support a good effort set of defaults for using the swagger-codegen-maven-plugin to generate the model DTOs. If you need more power and flexibility then use the [Swagger Codegen Maven Plugin](https://github.com/swagger-api/swagger-codegen/tree/3.0.0/modules/swagger-codegen-maven-plugin) directly to generate the DTO and not this plugin.

The DTO classes may require additional dependencies such as:

```
<dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.10.1</version>
    </dependency>
    <dependency>
      <groupId>io.swagger.core.v3</groupId>
      <artifactId>swagger-core</artifactId>
      <version>2.2.8</version>
    </dependency>
    <dependency>
      <groupId>org.threeten</groupId>
      <artifactId>threetenbp</artifactId>
      <version>1.6.8</version>
    </dependency>
```

Copy to Clipboard

Toggle word wrap

##### [3.9.4.1. Options Copy link](#options_2)

The plugin supports the following **additional** options

Expand

| Parameter                                | Default Value                   | Description                                                                                                          |
|------------------------------------------|---------------------------------|----------------------------------------------------------------------------------------------------------------------|
| ``` swaggerCodegenMavenPluginVersion ``` | 3.0.36                          | The version of the  ``` io.swagger.codegen.v3:swagger-codegen-maven-plugin ```  maven plugin to be used.             |
| ``` modelOutput ```                      |                                 | Target output path (default is ${project.build.directory}/generated-sources/openapi)                                 |
| ``` modelPackage ```                     | ``` io.swagger.client.model ``` | The package to use for generated model objects/classes                                                               |
| ``` modelNamePrefix ```                  |                                 | Sets the pre- or suffix for model classes and enums                                                                  |
| ``` modelNameSuffix ```                  |                                 | Sets the pre- or suffix for model classes and enums                                                                  |
| ``` modelWithXml ```                     | false                           | Enable XML annotations inside the generated models (only works with libraries that provide support for JSON and XML) |
| ``` configOptions ```                    |                                 | Pass a map of language-specific parameters to  ``` swagger-codegen-maven-plugin ```                                  |

Show more

#### [3.9.5. camel-restdsl-openapi:generate-xml Copy link](#camel_restdsl_openapigenerate_xml)

The `camel-restdsl-openapi:generate-xml` goal of the Camel REST DSL OpenApi Maven Plugin is used to generate REST DSL XML implementation source code from Maven.

##### [3.9.5.1. Options Copy link](#options_3)

The plugin supports the following options which can be configured from the command line (use `-D` syntax), or defined in the `pom.xml` file in the `<configuration>` tag.

Expand

| Parameter                                                                                        | Default Value                             | Description                                                                                                                                                                                     |
|--------------------------------------------------------------------------------------------------|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` skip ```                                                                                     | ``` false ```                             | Set to  ``` true ```  to skip code generation.                                                                                                                                                  |
| ``` filterOperation ```                                                                          |                                           | Used for including only the operation ids specified. Multiple ids can be separated by comma. Wildcards can be used, eg  ``` find* ```  to include all operations starting with  ``` find ```  . |
| ``` specificationUri ```                                                                         | ``` src/spec/openapi.json ```             | URI of the OpenApi specification, supports filesystem paths, HTTP and classpath resources, by default  ``` src/spec/openapi.json ```  within the project directory. Supports JSON and YAML.     |
| ``` auth ```                                                                                     |                                           | Adds authorization headers when fetching the OpenApi specification definitions remotely. Pass in a URL-encoded string of name:header with a comma separating multiple values.                   |
| ``` outputDirectory ```                                                                          | ``` generated-sources/restdsl-openapi ``` | Where to place the generated source file, by default  ``` generated-sources/restdsl-openapi ```  within the project directory                                                                   |
| ``` fileName ```                                                                                 | ``` camel-rest.xml ```                    | The name of the XML file as output.                                                                                                                                                             |
| ``` blueprint ```                                                                                | ``` false ```                             | If enabled generates OSGi Blueprint XML instead of Spring XML.                                                                                                                                  |
| ``` destinationGenerator ```                                                                     |                                           | Fully qualified class name of the class that implements  ``` org.apache.camel.generator.openapi.DestinationGenerator ```  interface for customizing destination endpoint                        |
| ``` destinationToSyntax ```                                                                      | ``` direct:${operationId} ```             | The default to syntax for the to uri, which is to use the direct component.                                                                                                                     |
|                                                                                                  | ``` restConfiguration ```                 | ``` true ```                                                                                                                                                                                    |
| Whether to include generation of the rest configuration with detected rest component to be used. | ``` apiContextPath ```                    |                                                                                                                                                                                                 |
| Define openapi endpoint path if  ``` restConfiguration ```  is set to  ``` true ```  .           | ``` clientRequestValidation ```           | ``` false ```                                                                                                                                                                                   |
| Whether to enable request validation.                                                            | ``` basePath ```                          |                                                                                                                                                                                                 |
| Overrides the api base path as defined in the OpenAPI specification.                             | ``` requestMappingValues ```              | ``` /** ```                                                                                                                                                                                     |

Show more

#### [3.9.6. camel-restdsl-openapi:generate-xml-with-dto Copy link](#camel_restdsl_openapigenerate_xml_with_dto)

Works as `generate-xml` goal but also generates DTO model classes by automatic executing the swagger-codegen-maven-plugin to generate java source code of the DTO model classes from the OpenApi specification.

This plugin has been scoped and limited to only support a good effort set of defaults for using the swagger-codegen-maven-plugin to generate the model DTOs. If you need more power and flexibility then use the [Swagger Codegen Maven Plugin](https://github.com/swagger-api/swagger-codegen/tree/master/modules/swagger-codegen-maven-plugin) directly to generate the DTO and not this plugin.

The DTO classes may require additional dependencies such as:

```
<dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.10.1</version>
    </dependency>
    <dependency>
      <groupId>io.swagger.core.v3</groupId>
      <artifactId>swagger-core</artifactId>
      <version>2.2.8</version>
    </dependency>
    <dependency>
      <groupId>org.threeten</groupId>
      <artifactId>threetenbp</artifactId>
      <version>1.6.8</version>
    </dependency>
```

Copy to Clipboard

Toggle word wrap

##### [3.9.6.1. Options Copy link](#options_4)

The plugin supports the following **additional** options

Expand

| Parameter                                | Default Value                   | Description                                                                                                          |
|------------------------------------------|---------------------------------|----------------------------------------------------------------------------------------------------------------------|
| ``` swaggerCodegenMavenPluginVersion ``` | 3.0.36                          | The version of the  ``` io.swagger.codegen.v3:swagger-codegen-maven-plugin ```  maven plugin to be used.             |
| ``` modelOutput ```                      |                                 | Target output path (default is ${project.build.directory}/generated-sources/openapi)                                 |
| ``` modelPackage ```                     | ``` io.swagger.client.model ``` | The package to use for generated model objects/classes                                                               |
| ``` modelNamePrefix ```                  |                                 | Sets the pre- or suffix for model classes and enums                                                                  |
| ``` modelNameSuffix ```                  |                                 | Sets the pre- or suffix for model classes and enums                                                                  |
| ``` modelWithXml ```                     | false                           | Enable XML annotations inside the generated models (only works with libraries that provide support for JSON and XML) |
| ``` configOptions ```                    |                                 | Pass a map of language-specific parameters to  ``` swagger-codegen-maven-plugin ```                                  |

Show more

#### [3.9.7. camel-restdsl-openapi:generate-yaml Copy link](#camel_restdsl_openapigenerate_yaml)

The `camel-restdsl-openapi:generate-yaml` goal of the Camel REST DSL OpenApi Maven Plugin is used to generate REST DSL YAML implementation source code from Maven.

##### [3.9.7.1. Options Copy link](#options_5)

The plugin supports the following options which can be configured from the command line (use `-D` syntax), or defined in the `pom.xml` file in the `<configuration>` tag.

Expand

| Parameter                                                                                        | Default Value                             | Description                                                                                                                                                                                     |
|--------------------------------------------------------------------------------------------------|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` skip ```                                                                                     | ``` false ```                             | Set to  ``` true ```  to skip code generation.                                                                                                                                                  |
| ``` filterOperation ```                                                                          |                                           | Used for including only the operation ids specified. Multiple ids can be separated by comma. Wildcards can be used, eg  ``` find* ```  to include all operations starting with  ``` find ```  . |
| ``` specificationUri ```                                                                         | ``` src/spec/openapi.json ```             | URI of the OpenApi specification, supports filesystem paths, HTTP and classpath resources, by default  ``` src/spec/openapi.json ```  within the project directory. Supports JSON and YAML.     |
| ``` auth ```                                                                                     |                                           | Adds authorization headers when fetching the OpenApi specification definitions remotely. Pass in a URL-encoded string of name:header with a comma separating multiple values.                   |
| ``` outputDirectory ```                                                                          | ``` generated-sources/restdsl-openapi ``` | Where to place the generated source file, by default  ``` generated-sources/restdsl-openapi ```  within the project directory                                                                   |
| ``` fileName ```                                                                                 | ``` camel-rest.xml ```                    | The name of the XML file as output.                                                                                                                                                             |
| ``` destinationGenerator ```                                                                     |                                           | Fully qualified class name of the class that implements  ``` org.apache.camel.generator.openapi.DestinationGenerator ```  interface for customizing destination endpoint                        |
| ``` destinationToSyntax ```                                                                      | ``` direct:${operationId} ```             | The default to syntax for the to uri, which is to use the direct component.                                                                                                                     |
|                                                                                                  | ``` restConfiguration ```                 | ``` true ```                                                                                                                                                                                    |
| Whether to include generation of the rest configuration with detected rest component to be used. | ``` apiContextPath ```                    |                                                                                                                                                                                                 |
| Define openapi endpoint path if  ``` restConfiguration ```  is set to  ``` true ```  .           | ``` clientRequestValidation ```           | ``` false ```                                                                                                                                                                                   |
| Whether to enable request validation.                                                            | ``` basePath ```                          |                                                                                                                                                                                                 |
| Overrides the api base path as defined in the OpenAPI specification.                             | ``` requestMappingValues ```              | ``` /** ```                                                                                                                                                                                     |

Show more

#### [3.9.8. camel-restdsl-openapi:generate-yaml-with-dto Copy link](#camel_restdsl_openapigenerate_yaml_with_dto)

Works as `generate-yaml` goal but also generates DTO model classes by automatic executing the swagger-codegen-maven-plugin to generate java source code of the DTO model classes from the OpenApi specification.

This plugin has been scoped and limited to only support a good effort set of defaults for using the `swagger-codegen-maven-plugin` to generate the model DTOs. If you need more power and flexibility then use the [Swagger Codegen Maven Plugin](https://github.com/swagger-api/swagger-codegen/tree/master/modules/swagger-codegen-maven-plugin) directly to generate the DTO and not this plugin.

The DTO classes may require additional dependencies such as:

```
<dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.10.1</version>
    </dependency>
    <dependency>
      <groupId>io.swagger.core.v3</groupId>
      <artifactId>swagger-core</artifactId>
      <version>2.2.8</version>
    </dependency>
    <dependency>
      <groupId>org.threeten</groupId>
      <artifactId>threetenbp</artifactId>
      <version>1.6.8</version>
    </dependency>
```

Copy to Clipboard

Toggle word wrap

##### [3.9.8.1. Options Copy link](#options_6)

The plugin supports the following **additional** options

Expand

| Parameter                                | Default Value                   | Description                                                                                                          |
|------------------------------------------|---------------------------------|----------------------------------------------------------------------------------------------------------------------|
| ``` swaggerCodegenMavenPluginVersion ``` | 3.0.36                          | The version of the  ``` io.swagger.codegen.v3:swagger-codegen-maven-plugin ```  maven plugin to be used.             |
| ``` modelOutput ```                      |                                 | Target output path (default is ${project.build.directory}/generated-sources/openapi)                                 |
| ``` modelPackage ```                     | ``` io.swagger.client.model ``` | The package to use for generated model objects/classes                                                               |
| ``` modelNamePrefix ```                  |                                 | Sets the pre- or suffix for model classes and enums                                                                  |
| ``` modelNameSuffix ```                  |                                 | Sets the pre- or suffix for model classes and enums                                                                  |
| ``` modelWithXml ```                     | false                           | Enable XML annotations inside the generated models (only works with libraries that provide support for JSON and XML) |
| ``` configOptions ```                    |                                 | Pass a map of language-specific parameters to  ``` swagger-codegen-maven-plugin ```                                  |

Show more

### [3.10. Support for FIPS Compliance Copy link](#support-for-FIPS-cryptography)

You can install an OpenShift Container Platform cluster that uses FIPS Validated / Modules in Process cryptographic libraries on the x86\_64 architecture.

For the Red Hat Enterprise Linux CoreOS (RHCOS) machines in your cluster, this change applies when the machines deploy based on the status of an option in the install-config.yaml file, which governs the cluster options that users can change during cluster deployment. With Red Hat Enterprise Linux (RHEL) machines, you must enable FIPS mode when installing the operating system on the machines you plan to use as worker machines. These configuration methods ensure that your cluster meets the requirements of a FIPS compliance audit. Only FIPS Validated / Modules in Process cryptography packages are enabled before the initial system boot.

Because you must enable FIPS before your cluster's operating system boots for the first time, you cannot enable FIPS after you deploy a cluster.

#### [3.10.1. FIPS validation in OpenShift Container Platform Copy link](#fips_validation_in_openshift_container_platform)

OpenShift Container Platform uses certain FIPS Validated / Modules in Process modules within RHEL and RHCOS for its operating system components. For example, when users SSH into OpenShift Container Platform clusters and containers, those connections are properly encrypted.

OpenShift Container Platform components are written in Go and built with Red Hat's Golang compiler. When you enable FIPS mode for your cluster, all OpenShift Container Platform components that require cryptographic signing call RHEL and RHCOS cryptographic libraries.

For more details about FIPS, see [FIPS mode attributes and limitations](https://docs.redhat.com/en/documentation/openshift_container_platform/4.17/html/installation_overview/installing-fips)

For details on deploying Camel Spring Boot on OpenShift, see [How to deploy a Camel Spring Boot application to OpenShift?](https://access.redhat.com/solutions/6978927)

Details about supported configurations can be found at, [Camel for Spring Boot Supported Configurations](https://access.redhat.com/articles/6970899)

## [Chapter 4. Setting up Maven locally Copy link](#set-up-maven-locally)

Maven is the typical choice for Red Hat build of Apache Camel application development and project management.

### [4.1. Preparing to set up Maven Copy link](#prepare-to-set-up-maven)

Maven is a free, open source, build tool from Apache.

**Procedure**

1. Download Maven 3.8.6 or later from the [Maven download page](http://maven.apache.org/download.html) . Tip To verify that you have the correct Maven and JDK version installed, open a command terminal and enter the following command: `mvn --version` Copy to Clipboard Toggle word wrap Check the output to verify that Maven is version 3.8.6 or newer, and is using OpenJDK 17.
2. Ensure that your system is connected to the Internet. While building a project, the default behavior is that Maven searches external repositories and downloads the required artifacts. Maven looks for repositories that are accessible over the Internet. You can change this behavior so that Maven searches only repositories that are on a local network. That is, Maven can run in an offline mode. In offline mode, Maven looks for artifacts in its local repository. See [Section 4.4, "Using local Maven repositories"](#use-local-maven-repositories) .

### [4.2. Adding Red Hat repositories to Maven Copy link](#add-red-hat-repositories-to-maven)

To access artifacts that are in Red Hat Maven repositories, you need to add those repositories to Maven's `settings.xml` file.

Maven looks for the `settings.xml` file in the `.m2` directory of the user's home directory. If there is not a user specified `settings.xml` file, Maven uses the system-level `settings.xml` file at `M2_HOME/conf/settings.xml` .

**Prerequisite**

You know the location of the `settings.xml` file in which you want to add the Red Hat repositories.

**Procedure**

- In the `settings.xml` file, add `repository` elements for the Red Hat repositories as shown in this example:

Note

If you are using the `camel-jira` component, also add the atlassian repository.

Note

If you want to use technology preview builds, also add the `earlyaccess` repository.

```
<?xml version="1.0"?>
<settings>

  <profiles>
    <profile>
      <id>extra-repos</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <repositories>
       <repository>
            <id>redhat-ga-repository</id>
            <url>https://maven.repository.redhat.com/ga</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
        <repository>
            <id>redhat-ea-repository</id>
            <url>https://maven.repository.redhat.com/earlyaccess/all</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
        <repository>
            <id>atlassian</id>
            <url>https://packages.atlassian.com/artifactory/maven-public/</url>
            <name>atlassian external repo</name>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
            <releases>
                <enabled>true</enabled>
            </releases>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
            <id>redhat-ga-repository</id>
            <url>https://maven.repository.redhat.com/ga</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </pluginRepository>
        <pluginRepository>
            <id>redhat-ea-repository</id>
            <url>https://maven.repository.redhat.com/earlyaccess/all</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>

  <activeProfiles>
    <activeProfile>extra-repos</activeProfile>
  </activeProfiles>

</settings>
```

Copy to Clipboard

Toggle word wrap

### [4.3. Building an offline Maven repository Copy link](#building-offline-maven-repository)

Red Hat build of Apache Camel for Spring Boot users can build their own offline Maven repository which is used in a restricted environment. For each release of Red Hat build of Apache Camel for Spring Boot users can download the zip file from the Red Hat Customer Portal.

**Procedure**

1. Download the offile Maven repository builder from the customer portal. For example, for Red Hat build of Camel Spring Boot version 4.14, use the [Offline Maven builder](https://access.redhat.com/jbossnetwork/restricted/softwareDownload.html?softwareId=108667) .
2. The downloaded file is a zip file that contains everything to build an offline Maven repository for this specific release.
3. Unzip the downloaded zip file. The directory structure of the archive is as follows: `├── build-offline-repo.sh ├── logback.xml ├── maven-repositories.txt ├── offliner-2.2.jar ├── offliner-2.2.jar.md5 ├── offliner-2.2-sources.jar ├── offliner-2.2-sources.jar.md5 ├── README ├── rhaf-camel-offliner-4.14.2.txt └── rhaf-camel-spring-boot-offliner-4.14.2.txt` Copy to Clipboard Toggle word wrap This zip contains the following files:
4. To build an offline repository, run the `build-offline-repo.sh` script as per instructions given in the `README` file. Optionally you can specify a directory where the artifacts should be downloaded to. If not specified, a directory called 'repository' is created in the current working directory.

If needed, you can configure the tool to use additional Maven repositories, by adding them to file `maven-repositories.txt` . This is generally not necessary as the tool is pre-configured with the right set of Maven repositories.

In case of a HTTP proxy and any HTTP calls that need to go via this proxy, you may need to change the script. Add the arguments `--proxy <proxy-host> --proxy-user <proxy-user> --proxy-pass <proxy-pass>` in the line that invokes the JVM in the script.

You can use the option `-v` to print the version number of the script. This version is the version number of the script and not related to the Red Hat build of Apache Camel product version.

**Troubleshooting**

You can configure the logging via the provided `logback.xml` file. When the shell script is executed, any download activity will be written to the log file `offliner.log` and any download failures are listed in `errors.log` . At the end of the execution the offliner tool displays a summary of the downloaded and failed artifacts, but we also recommend to scan through `errors.log` for any download failures.

If any artifacts are failed to be downloaded, re-run the tool against the same target folder. The tool will avoid to download artifacts that it already downloaded and only attempt those that it failed on previously.

### [4.4. Using local Maven repositories Copy link](#use-local-maven-repositories)

If you are running a container without an Internet connection, and you need to deploy an application that has dependencies that are not available offline, you can use the Maven dependency plug-in to download the application's dependencies into a Maven offline repository. You can then distribute this customized Maven offline repository to machines that do not have an Internet connection.

**Procedure**

1. In the project directory that contains the `pom.xml` file, download a repository for a Maven project by running a command such as the following: `mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.0:go-offline -Dmaven.repo.local=/tmp/my-project` Copy to Clipboard Toggle word wrap In this example, Maven dependencies and plug-ins that are required to build the project are downloaded to the `/tmp/my-project` directory.
2. Distribute this customized Maven offline repository internally to any machines that do not have an Internet connection.

### [4.5. Setting Maven mirror using environmental variables or system properties Copy link](#set-maven-mirror-url)

When running the applications you need access to the artifacts that are in the Red Hat Maven repositories. These repositories are added to Maven's `settings.xml` file. Maven checks the following locations for `settings.xml` file:

- looks for the specified url
- if not found looks for `${user.home}/.m2/settings.xml`
- if not found looks for `${maven.home}/conf/settings.xml`
- if not found looks for `${M2_HOME}/conf/settings.xml`
- if no location is found, empty `org.apache.maven.settings.Settings` instance is created.

#### [4.5.1. About Maven mirror Copy link](#maven-mirror)

Maven uses a set of remote repositories to access the artifacts, which are currently not available in local repository. The list of repositories almost always contains Maven Central repository, but for Red Hat Fuse, it also contains Maven Red Hat repositories. In some cases where it is not possible or allowed to access different remote repositories, you can use a mechanism of Maven mirrors. A mirror replaces a particular repository URL with a different one, so all HTTP traffic when remote artifacts are being searched for can be directed to a single URL.

#### [4.5.2. Adding Maven mirror to settings.xml Copy link](#add-maven-mirror-url-settings-xml)

To set the Maven mirror, add the following section to Maven's `settings.xml` :

```
<mirror>
      <id>all</id>
      <mirrorOf>*</mirrorOf>
      <url>http://host:port/path</url>
</mirror>
```

Copy to Clipboard

Toggle word wrap

No mirror is used if the above section is not found in the `settings.xml` file. To specify a global mirror without providing the XML configuration, you can use either system property or environmental variables.

#### [4.5.3. Setting Maven mirror using environmental variable or system property Copy link](#set-maven-mirror-url-using-env-variables)

To set the Maven mirror using either environmental variable or system property, you can add:

- Environmental variable called **MAVEN\_MIRROR\_URL** to `bin/setenv` file
- System property called **mavenMirrorUrl** to `etc/system.properties` file

#### [4.5.4. Using Maven options to specify Maven mirror url Copy link](#set-maven-mirror-url-using-maven-options)

To use an alternate Maven mirror url, other than the one specified by environmental variables or system property, use the following maven options when running the application:

- `-DmavenMirrorUrl=mirrorId::mirrorUrl` for example, `-DmavenMirrorUrl=my-mirror::http://mirror.net/repository`
- `-DmavenMirrorUrl=mirrorUrl` for example, `-DmavenMirrorUrl=http://mirror.net/repository` . In this example, the &lt;id&gt; of the &lt;mirror&gt; is just a mirror.

### [4.6. About Maven artifacts and coordinates Copy link](#about-maven-coordinates)

In the Maven build system, the basic building block is an *artifact* . After a build, the output of an artifact is typically an archive, such as a JAR or WAR file.

A key aspect of Maven is the ability to locate artifacts and manage the dependencies between them. A *Maven coordinate* is a set of values that identifies the location of a particular artifact. A basic coordinate has three values in the following form:

```
groupId:artifactId:version
```

Sometimes Maven augments a basic coordinate with a *packaging* value or with both a *packaging* value and a *classifier* value. A Maven coordinate can have any one of the following forms:

```
groupId:artifactId:version
groupId:artifactId:packaging:version
groupId:artifactId:packaging:classifier:version
```

Copy to Clipboard

Toggle word wrap

Here are descriptions of the values:

*groupdId* Defines a scope for the name of the artifact. You would typically use all or part of a package name as a group ID. For example, `org.fusesource.example` . *artifactId* Defines the artifact name relative to the group ID. *version* Specifies the artifact's version. A version number can have up to four parts: `n.n.n.n` , where the last part of the version number can contain non-numeric characters. For example, the last part of `1.0-SNAPSHOT` is the alphanumeric substring, `0-SNAPSHOT` . *packaging* Defines the packaged entity that is produced when you build the project. For OSGi projects, the packaging is `bundle` . The default value is `jar` . *classifier* Enables you to distinguish between artifacts that were built from the same POM, but have different content.

Elements in an artifact's POM file define the artifact's group ID, artifact ID, packaging, and version, as shown here:

```
<project ... >
  ...
  <groupId>org.fusesource.example</groupId>
  <artifactId>bundle-demo</artifactId>
  <packaging>bundle</packaging>
  <version>1.0-SNAPSHOT</version>
  ...
</project>
```

Copy to Clipboard

Toggle word wrap

To define a dependency on the preceding artifact, you would add the following `dependency` element to a POM file:

```
<project ... >
  ...
  <dependencies>
    <dependency>
      <groupId>org.fusesource.example</groupId>
      <artifactId>bundle-demo</artifactId>
      <version>1.0-SNAPSHOT</version>
    </dependency>
  </dependencies>
  ...
</project>
```

Copy to Clipboard

Toggle word wrap

Note

It is not necessary to specify the `bundle` package type in the preceding dependency, because a bundle is just a particular kind of JAR file and `jar` is the default Maven package type. If you do need to specify the packaging type explicitly in a dependency, however, you can use the `type` element.

## [Chapter 5. Sample applications Copy link](#getting-started-with-camel-spring-boot-examples_csb)

### [5.1. Spring Boot Examples Copy link](#camel-spring-boot-examples)

The [Spring Boot examples](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/) repository contains a number of examples on how to integrate with Camel for a variety of use cases. They provide best practice advice and describe common patterns that we see in integration and messaging problems.

The examples can be run using Maven. When using the `mvn` command, Maven will attempt to download the required dependencies from a central repository to your local repository.

### [5.2. Examples repository Copy link](#camel-spring-boot-examples-list)

There are 64 examples:

Expand

| Example                                                                                                                                                                                                                                                                 | Category                   | Description                                                                                                                                                                  | Deploy with devfile   |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------|
| [Actuator HTTP Metrics](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/actuator-http-metrics/README.adoc)  (actuator-http-metrics)                                                                | Management and Monitoring  | Example on how to use Spring Boot's Actuator endpoints to gather info like mappings or metrics                                                                               | No                    |
| [Amqp](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/amqp/README.adoc)  (amqp)                                                                                                                   | Messaging                  | An example showing how to work with Camel, ActiveMQ Amqp and Spring Boot                                                                                                     | No                    |
| [AMQP Salesforce](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/amqp-salesforce/README.adoc)  (amqp-salesforce)                                                                                  | Messaging                  | AMQP message sending is created as contacts in Salesforce                                                                                                                    | No                    |
| [Amq Cert Manager](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/amq-cert-manager/README.adoc)  (amq-cert-manager)                                                                               | Messaging                  | An example showing how to work with Camel, ActiveMQ Amqp and Spring Boot                                                                                                     | No                    |
| [artemis](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/artemis/README.adoc)  (artemis)                                                                                                          | Messaging                  | An example showing how to work with Camel, ActiveMQ Artemis and Spring Boot                                                                                                  | No                    |
| [AWS2 S3](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/aws2-s3/README.adoc)  (aws2-s3)                                                                                                          | Cloud                      | An example showing the Camel AWS2 S3 component with Spring Boot                                                                                                              | No                    |
| [Azure Event Hubs](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/azure/camel-example-spring-boot-azure-eventhubs/README.adoc)  (azure-eventhubs)                                                 | Cloud                      | An example showing how to work with Camel, Azure Event Hubs and Spring Boot                                                                                                  | No                    |
| [Azure Service Bus](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/azure/camel-example-spring-boot-azure-servicebus/README.adoc)  (azure-servicebus)                                              | Cloud                      | An example showing how to work with Camel, Azure Service Bus and Spring Boot                                                                                                 | No                    |
| [Endpoint DSL](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/endpointdsl/README.adoc)  (camel-example-endpointdsl)                                                                               | Beginner                   | Using type-safe Endpoint DSL                                                                                                                                                 | No                    |
| [FHIR](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/fhir/README.adoc)  (fhir)                                                                                                                   | Health Care                | An example showing how to work with Camel, FHIR and Spring Boot                                                                                                              | No                    |
| [Transaction](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/fhir-auth-tx/README.adoc)  (fhir-auth-tx)                                                                                            | Health Care                | An example showing how to work with Camel, FHIR Authorization, FHIR Transaction and Spring Boot                                                                              | Yes                   |
| [Health Checks](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/health-checks/README.adoc)  (health-checks)                                                                                        | Health Care                | An example on how to work with health checks ib a simple Apache Camel application using Spring Boot.                                                                         | Yes                   |
| [HTTP SSL](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/http-ssl/README.adoc)  (http-ssl)                                                                                                       | Rest                       | An example showing the Camel HTTP component with Spring Boot and SSL                                                                                                         | No                    |
| [Http Streaming](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/http-streaming/README.adoc)  (http-streaming)                                                                                     | Rest                       | An example showing large data stream scenario using Camel Platform HTTP component                                                                                            | No                    |
| [Infinispan](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/infinispan/README.adoc)  (infinispan)                                                                                                 | Cloud                      | An example showing the Camel Infinispan component with Spring Boot                                                                                                           | No                    |
| [Jira](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/jira/README.adoc)  (jira)                                                                                                                   | Beginner                   | An example that uses Jira Camel API                                                                                                                                          | No                    |
| [Jolokia](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/jolokia/README.adoc)  (jolokia)                                                                                                          | Management and Monitoring  | An example that uses Jolokia to monitor and to manage Camel Routes                                                                                                           | No                    |
| [Avro](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/kafka-avro/README.adoc)  (kafka-avro)                                                                                                       | Messaging                  | An example for Kafka avro                                                                                                                                                    | No                    |
| [Kafka OAuth Ocp](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/kafka-oauth-ocp/README.adoc)  (kafka-oauth-ocp)                                                                                  | Messaging                  | An example for Kafka on OCP using integrated OAuth                                                                                                                           | No                    |
| [offsetrepository](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/kafka-offsetrepository/README.adoc)  (kafka-offsetrepository)                                                                   | Messaging                  | An example for Kafka offsetrepository                                                                                                                                        | No                    |
| [Kamelet Chuck Norris](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/kamelet-chucknorris/README.adoc)  (kamelet-chucknorris)                                                                     | Beginner                   | How easy it is to create your own Kamelets                                                                                                                                   | Yes                   |
| [Custom Type Converter](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/load-balancer-eip/README.adoc)  (load-balancer-eip)                                                                        | Beginner                   | An example showing Load Balancer EIP with Camel and Spring Boot                                                                                                              | Yes                   |
| [Microsoft Exchange Oauth2 Authentication](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/mail-ms-exchange-oauth2/README.adoc)  (mail-exchange-oauth2)                                            | Mail                       | An example showing how to use Camel on Spring Boot to connect with IMAP protocol and access email data for Office 365 users using OAuth2 authentication                      | No                    |
| [Master](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/master/README.adoc)  (master)                                                                                                             | Clustering                 | An example showing how to work with Camel's Master component and Spring Boot                                                                                                 | No                    |
| [Monitoring Micrometrics Grafana Prometheus](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/monitoring-micrometrics-grafana-prometheus/README.adoc)  (monitoring-micrometrics-grafana-prometheus) | Management and Monitoring  | Example on how to use Spring Boot's Actuator endpoints to gather info like mappings or metrics                                                                               | No                    |
| [Multiple pooled datasources with two-phase commit](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/multi-datasource-2pc/README.adoc)  (muti-datasources-2pc)                                      | Database                   | An example showing how to work with Camel and Spring Boot using multiple pooled datasources with two-phase commit                                                            | No                    |
| [Observability Services](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/observability-services/README.adoc)  (observability-services)                                                             | Management and Monitoring  | An example showing how to use Camel with Observability Services                                                                                                              | No                    |
| [OpenAPI Contract First](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/openapi-contract-first/README.adoc)  (openapi-contract-first)                                                             | Rest                       | Contract First OpenAPI example                                                                                                                                               | No                    |
| [OpenTelemetry](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/opentelemetry/README.adoc)  (opentelemetry)                                                                                        | Management and Monitoring  | An example showing how to use Camel with OpenTelemetry                                                                                                                       | No                    |
| [Paho MQTT5 Shared Subscriptions](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/paho-mqtt5-shared-subscriptions/README.adoc)  (paho-mqtt5-shared-subscriptions)                                  | Messaging                  | An example showing how to set up multiple mqtt5 consumers that use shared subscription feature of MQTT5                                                                      | Yes                   |
| [REST DSL and Platform HTTP](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/platform-http/README.adoc)  (platform-http)                                                                           | Rest                       | An example showing Camel REST DSL with platform HTTP                                                                                                                         | No                    |
| [Platform-http Proxy](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1//README.adoc)  (platform-http-proxy)                                                                                         | EIP                        | An example with Camel Platform HTTP act as reverse proxy                                                                                                                     | No                    |
| [POJO Routing](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/pojo/README.adoc)  (pojo)                                                                                                           | Beginner                   | An example showing how to work with Camel POJO routing with Spring Boot                                                                                                      | Yes                   |
| [Quartz](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/quartz/README.adoc)  (quartz)                                                                                                             | Beginner                   | An example showing how to work with Camel Quartz and Camel Log with Spring Boot                                                                                              | Yes                   |
| [RabbitMQ](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/rabbitmq/README.adoc)  (rabbitmq)                                                                                                       | Messaging                  | An example showing how to work with Camel and RabbitMQ                                                                                                                       | No                    |
| [Resilience4j](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/resilience4j/README.adoc)  (resilience4j)                                                                                           | EIP                        | An example showing how to use Resilience4j EIP as circuit breaker in Camel routes                                                                                            | No                    |
| [REST using CXF and OpenTelemetry](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/rest-cxf-opentelemetry/README.adoc)  (rest-cxf-opentelemetry)                                                   | CXF                        | An example showing Camel REST using CXF and OpenTelemetry with Spring Boot                                                                                                   | No                    |
| [REST DSL and OpenApi](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/rest-openapi/README.adoc)  (rest-openapi)                                                                                   | Rest                       | An example showing Camel REST DSL and OpenApi with Spring Boot                                                                                                               | Yes                   |
| [OpenApi Simple](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/rest-openapi-simple/README.adoc)  (rest-openapi-simple)                                                                           | Beginner                   | This example shows how to call a Rest service defined using OpenApi specification                                                                                            | No                    |
| [REST DSL and OpenApi](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/rest-openapi-springdoc/README.adoc)  (rest-openapi-springdoc)                                                               | Rest                       | An example showing Camel REST DSL and OpenApi with a Springdoc UI in a Spring Boot application                                                                               | Yes                   |
| [REST DSL and Spring Security](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/rest-spring-security/README.adoc)  (rest-spring-security)                                                           | Rest                       | An example showing Camel REST DSL secured with Spring Security and JWT token in a Spring Boot application                                                                    | Yes                   |
| [Route Reload](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/route-reload/README.adoc)  (route-reload)                                                                                           | Beginner                   | Live reload of routes if file is updated and saved                                                                                                                           | Yes                   |
| [Routes Configuration](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/routes-configuration/README.adoc)  (routes-configuration)                                                                   | Beginner                   | Example with global routes configuration for error handling                                                                                                                  | No                    |
| [Route Template](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/routetemplate/README.adoc)  (routetemplate)                                                                                       | Beginner                   | How to use route templates (parameterized routes)                                                                                                                            | Yes                   |
| [XML](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/routetemplate-xml/README.adoc)  (routetemplate-xml)                                                                                          | Beginner                   | How to use route templates (parameterized routes) in XML                                                                                                                     | No                    |
| [Saga](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/saga/README.adoc)  (saga)                                                                                                                   | EIP                        | This example shows how to work with a simple Apache Camel application using Spring Boot and Narayana LRA Coordinator to manage distributed actions implementing SAGA pattern | No                    |
| [Salesforce](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1//README.adoc)  (salesforce)                                                                                                           | SaaS                       | How to work with Salesforce contacts using REST endpoints and Streaming API                                                                                                  | No                    |
| [SOAP CXF](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/soap-cxf/README.adoc)  (soap-cxf)                                                                                                       | CXF                        | An example showing the Camel SOAP CXF                                                                                                                                        | No                    |
| [Camel Splitter EIP](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/splitter-eip/README.adoc)  (splitter-eip)                                                                                     | Beginner                   | An example showing Splitter EIP with Camel and Spring Boot                                                                                                                   | Yes                   |
| [Spring Boot](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/spring-boot/README.adoc)  (camel-example-spring-boot)                                                                                | Beginner                   | An example showing how to work with Camel and Spring Boot                                                                                                                    | No                    |
| [Spring Boot Cxf Jaxws](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1//README.adoc)  (spring-boot-cxf-jaxws)                                                                                     | CXF                        | Spring Boot example running a CXF JAXWS Endpoint                                                                                                                             | No                    |
| [Spring Boot Cxf Jaxws XML](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1//README.adoc)  (spring-boot-cxf-jaxws-xml)                                                                             | CXF                        | Spring Boot example running a CXF JAXWS XML Endpoint                                                                                                                         | No                    |
| [JTA](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/spring-boot-jta-jpa-autoconfigure/README.adoc)  (spring-boot-jta-jpa-autoconfigure)                                                          | Advanced                   | An example showing JTA with Spring Boot Autoconfiguration                                                                                                                    | No                    |
| [JTA](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/spring-boot-jta-jpa-xml/README.adoc)  (spring-boot-jta-jpa-xml)                                                                              | Advanced                   | An example showing JTA with Spring Boot using Spring XML configuration                                                                                                       | No                    |
| [Spring JDBC](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/spring-jdbc/README.adoc)  (spring-jdbc)                                                                                              | Beginner                   | Camel transacted routes integrating local Spring Transaction                                                                                                                 | No                    |
| [Strimzi](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/strimzi/README.adoc)  (strimzi)                                                                                                          | Messaging                  | Camel example which a route is defined in XML for Strimzi integration on Openshift/Kubernetes                                                                                | No                    |
| [Supervising Route Controller](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/supervising-route-controller/README.adoc)  (supervising-route-controller)                                           | Management and Monitoring  | An example showing how to work with Camel's Supervising Route Controller and Spring Boot                                                                                     | Yes                   |
| [Tomcat JDBC](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/tomcat-jdbc/README.adoc)  (camel-example-spring-boot)                                                                                | Beginner                   | An example showing how to deploy a Camel Spring Boot application in Tomcat using its JDBC Data Source                                                                        | No                    |
| [Custom Type Converter](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/type-converter/README.adoc)  (type-converter)                                                                              | Beginner                   | An example showing how to create custom type converter with Camel and Spring Boot                                                                                            | Yes                   |
| [Validator](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/validator/README.adoc)  (validator)                                                                                                    | Input/Output Type Contract | An example showing how to work with declarative validation and Spring Boot                                                                                                   | Yes                   |
| [Webhook](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/webhook/README.adoc)  (webhook)                                                                                                          | Advanced                   | Example on how to use the Camel Webhook component                                                                                                                            | No                    |
| [Widget Gadget](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/widget-gadget/README.adoc)  (widget-gadget)                                                                                        | Messaging                  | The widget and gadget example from EIP book, running on Spring Boot                                                                                                          | No                    |
| [XML](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/xml/README.adoc)  (xml)                                                                                                                      | Beginner                   | An example showing how to work with Camel routes in XML files and Spring Boot                                                                                                | Yes                   |
| [XML Import](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/xml-import/README.adoc)  (xml-import)                                                                                                 | Beginner                   | An example showing how to work with Spring XML files imported with embedded CamelContext                                                                                     | Yes                   |

Show more

### [5.3. Running Examples Copy link](#camel-spring-boot-examples-run)

You should always use the latest release for the examples.

To run the examples:

1. Check out the tag for the latest release (currently camel-spring-boot-examples-4.14.2.redhat-00001-patch-1): `$ git checkout tags/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1`
2. Install the root pom: `$ mvn install`
3. Check the README for the example you want to run for additional steps.

### [5.4. Deploying examples Copy link](#camel-spring-boot-examples-deploy)

You can deploy examples in OpenShift or dev-sandbox using devfiles. For more information about deploying the Camel Spring Boot applications on OpenShift Container Platform, see [Apache Camel on OCP Best practices](https://jboss-fuse.github.io/apache-camel-on-ocp-best-practices/) .

Note

Only some of the examples can be deployed with devfile. See the column "Deploy with devfile" in the examples table.

**Prerequisites**

1. If you haven't already, install [`odo`](https://odo.dev/docs/overview/installation) (We recommend version 2.x)

**Procedure**

1. Log in to your openshift or dev-sandbox and create a new project. Here $EXAMPLE is the name of the example you want to deploy: `$ oc new-project csbex-$EXAMPLE`
2. Create an odo component using the devfile.yaml `$ odo create csb-ubi8 --app $EXAMPLE`
3. To set the specific example you want to deploy as an env variable (SUB\_FOLDER): `$ odo config set --env SUB_FOLDER=$EXAMPLE`
4. Then push it to openshift cluster: `$ odo push`
5. Before you deploy an example, delete the `.odo` directory in your repository. This removes components related to any previous example.
6. If you have an internal repository, set the `MAVEN_MIRROR_URL` environment with your maven repo before pushing: `$ odo config set --env MAVEN_MIRROR_URL=https://my-maven-mirror/`

## [Chapter 6. Monitoring Camel Spring Boot integrations Copy link](#monitoring-csb-integrations)

This chapter explains how to monitor integrations on Red Hat build of Camel Spring Boot at runtime. You can use the Prometheus Operator that is already deployed as part of OpenShift Monitoring to monitor your own applications.

For more information about deploying the Camel Spring Boot applications on OpenShift Container Platform, see [Apache Camel on OCP Best practices](https://jboss-fuse.github.io/apache-camel-on-ocp-best-practices/) .

For information about the HawtIO Diagnostic Console, see the [HawtIO Diagnostic Console documentation](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html/hawtio_diagnostic_console_guide) .

### [6.1. Enabling user workload monitoring in OpenShift Copy link](#csb-enabling-user-workload-monitoring)

You can enable the monitoring for user-defined projects by setting the `enableUserWorkload: true` field in the cluster monitoring ConfigMap object.

Important

In OpenShift Container Platform 4.13 you must remove any custom Prometheus instances before enabling monitoring for user-defined projects.

**Prerequisites**

You must have access to the cluster as a user with the cluster-admin cluster role access to enable monitoring for user-defined projects in OpenShift Container Platform. Cluster administrators can then optionally grant users permission to configure the components that are responsible for monitoring user-defined projects.

- You have cluster admin access to the OpenShift cluster.
- You have installed the OpenShift CLI ( `oc` ).

Note

Every time you save configuration changes to the user-workload-monitoring-config ConfigMap object, the pods in the openshift-user-workload-monitoring project are redeployed. It can sometimes take a while for these components to redeploy. You can create and configure the ConfigMap object before you first enable monitoring for user-defined projects, to prevent having to redeploy the pods often.

**Procedure**

1. Login to OpenShift with administrator permissions. `oc login --user system:admin --token=my-token --server=https://my-cluster.example.com:6443` Copy to Clipboard Toggle word wrap
2. Edit the `cluster-monitoring-config` ConfigMap object. `$ oc -n openshift-monitoring edit configmap cluster-monitoring-config` Copy to Clipboard Toggle word wrap
3. Add `enableUserWorkload: true` in the data/config.yaml section. `apiVersion: v1 kind: ConfigMap metadata: name: cluster-monitoring-config namespace: openshift-monitoring data: config.yaml: | enableUserWorkload: true` Copy to Clipboard Toggle word wrap When it is set to true, the `enableUserWorkload` parameter enables monitoring for user-defined projects in a cluster.
4. Save the file to apply the changes. The monitoring for the user-defined projects is then enabled automatically. Note When the changes are saved to the `cluster-monitoring-config` ConfigMap object, the pods and other resources in the `openshift-monitoring` project might be redeployed. The running monitoring processes in that project might also be restarted.
5. Verify that the `prometheus-operator` , `prometheus-user-workload` and `thanos-ruler-user-workload` pods are running in the `openshift-user-workload-monitoring` project. `$ oc -n openshift-user-workload-monitoring get pod Example output NAME READY STATUS RESTARTS AGE prometheus-operator-6f7b748d5b-t7nbg 2/2 Running 0 3h prometheus-user-workload-0 4/4 Running 1 3h prometheus-user-workload-1 4/4 Running 1 3h thanos-ruler-user-workload-0 3/3 Running 0 3h thanos-ruler-user-workload-1 3/3 Running 0 3h` Copy to Clipboard Toggle word wrap

### [6.2. Monitoring a Camel Spring Boot application Copy link](#monitoring-csb-application)

After you enable the monitoring for your project, you can deploy and monitor the Camel Spring Boot application. This section uses the `monitoring-micrometrics-grafana-prometheus` example listed in the [Camel Spring Boot Examples](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.0.0.redhat-00001/monitoring-micrometrics-grafana-prometheus) .

**Procedure**

1. Add the openshift-maven-plugin to the `pom.xml` file of the `monitoring-micrometrics-grafana-prometheus` example. In the `pom.xml` , add an **openshift** profile to allow deployment to openshift through the openshift-maven-plugin.
2. Add the openshift-maven-plugin to the `pom.xml` file of the `monitoring-micrometrics-grafana-prometheus` example. In the `pom.xml` , add an **openshift** profile to allow deployment to openshift through the `openshift-maven-plugin` . `<profiles> <profile> <id>openshift</id> <build> <plugins> <plugin> <groupId>org.eclipse.jkube</groupId> <artifactId>openshift-maven-plugin</artifactId> <version>1.13.1</version> <executions> <execution> <goals> <goal>resource</goal> <goal>build</goal> </goals> </execution> </executions> </plugin> </plugins> </build> </profile> </profiles>` Copy to Clipboard Toggle word wrap
3. Add the Prometheus support. In order to add the Prometheus support to your Camel application, expose the Prometheus statistics on an actuator endpoint.
4. Add the following to the `<dependencies/>` section of your pom.xml to add some starter support to your application. `<dependency> <groupId>org.springframework</groupId> <artifactId>spring-context</artifactId> <version>6.1.8</version> </dependency> <dependency> <groupId>io.micrometer</groupId> <artifactId>micrometer-registry-prometheus</artifactId> <version>1.13.6</version> </dependency> <dependency> <groupId>org.jolokia</groupId> <artifactId>jolokia-server-core</artifactId> <version>2.3.0.redhat-00001</version> </dependency> <dependency> <groupId>io.prometheus.jmx</groupId> <artifactId>collector</artifactId> <version>1.0.1</version> </dependency>` Copy to Clipboard Toggle word wrap
5. Create the file `config/prometheus_exporter_config.yml` : `startDelaySecs : 5 ssl : false blacklistObjectNames : [ "java.lang:*" ] rules : # Context level - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>ExchangesCompleted' name : org.apache.camel.ExchangesCompleted help : Exchanges Completed type : COUNTER labels : context : $1 type : context - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>ExchangesFailed' name : org.apache.camel.ExchangesFailed help : Exchanges Failed type : COUNTER labels : context : $1 type : context - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>ExchangesInflight' name : org.apache.camel.ExchangesInflight help : Exchanges Inflight type : GAUGE labels : context : $1 type : context - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>ExchangesTotal' name : org.apache.camel.ExchangesTotal help : Exchanges Total type : COUNTER labels : context : $1 type : context - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>FailuresHandled' name : org.apache.camel.FailuresHandled help : Failures Handled labels : context : $1 type : context type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>ExternalRedeliveries' name : org.apache.camel.ExternalRedeliveries help : External Redeliveries labels : context : $1 type : context type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>MaxProcessingTime' name : org.apache.camel.MaxProcessingTime help : Maximum Processing Time labels : context : $1 type : context type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>MeanProcessingTime' name : org.apache.camel.MeanProcessingTime help : Mean Processing Time labels : context : $1 type : context type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>MinProcessingTime' name : org.apache.camel.MinProcessingTime help : Minimum Processing Time labels : context : $1 type : context type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>LastProcessingTime' name : org.apache.camel.LastProcessingTime help : Last Processing Time labels : context : $1 type : context type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>DeltaProcessingTime' name : org.apache.camel.DeltaProcessingTime help : Delta Processing Time labels : context : $1 type : context type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>Redeliveries' name : org.apache.camel.Redeliveries help : Redeliveries labels : context : $1 type : context type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=context, name=([^,]+)><>TotalProcessingTime' name : org.apache.camel.TotalProcessingTime help : Total Processing Time labels : context : $1 type : context type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=consumers, name=([^,]+)><>InflightExchanges' name : org.apache.camel.InflightExchanges help : Inflight Exchanges labels : context : $1 type : context type : GAUGE # Route level - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>ExchangesCompleted' name : org.apache.camel.ExchangesCompleted help : Exchanges Completed type : COUNTER labels : context : $1 route : $2 type : routes - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>ExchangesFailed' name : org.apache.camel.ExchangesFailed help : Exchanges Failed type : COUNTER labels : context : $1 route : $2 type : routes - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>ExchangesInflight' name : org.apache.camel.ExchangesInflight help : Exchanges Inflight type : GAUGE labels : context : $1 route : $2 type : routes - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>ExchangesTotal' name : org.apache.camel.ExchangesTotal help : Exchanges Total type : COUNTER labels : context : $1 route : $2 type : routes - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>FailuresHandled' name : org.apache.camel.FailuresHandled help : Failures Handled labels : context : $1 route : $2 type : routes type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>ExternalRedeliveries' name : org.apache.camel.ExternalRedeliveries help : External Redeliveries labels : context : $1 route : $2 type : routes type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>MaxProcessingTime' name : org.apache.camel.MaxProcessingTime help : Maximum Processing Time labels : context : $1 route : $2 type : routes type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>MeanProcessingTime' name : org.apache.camel.MeanProcessingTime help : Mean Processing Time labels : context : $1 route : $2 type : routes type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>MinProcessingTime' name : org.apache.camel.MinProcessingTime help : Minimum Processing Time labels : context : $1 route : $2 type : routes type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>LastProcessingTime' name : org.apache.camel.LastProcessingTime help : Last Processing Time labels : context : $1 route : $2 type : routes type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>DeltaProcessingTime' name : org.apache.camel.DeltaProcessingTime help : Delta Processing Time labels : context : $1 route : $2 type : routes type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>Redeliveries' name : org.apache.camel.Redeliveries help : Redeliveries labels : context : $1 route : $2 type : routes type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>TotalProcessingTime' name : org.apache.camel.TotalProcessingTime help : Total Processing Time labels : context : $1 route : $2 type : routes type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=routes, name=([^,]+)><>InflightExchanges' name : org.apache.camel.InflightExchanges help : Inflight Exchanges labels : context : $1 route : $2 type : routes type : GAUGE # Processor level - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>ExchangesCompleted' name : org.apache.camel.ExchangesCompleted help : Exchanges Completed type : COUNTER labels : context : $1 processor : $2 type : processors - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>ExchangesFailed' name : org.apache.camel.ExchangesFailed help : Exchanges Failed type : COUNTER labels : context : $1 processor : $2 type : processors - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>ExchangesInflight' name : org.apache.camel.ExchangesInflight help : Exchanges Inflight type : GAUGE labels : context : $1 processor : $2 type : processors - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>ExchangesTotal' name : org.apache.camel.ExchangesTotal help : Exchanges Total type : COUNTER labels : context : $1 processor : $2 type : processors - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>FailuresHandled' name : org.apache.camel.FailuresHandled help : Failures Handled labels : context : $1 processor : $2 type : processors type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>ExternalRedeliveries' name : org.apache.camel.ExternalRedeliveries help : External Redeliveries labels : context : $1 processor : $2 type : processors type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>MaxProcessingTime' name : org.apache.camel.MaxProcessingTime help : Maximum Processing Time labels : context : $1 processor : $2 type : processors type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>MeanProcessingTime' name : org.apache.camel.MeanProcessingTime help : Mean Processing Time labels : context : $1 processor : $2 type : processors type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>MinProcessingTime' name : org.apache.camel.MinProcessingTime help : Minimum Processing Time labels : context : $1 processor : $2 type : processors type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>LastProcessingTime' name : org.apache.camel.LastProcessingTime help : Last Processing Time labels : context : $1 processor : $2 type : processors type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>DeltaProcessingTime' name : org.apache.camel.DeltaProcessingTime help : Delta Processing Time labels : context : $1 processor : $2 type : processors type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>Redeliveries' name : org.apache.camel.Redeliveries help : Redeliveries labels : context : $1 processor : $2 type : processors type : COUNTER - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>TotalProcessingTime' name : org.apache.camel.TotalProcessingTime help : Total Processing Time labels : context : $1 processor : $2 type : processors type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=processors, name=([^,]+)><>InflightExchanges' name : org.apache.camel.InflightExchanges help : Inflight Exchanges labels : context : $1 processor : $2 type : processors type : COUNTER # Consumers - pattern : 'org.apache.camel<context=([^,]+), type=consumers, name=([^,]+)><>InflightExchanges' name : org.apache.camel.InflightExchanges help : Inflight Exchanges labels : context : $1 consumer : $2 type : consumers type : GAUGE # Services - pattern : 'org.apache.camel<context=([^,]+), type=services, name=([^,]+)><>MaxDuration' name : org.apache.camel.MaxDuration help : Maximum Duration labels : context : $1 service : $2 type : services type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=services, name=([^,]+)><>MeanDuration' name : org.apache.camel.MeanDuration help : Mean Duration labels : context : $1 service : $2 type : services type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=services, name=([^,]+)><>MinDuration' name : org.apache.camel.MinDuration help : Minimum Duration labels : context : $1 service : $2 type : services type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=services, name=([^,]+)><>TotalDuration' name : org.apache.camel.TotalDuration help : Total Duration labels : context : $1 service : $2 type : services type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=services, name=([^,]+)><>ThreadsBlocked' name : org.apache.camel.ThreadsBlocked help : Threads Blocked labels : context : $1 service : $2 type : services type : GAUGE - pattern : 'org.apache.camel<context=([^,]+), type=services, name=([^,]+)><>ThreadsInterrupted' name : org.apache.camel.ThreadsInterrupted help : Threads Interrupted labels : context : $1 service : $2 type : services type : GAUGE - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>NumLogicalRuntimeFaults' name : org.apache.cxf.NumLogicalRuntimeFaults help : Number of logical runtime faults type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+)><>NumLogicalRuntimeFaults' name : org.apache.cxf.NumLogicalRuntimeFaults help : Number of logical runtime faults type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>AvgResponseTime' name : org.apache.cxf.AvgResponseTime help : Average Response Time type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+)><>AvgResponseTime' name : org.apache.cxf.AvgResponseTime help : Average Response Time type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>NumInvocations' name : org.apache.cxf.NumInvocations help : Number of invocations type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+)><>NumInvocations' name : org.apache.cxf.NumInvocations help : Number of invocations type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>MaxResponseTime' name : org.apache.cxf.MaxResponseTime help : Maximum Response Time type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+)><>MaxResponseTime' name : org.apache.cxf.MaxResponseTime help : Maximum Response Time type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>MinResponseTime' name : org.apache.cxf.MinResponseTime help : Minimum Response Time type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>MinResponseTime' name : org.apache.cxf.MinResponseTime help : Minimum Response Time type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>TotalHandlingTime' name : org.apache.cxf.TotalHandlingTime help : Total Handling Time type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+)><>TotalHandlingTime' name : org.apache.cxf.TotalHandlingTime help : Total Handling Time type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>NumRuntimeFaults' name : org.apache.cxf.NumRuntimeFaults help : Number of runtime faults type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+)><>NumRuntimeFaults' name : org.apache.cxf.NumRuntimeFaults help : Number of runtime faults type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>NumUnCheckedApplicationFaults' name : org.apache.cxf.NumUnCheckedApplicationFaults help : Number of unchecked application faults type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+)><>NumUnCheckedApplicationFaults' name : org.apache.cxf.NumUnCheckedApplicationFaults help : Number of unchecked application faults type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+), operation=([^,]+)><>NumCheckedApplicationFaults' name : org.apache.cxf.NumCheckedApplicationFaults help : Number of checked application faults type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4 operation : $5 - pattern : 'org.apache.cxf<bus.id=([^,]+), type=([^,]+), service=([^,]+), port=([^,]+)><>NumCheckedApplicationFaults' name : org.apache.cxf.NumCheckedApplicationFaults help : Number of checked application faults type : GAUGE labels : bus.id : $1 type : $2 service : $3 port : $4` Copy to Clipboard Toggle word wrap
6. Add the following to the `Application.java` of your Camel application. `import java.io.InputStream; import io.micrometer.core.instrument.Clock; import org.apache.camel.CamelContext; import org.apache.camel.spring.boot.CamelContextConfiguration; import org.springframework.context.annotation.Bean; import org.apache.camel.component.micrometer.MicrometerConstants; import org.apache.camel.component.micrometer.eventnotifier.MicrometerExchangeEventNotifier; import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier; import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryFactory; import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;` Copy to Clipboard Toggle word wrap
7. The updated `Application.java` is shown below. `@SpringBootApplication public class SampleCamelApplication { @Bean(name = {MicrometerConstants.METRICS_REGISTRY_NAME, "prometheusMeterRegistry"}) public PrometheusMeterRegistry prometheusMeterRegistry( PrometheusConfig prometheusConfig, CollectorRegistry collectorRegistry, Clock clock) throws MalformedObjectNameException, IOException { InputStream resource = new ClassPathResource("config/prometheus_exporter_config.yml").getInputStream(); new JmxCollector(resource).register(collectorRegistry); new BuildInfoCollector().register(collectorRegistry); return new PrometheusMeterRegistry(prometheusConfig, collectorRegistry, clock); } @Bean public CamelContextConfiguration camelContextConfiguration(@Autowired PrometheusMeterRegistry registry) { return new CamelContextConfiguration() { @Override public void beforeApplicationStart(CamelContext camelContext) { MicrometerRoutePolicyFactory micrometerRoutePolicyFactory = new MicrometerRoutePolicyFactory(); micrometerRoutePolicyFactory.setMeterRegistry(registry); camelContext.addRoutePolicyFactory(micrometerRoutePolicyFactory); MicrometerMessageHistoryFactory micrometerMessageHistoryFactory = new MicrometerMessageHistoryFactory(); micrometerMessageHistoryFactory.setMeterRegistry(registry); camelContext.setMessageHistoryFactory(micrometerMessageHistoryFactory); MicrometerExchangeEventNotifier micrometerExchangeEventNotifier = new MicrometerExchangeEventNotifier(); micrometerExchangeEventNotifier.setMeterRegistry(registry); camelContext.getManagementStrategy().addEventNotifier(micrometerExchangeEventNotifier); MicrometerRouteEventNotifier micrometerRouteEventNotifier = new MicrometerRouteEventNotifier(); micrometerRouteEventNotifier.setMeterRegistry(registry); camelContext.getManagementStrategy().addEventNotifier(micrometerRouteEventNotifier); } @Override public void afterApplicationStart(CamelContext camelContext) { } }; }` Copy to Clipboard Toggle word wrap
8. Deploy the application to OpenShift. `mvn -Popenshift oc:deploy` Copy to Clipboard Toggle word wrap
9. Verify if your application is deployed. `oc get pods -n myapp NAME READY STATUS RESTARTS AGE camel-example-spring-boot-xml-2-deploy 0/1 Completed 0 13m camel-example-spring-boot-xml-2-x78rk 1/1 Running 0 13m camel-example-spring-boot-xml-s2i-2-build 0/1 Completed 0 14m` Copy to Clipboard Toggle word wrap
10. Add the Service Monitor for this application so that Openshift's prometheus instance can start scraping from the / actuator/prometheus endpoint.
11. Verify that the service monitor was successfully deployed. `oc get servicemonitor NAME AGE csb-demo-monitor 9m17s` Copy to Clipboard Toggle word wrap
12. Verify that you can see the service monitor in the list of scrape targets. In the Administrator view, navigate to Observe → Targets. You can find `csb-demo-monitor` within the list of scrape targets.
13. Wait about ten minutes after deploying the servicemonitor. Then navigate to the Observe → Metrics in the Developer view. Select **Custom query** in the drop-down menu and type `camel` to view the Camel metrics that are exposed through the /actuator/prometheus endpoint.

Note

Red Hat does not offer support for installing and configuring Prometheus and Grafana on non-OCP environments.

## [Chapter 7. Using Agroal database connection pool Copy link](#csb-using-agroal-database-connection-pool)

Agroal is a fast and lightweight database connection pool. The `agroal-spring-boot-starter` is a Red Hat build of Camel Spring Boot starter project that simplifies the integration of Agroal into Spring Boot applications. The `io.agroal.springframework.boot.AgroalDataSource` class is a Spring Boot-compatible implementation of the `javax.sql.DataSource` interface, providing a high-performance connection pool for database operations. It is a part of the Agroal connection pooling library developed by Red Hat.

### [7.1. Key Features Copy link](#key_features)

The key features of Agroal are as follows:

- High-Performance Connection Pooling: Efficiently manages database connections with configurable pool settings
- Spring Boot Integration: Seamlessly integrates with Spring Boot's auto-configuration
- Transaction Support: Provides JTA transaction integration through Narayana
- Connection Monitoring: Includes detailed logging and metrics for connection lifecycle
- Leak Detection: Built-in connection leak detection and reporting

### [7.2. Dependencies Copy link](#dependencies)

When using Agroal with Red Hat build of Camel Spring Boot, add the necessary dependencies to your pom.xml.

- `camel-spring-boot-starter` : For seamless integration of Camel with Spring Boot.
- `camel-jdbc-starter` : If you are using the Camel JDBC component.
- `agroal-spring-boot-starter` : For Agroal integration with Spring Boot.
- Your database driver (e.g., postgresql, mysql).

**Dependencies**

```
<dependency>
   <groupId>org.apache.camel.springboot</groupId>
   <artifactId>camel-spring-boot-starter</artifactId>
</dependency>
<dependency>
   <groupId>org.apache.camel.springboot</groupId>
   <artifactId>camel-jdbc-starter</artifactId>
</dependency>
<dependency>
   <groupId>io.agroal</groupId>
   <artifactId>agroal-spring-boot-starter</artifactId>
 </dependency>
<!-- Replace with your database driver -->
<dependency>
   <groupId>org.postgresql</groupId>
   <artifactId>postgresql</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

### [7.3. Configuring Agroal Copy link](#configuring_agroal)

Following section explains how to configure and use Agroal in your application.

**Procedure**

1. Add the necessary dependencies as shown above.
2. Define database connection details and Agroal-specific properties in the `application.properties` or the `application.yml` file. Agroal will use these to create and manage the connection pool. `# Database connection spring.datasource.url=jdbc:postgresql://localhost:5432/mydb spring.datasource.username=myuser spring.datasource.password=mypassword # Agroal-specific settings (in seconds) spring.datasource.agroal.max-size=20 spring.datasource.agroal.min-size=5 spring.datasource.agroal.initial-size=5 spring.datasource.agroal.acquisition-timeout=30` Copy to Clipboard Toggle word wrap
3. Define Camel Routes. Create the Camel routes that interact with your database using the JDBC component. Camel's JDBC component can be configured to use the DataSource provided by Spring Boot, which is managed by Agroal. `import org.apache.camel.builder.RouteBuilder; import org.springframework.stereotype.Component; @Component public class MyDatabaseRoute extends RouteBuilder { @Override public void configure() throws Exception { from("timer:myTimer?period=5000") .setBody().constant("SELECT * FROM my_table") .to("jdbc:dataSource") // 'dataSource' is the default name for Spring's DataSource .log("Query Result: ${body}"); } }` Copy to Clipboard Toggle word wrap
4. Ensure your main Spring Boot application class is correctly set up to run the application. `import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; @SpringBootApplication public class AgroalCamelApplication { public static void main(String[] args) { SpringApplication.run(AgroalCamelApplication.class, args); } }` Copy to Clipboard Toggle word wrap

Spring Boot's auto-configuration detects the `agroal-spring-boot-starter` and configures Agroal as the connection pool for your DataSource bean. The `camel-spring-boot-starter` automatically configures a CamelContext and makes it available as a Spring bean. When using the jdbc component in your Camel routes, it automatically leverages the DataSource bean managed by Agroal, ensuring efficient and robust database connectivity.

### [7.4. API changes in AgroalDataoSourceAutoConfiguration Copy link](#api_changes_in_agroaldataosourceautoconfiguration)

When upgrading from version 4.10.3 to 4.10.7 of Red Hat build of Camel Spring Boot, the API changes in AgroalDataSourceAutoConfiguration require code updates, which specifically affect programmatic DataSource configurations. Projects that use programmatic DataSource definitions (rather than auto-configuration) will experience compilation errors and need code modifications. After upgrading from version 4.10.3 to 4.10.7, the `AgroalDataSourceAutoConfiguration` constructor requires additional parameters wrapped in `ObjectProvider` .

Following sample shows the required changes.

**Before version 4.10.7**

```
@ConfigurationProperties("app.datasource.ds1.agroal")
    public AgroalDataSource firstDataSource(
        @Qualifier("ds1properties") DataSourceProperties properties,
        JtaTransactionManager jtaPlatform,
        XAResourceRecoveryRegistry xaResourceRecoveryRegistry,
        ObjectProvider<AgroalDataSourceJndiBinder> jndiBinder) {

        return new AgroalDataSourceAutoConfiguration(jtaPlatform, xaResourceRecoveryRegistry)
            .dataSource(properties, false, false, jndiBinder);
    }
```

Copy to Clipboard

Toggle word wrap

**After upgrading to version 4.10.7**

```
@ConfigurationProperties("app.datasource.ds1.agroal")
    public AgroalDataSource firstDataSource(
        @Qualifier("ds1properties") DataSourceProperties properties,
        ObjectProvider<JtaTransactionManager> jtaPlatform,
        ObjectProvider<XAResourceRecoveryRegistry> xaResourceRecoveryRegistry,
        ObjectProvider<AgroalDataSourceJndiBinder> jndiBinder,
        ObjectProvider<AgroalSecurityProvider> securityProvider) {

        return new AgroalDataSourceAutoConfiguration(jtaPlatform, xaResourceRecoveryRegistry, jndiBinder, securityProvider)
            .dataSource(properties, true, false, false, new ArrayList<Object>(), new ArrayList<Object>());
    }
```

Copy to Clipboard

Toggle word wrap

### [7.5. Monitoring and Logging Copy link](#monitoring_and_logging)

The class includes built-in logging capabilities through an internal LoggingListener that tracks:

- Connection creation and destruction
- Connection acquisition and return
- Connection validation events
- Connection leak detection

The metrics will be enabled using the follwing property.

```
spring.datasource.agroal.metrics = true
```

Copy to Clipboard

Toggle word wrap

This property exposes the following metrics documented in the [javadoc](https://javadoc.io/doc/io.agroal/agroal-api/latest/io/agroal/api/AgroalDataSourceMetrics.html)

- agroal.acquire.count
- agroal.awaiting.count
- agroal.blocking.time.average
- agroal.blocking.time.max
- agroal.blocking.time.total
- agroal.connections.active.count
- agroal.connections.available.count
- agroal.connections.creation.count
- agroal.connections.creation.time.average
- agroal.connections.creation.time.max
- agroal.connections.creation.time.total
- agroal.connections.destroy.count
- agroal.connections.flush.count
- agroal.connections.invalid.count
- agroal.connections.max.used.count
- agroal.connections.reap.count
- agroal.leak.detection.count

### [7.6. Basic DataSource Configuration Copy link](#basic_datasource_configuration)

Following section explains the basic datasource configuration using Agroal.

- Properties `# DataSource name and implementation spring.datasource.agroal.name=<datasource-name> spring.datasource.agroal.implementation=<AGROAL|AGROAL_POOLLESS>` Copy to Clipboard Toggle word wrap
- Database connection settings `spring.datasource.url=jdbc:postgresql://localhost:5432/mydb spring.datasource.username=myuser spring.datasource.password=mypassword spring.datasource.driver-class-name=org.postgresql.Driver` Copy to Clipboard Toggle word wrap
- Connection Pool Configuration `# Pool sizing spring.datasource.agroal.max-size=20 spring.datasource.agroal.min-size=5 spring.datasource.agroal.initial-size=5 # Validation settings spring.datasource.agroal.validate-on-borrow=true spring.datasource.agroal.connection-validator-name=<validator-name> spring.datasource.agroal.exception-sorter-name=<sorter-name>` Copy to Clipboard Toggle word wrap
- Timeout Configuration `# Connection timeouts (in seconds) spring.datasource.agroal.acquisition-timeout=30 spring.datasource.agroal.foreground-validation-timeout=5 spring.datasource.agroal.idle-timeout=300 spring.datasource.agroal.leak-timeout=0 spring.datasource.agroal.lifetime-timeout=0 spring.datasource.agroal.validation-timeout=5` Copy to Clipboard Toggle word wrap
- Connection Factory Configuration `spring.datasource.agroal.initial-sql=SELECT 1 spring.datasource.agroal.auto-commit=true spring.datasource.agroal.track-resources=true spring.datasource.agroal.jdbc-transaction-isolation=2` Copy to Clipboard Toggle word wrap
- Recovery credentials (for XA transactions) `spring.datasource.agroal.recovery-username=recovery_user spring.datasource.agroal.recovery-password=recovery_password` Copy to Clipboard Toggle word wrap
- Advanced Configuration `spring.datasource.agroal.metrics=true` Copy to Clipboard Toggle word wrap
- Advanced pool settings `spring.datasource.agroal.enhanced-leak-report=false spring.datasource.agroal.flush-on-close=false` Copy to Clipboard Toggle word wrap
- JDBC and XA properties (as nested properties) `spring.datasource.agroal.jdbc-properties.key1=value1 spring.datasource.agroal.jdbc-properties.key2=value2 spring.datasource.agroal.xa-properties.key1=value1 spring.datasource.agroal.xa-properties.key2=value2` Copy to Clipboard Toggle word wrap
- Transaction Integration `# JTA Transaction Management # Note: These are typically configured programmatically through beans # spring.datasource.agroal.jta-transaction-integration=<bean-reference> # spring.datasource.agroal.jta-transaction-manager=<bean-reference>` Copy to Clipboard Toggle word wrap

### [7.7. Property Reference Table Copy link](#property_reference_table)

Expand

| Property                                               | Type    | Default   | Description                                         |
|--------------------------------------------------------|---------|-----------|-----------------------------------------------------|
| spring.datasource.agroal.name                          | String  | <default> | DataSource name for logging                         |
| spring.datasource.agroal.implementation                | String  | AGROAL    | DataSource implementation (AGROAL, AGROAL_POOLLESS) |
| spring.datasource.agroal.max-size                      | Integer | 10        | Maximum number of connections in pool               |
| spring.datasource.agroal.min-size                      | Integer | 0         | Minimum number of connections in pool               |
| spring.datasource.agroal.initial-size                  | Integer | 0         | Initial number of connections created               |
| spring.datasource.agroal.validate-on-borrow            | Boolean | false     | Validate connections when borrowed                  |
| spring.datasource.agroal.connection-validator-name     | String  | -         | Connection validator implementation                 |
| spring.datasource.agroal.exception-sorter-name         | String  | -         | Exception sorter implementation                     |
| spring.datasource.agroal.acquisition-timeout           | Integer | -         | Connection acquisition timeout (seconds)            |
| spring.datasource.agroal.foreground-validation-timeout | Integer | -         | Foreground validation timeout (seconds)             |
| spring.datasource.agroal.idle-timeout                  | Integer | -         | Connection idle timeout (seconds)                   |
| spring.datasource.agroal.leak-timeout                  | Integer | -         | Connection leak detection timeout (seconds)         |
| spring.datasource.agroal.lifetime-timeout              | Integer | -         | Connection maximum lifetime (seconds)               |
| spring.datasource.agroal.validation-timeout            | Integer | -         | Connection validation timeout (seconds)             |
| spring.datasource.agroal.initial-sql                   | String  | -         | SQL to execute on new connections                   |
| spring.datasource.agroal.auto-commit                   | Boolean | true      | Enable auto-commit on connections                   |
| spring.datasource.agroal.track-resources               | Boolean | false     | Track JDBC resources                                |
| spring.datasource.agroal.recovery-username             | String  | -         | XA recovery username                                |
| spring.datasource.agroal.recovery-password             | String  | -         | XA recovery password                                |
| spring.datasource.agroal.jdbc-transaction-isolation    | Integer | -         | JDBC transaction isolation level                    |
| spring.datasource.agroal.metrics                       | Boolean | false     | Enable metrics collection                           |
| spring.datasource.agroal.enhanced-leak-report          | Boolean | false     | Enhanced leak reporting                             |
| spring.datasource.agroal.flush-on-close                | Boolean | false     | Flush connections on datasource close               |
| spring.datasource.agroal.jdbc-properties.*             | Map     | -         | Additional JDBC properties                          |
| spring.datasource.agroal.xa-properties.*               | Map     | -         | XA datasource properties                            |

Show more

## [Chapter 8. Using Camel with Spring XML Copy link](#csb-camel-spring-xml)

Using Camel with Spring XML files is a way of using XML DSL with Camel. Camel has historically been using Spring XML for a long time. The Spring framework started with XML files as a popular and common configuration for building Spring applications.

**Example of Spring application**

```
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
       http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
       http://camel.apache.org/schema/spring http://camel.apache.org/schema/spring/camel-spring.xsd
    ">

    <camelContext xmlns="http://camel.apache.org/schema/spring">
        <route>
            <from uri="direct:a"/>
            <choice>
                <when>
                    <xpath>$foo = 'bar'</xpath>
                    <to uri="direct:b"/>
                </when>
                <when>
                    <xpath>$foo = 'cheese'</xpath>
                    <to uri="direct:c"/>
                </when>
                <otherwise>
                    <to uri="direct:d"/>
                </otherwise>
            </choice>
        </route>
    </camelContext>

</beans>
```

Copy to Clipboard

Toggle word wrap

### [8.1. Using Java DSL with Spring XML files Copy link](#using_java_dsl_with_spring_xml_files)

You can use Java Code to define your RouteBuilder implementations. These are defined as beans in spring and then referenced in your camel context, as shown:

```
<camelContext xmlns="http://camel.apache.org/schema/spring">
  <routeBuilder ref="myBuilder"/>
</camelContext>

<bean id="myBuilder" class="org.apache.camel.spring.example.test1.MyRouteBuilder"/>
```

Copy to Clipboard

Toggle word wrap

#### [8.1.1. Configure Spring Boot Application Copy link](#configure_spring_boot_application)

To use Spring Boot Autoconfigure XML routes for beans, you musy import the XML resource. To do this, you can use a `Configuration` class.

For example, given that the Spring XML file is located to `src/main/resources/camel-context.xml` you can use the following configuration class to load the camel-context:

**Example: using a** **`Configuration`** **class**

```
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;


/**
 * A Configuration class that import the Spring XML resource
 */
@Configuration
// load the spring xml file from classpath
@ImportResource("classpath:camel-context.xml")
public class CamelSpringXMLConfiguration {
}
```

Copy to Clipboard

Toggle word wrap

Tip

For a sample application, see the [XML import](https://github.com/jboss-fuse/camel-spring-boot-examples/tree/camel-spring-boot-examples-4.14.2.redhat-00001-patch-1/xml-import/) example in the [camel-spring-boot-examples](https://github.com/jboss-fuse/camel-spring-boot-examples/) repository.

### [8.2. Specifying Camel routes using Spring XML Copy link](#specifying_camel_routes_using_spring_xml)

You can use Spring XML files to specify Camel routes using XML DSL as shown:

```
<camelContext id="camel-A" xmlns="http://camel.apache.org/schema/spring">
  <route>
    <from uri="seda:start"/>
    <to uri="mock:result"/>
  </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

### [8.3. Configuring Components and Endpoints Copy link](#configuring_components_and_endpoints)

You can configure your Component or Endpoint instances in your Spring XML as follows in this example.

```
<camelContext id="camel" xmlns="http://camel.apache.org/schema/spring">
</camelContext>

<bean id="jmsConnectionFactory" class="org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory">
  <property name="brokerURL" value="tcp:someserver:61616"/>
</bean>
<bean id="jms" class="org.apache.camel.component.jms.JmsComponent">
  <property name="connectionFactory">
    <bean class="org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory">
  <property name="brokerURL" value="tcp:someserver:61616"/>
      </bean>
  </property>
</bean>
```

Copy to Clipboard

Toggle word wrap

This allows you to configure a component using any name, but its common to use the same name, for example, `jms` . Then you can refer to the component using `jms:destinationName` .

This works by the Camel fetching components from the Spring context for the scheme name you use for Endpoint URIs.

### [8.4. Using package scanning Copy link](#using_package_scanning)

Camel also provides a powerful feature that allows for the automatic discovery and initialization of routes in given packages. This is configured by adding tags to the camel context in your spring context definition, specifying the packages to be recursively searched for `RouteBuilder` implementations. To use this feature add a &lt;package&gt;&lt;/package&gt; tag specifying a comma separated list of packages that should be searched. For example,

```
<camelContext>
  <packageScan>
    <package>com.foo</package>
    <excludes>**.*Excluded*</excludes>
    <includes>**.*</includes>
  </packageScan>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This scans for RouteBuilder classes in the `com.foo` and the sub-packages.

You can also filter the classes with includes or excludes such as:

```
<camelContext>
  <packageScan>
    <package>com.foo</package>
    <excludes>**.*Special*</excludes>
  </packageScan>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This skips the classes that has Special in the name. Exclude patterns are applied before the include patterns. If no include or exclude patterns are defined then all the Route classes discovered in the packages are returned.

`?` matches one character, `*` matches zero or more characters, `**` matches zero or more segments of a fully qualified name.

### [8.5. Using context scanning Copy link](#using_context_scanning)

You can allow Camel to scan the container context, for example, the Spring `ApplicationContext` for route builder instances. This allows you to use the Spring `<component-scan>` feature and have Camel pickup any RouteBuilder instances which was created by Spring in its scan process.

```
<!-- enable Spring @Component scan -->
<context:component-scan base-package="org.apache.camel.spring.issues.contextscan"/>

<camelContext xmlns="http://camel.apache.org/schema/spring">
    <!-- and then let Camel use those @Component scanned route builders -->
    <contextScan/>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This allows you to just annotate your routes using the Spring `@Component` and have those routes included by Camel:

```
@Component
public class MyRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:start")
            .to("mock:result");
    }
}
```

Copy to Clipboard

Toggle word wrap

You can also use the ANT style for inclusion and exclusion, as mentioned above in the package scan section.

## [Chapter 9. XML IO DSL Copy link](#csb-camel-spring-xml-io-dsl)

The `xml-io-dsl` is the Camel optimized XML DSL with a very fast and low overhead XML parser. It is a source code generated parser that is Camel specific and can only parse Camel `.xml` route files (not classic Spring `<beans>` XML files).

We recommend that you use `xml-io-dsl` instead of `xml-jaxb-dsl` for Camel XML DSL. It works with all Camel runtimes.

Note

When you are using XML IO DSL, the `camel-spring-boot` application will by default look for xml files in `src/main/resources/camel/*.xml` .

You can configure this behavior by providing a different path in the `camel.springboot.routes-include-pattern` property:

```
camel.springboot.routes-include-pattern=/path/to/*.xml
```

### [9.1. Example Copy link](#example)

The following `my-route.xml` source file can be loaded and run with Camel CLI or Camel K:

**my-route.xml**

```
<routes xmlns="http://camel.apache.org/schema/spring">
    <route>
        <from uri="timer:tick"/>
        <setBody>
            <constant>Hello Camel K!</constant>
         </setBody>
        <to uri="log:info"/>
    </route>
</routes>
```

Copy to Clipboard

Toggle word wrap

Tip

You can omit the `xmlns` namespace.

If there is only a single route, you can use `<route>` as the root XML tag instead of `<routes>` .

**Running with Camel K**

```
kamel run my-route.xml
```

Copy to Clipboard

Toggle word wrap

**Running with Camel CLI**

```
camel run my-route.xml
```

Copy to Clipboard

Toggle word wrap

You can use `xml-io-dsl` to declare some beans to be bound to the Camel Registry.

You can declare and Beans define their properties (including `nested` properties) in XML. For example:

**Bean declaration and definition**

```
<camel>

	<bean name="beanFromProps" type="com.acme.MyBean">
		<properties>
			<property key="field1" value="f1_p" />
			<property key="field2" value="f2_p" />
			<property key="nested.field1" value="nf1_p" />
			<property key="nested.field2" value="nf2_p" />
		</properties>
	</bean>

</camel>
```

Copy to Clipboard

Toggle word wrap

While keeping all the benefits of fast XML parser used by `xml-io-dsl` , Camel can also process XML elements declared in other XML namespaces and process them separately. With this mechanism it is possible to include XML elements using Spring's [`http://www.springframework.org/schema/beans`](http://www.springframework.org/schema/beans) namespace.

This brings the flexibility of Spring Beans into Camel main without actually running any Spring Application Context (or Spring Boot).

When elements from Spring namespace are found, they are used to populate and configure an instance of `org.springframework.beans.factory.support.DefaultListableBeanFactory` and leverage Spring dependency injection to wire the beans together.

These beans are then exposed through normal Camel Registry and may be used by Camel routes.

Here's an example `camel.xml` file, which defines both the routes and beans used (referred to) by the route definition:

**camel.xml**

```
<camel>

    <beans xmlns="http://www.springframework.org/schema/beans">
        <bean id="messageString" class="java.lang.String">
            <constructor-arg index="0" value="Hello"/>
        </bean>

        <bean id="greeter" class="org.apache.camel.main.app.Greeter">
            <description>Spring Bean</description>
            <property name="message">
                <bean class="org.apache.camel.main.app.GreeterMessage">
                    <property name="msg" ref="messageString"/>
                </bean>
            </property>
        </bean>
    </beans>

    <route id="my-route">
        <from uri="direct:start"/>
        <bean ref="greeter"/>
        <to uri="mock:finish"/>
    </route>

</camel>
```

Copy to Clipboard

Toggle word wrap

A `my-route` route is referring to `greeter` bean which is defined using Spring `<bean>` element.

More examples can be found on the Apache [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) page.

### [9.2. Using beans with constructors Copy link](#using_beans_with_constructors)

When you want to create beans with constructor arguments, from Camel 4.1 onwards you can add them as XML tags. For example:

**Camel 4.1+: Beans with** **`constructor`** **tags**

```
<camel>

	<bean name="beanFromProps" type="com.acme.MyBean">
        <constructors>
          <constructor index="0" value="true"/>
          <constructor index="1" value="Hello World"/>
        </constructors>
        <!-- and you can still have properties -->
		<properties>
			<property key="field1" value="f1_p" />
			<property key="field2" value="f2_p" />
			<property key="nested.field1" value="nf1_p" />
			<property key="nested.field2" value="nf2_p" />
		</properties>
	</bean>

</camel>
```

Copy to Clipboard

Toggle word wrap

If you use Camel 4.0, you must put then constructor arguments in the `type` attribute:

**Camel 4.0: Beans with** **`constructor`** **arguments in the** **`type`** **attribute**

```
<bean name="beanFromProps" type="com.acme.MyBean(true, 'Hello World')">
    <properties>
        <property key="field1" value="f1_p" />
        <property key="field2" value="f2_p" />
        <property key="nested.field1" value="nf1_p" />
        <property key="nested.field2" value="nf2_p" />
    </properties>
</bean>
```

Copy to Clipboard

Toggle word wrap

### [9.3. Creating beans from factory method Copy link](#creating_beans_from_factory_method)

A bean can also be created from a `public static` factory method:

**Factory method XML**

```
<bean name="myBean" type="com.acme.MyBean" factoryMethod="createMyBean">
        <constructors>
          <constructor index="0" value="true"/>
          <constructor index="1" value="Hello World"/>
        </constructors>
	</bean>
```

Copy to Clipboard

Toggle word wrap

When you use a `factoryMethod` , you must provide `constructor` tags for the arguments.

For example, this means that the class `com.acme.MyBean` should be as follows:

**Factory method**

```
public class MyBean {

    public static MyBean createMyBean(boolean important, String message) {
        MyBean answer = ...
        // create and configure the bean
        return answer;
    }
}
```

Copy to Clipboard

Toggle word wrap

Note

You must make the factory method `public static` in the created class.

### [9.4. Creating beans from builder classes Copy link](#creating_beans_from_builder_classes)

You can create a bean created from another builder class as shown below:

**Builder XML**

```
<bean name="myBean" type="com.acme.MyBean"
          builderClass="com.acme.MyBeanBuilder" builderMethod="createMyBean">
        <properties>
          <property key="id" value="123"/>
          <property key="name" value="Acme"/>
        </constructors>
	</bean>
```

Copy to Clipboard

Toggle word wrap

Note

You must make the builder class `public` with a no-arg default constructor.

You can then use the builder class to create the actual bean by using fluent builder style configuration.

Set the properties on the builder class, and create the bean by invoking the `builderMethod` at the end.

You invocate this method via Java reflection.

### [9.5. Creating beans from factory bean Copy link](#creating_beans_from_factory_bean)

You can create a bean from a factory bean as shown below:

**Factory XML**

```
<bean name="myBean" type="com.acme.MyBean"
          factoryBean="com.acme.MyHelper" factoryMethod="createMyBean">
        <constructors>
          <constructor index="0" value="true"/>
          <constructor index="1" value="Hello World"/>
        </constructors>
	</bean>
```

Copy to Clipboard

Toggle word wrap

Tip

You can also use `factoryBean` to refer to an existing bean by bean id instead of the FQN classname.

When you use a `factoryBean` the, you must provide arguments as `constructor` tags.

For example, the class `com.acme.MyHelper` should be as follows:

**Factory bean**

```
public class MyHelper {

    public static MyBean createMyBean(boolean important, String message) {
        MyBean answer = ...
        // create and configure the bean
        return answer;
    }
}
```

Copy to Clipboard

Toggle word wrap

Note

You must make the factory method `public static` .

### [9.6. Creating beans using script language Copy link](#creating_beans_using_script_language)

If you have advanced use-cases, you can inline a script language, such as groovy, java, javascript, and so on, to create the bean.

With scripting, you can be more flexible and use a bit of programming to create and configure the bean:

**Scripting**

```
<bean name="myBean" type="com.acme.MyBean" scriptLanguage="groovy">
        <script>
      // some groovy script here to create the bean
      bean = ...
      ...
      return bean
        </script>
	</bean>
```

Copy to Clipboard

Toggle word wrap

Note

When you use `script` , the constructors, factory bean, and factory method are not used.

### [9.7. Using init and destroy methods on beans Copy link](#using_init_and_destroy_methods_on_beans)

If you need to do initialization and cleanup work before you use a bean, you can use the `initMethod` and `destroyMethod` which are triggered as appropriate by Camel.

Those methods must be `public void` and have no arguments, as shown below:

**Initialization and cleanup methods**

```
public class MyBean {

    public void initMe() {
        // do init work here
    }

    public void destroyMe() {
        // do cleanup work here
    }

}
```

Copy to Clipboard

Toggle word wrap

You also have to declare those methods in the XML DSL as follows:

**Initialization and cleanup XML**

```
<bean name="myBean" type="com.acme.MyBean"
          initMethod="initMe" destroyMethod="destroyMe">
        <constructors>
          <constructor index="0" value="true"/>
          <constructor index="1" value="Hello World"/>
        </constructors>
	</bean>
```

Copy to Clipboard

Toggle word wrap

Both `initMethod` and `destroyMethod` are optional, so a bean does not have to have both.

### [9.8. REST and routes in the same XML IO DSL file Copy link](#rest_and_routes_in_the_same_xml_io_dsl_file)

You can have both REST and routes in the same DSL file:

**REST and routes in the same XML IO DSL file**

```
<camel xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns="http://camel.apache.org/schema/spring"
       xsi:schemaLocation="
            http://camel.apache.org/schema/spring
            https://camel.apache.org/schema/spring/camel-spring.xsd">
   <rest id="rest">
        <post id="post" path="start">
            <to uri="direct:start"/>
        </post>
    </rest>

    <route>
        <from uri="direct:start"/>
        <to uri="amqp:queue:Test.Broker.StreamMessage?jmsMessageType=Stream&amp;disableReplyTo=true"/>
    </route>
</camel>
```

Copy to Clipboard

Toggle word wrap

## [Legal Notice Copy link](#idm139912566249248)

Copyright © Red Hat. The text of and illustrations in this document are licensed by Red Hat under a Creative Commons Attribution-Share Alike 3.0 Unported license ("CC-BY-SA"). An explanation of CC-BY-SA is available at [http://creativecommons.org/licenses/by-sa/3.0/](http://creativecommons.org/licenses/by-sa/3.0/) . In accordance with CC-BY-SA, if you distribute this document or an adaptation of it, you must provide the URL for the original version. Red Hat, as the licensor of this document, waives the right to enforce, and agrees not to assert, Section 4d of CC-BY-SA to the fullest extent permitted by applicable law. Red Hat, Red Hat Enterprise Linux, the Shadowman logo, JBoss, OpenShift, Fedora, the Infinity logo, and RHCE are trademarks of Red Hat, Inc., registered in the United States and other countries.

Linux ® is the registered trademark of Linus Torvalds in the United States and other countries.

Java ® is a registered trademark of Oracle and/or its affiliates.

XFS ® is a trademark of Silicon Graphics International Corp. or its subsidiaries in the United States and/or other countries.

MySQL ® is a registered trademark of MySQL AB in the United States, the European Union and other countries.

Node.js ® is an official trademark of Joyent. Red Hat Software Collections is not formally related to or endorsed by the official Joyent Node.js open source or commercial project. The OpenStack ® Word Mark and OpenStack logo are either registered trademarks/service marks or trademarks/service marks of the OpenStack Foundation, in the United States and other countries and are used with the OpenStack Foundation's permission. We are not affiliated with, endorsed or sponsored by the OpenStack Foundation, or the OpenStack community. All other trademarks are the property of their respective owners.

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