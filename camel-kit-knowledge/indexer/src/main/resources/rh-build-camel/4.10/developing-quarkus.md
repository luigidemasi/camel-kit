## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Developing Applications with Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm139685902733072)
3. [1. Introduction to developing applications with Red Hat build of Apache Camel for Quarkus](#introduction_to_developing_applications_with_red_hat_build_of_apache_camel_for_quarkus)
4. 2. Dependency management
5. 3. REST DSL in Red Hat build of Apache Camel for Quarkus
6. 4. Defining Camel routes
7. 5. Testing routes in Camel Quarkus
8. 6. Configuration
9. 7. Contexts and Dependency Injection (CDI) in Camel Quarkus
10. 8. Observability
11. 9. Native mode
12. 10. Kubernetes
13. 11. Quarkus CXF security guide
14. 12. Camel Security
15. [Legal Notice](#idm139685894188080)

Format Multi-page Single-page View full doc as PDF

# Developing Applications with Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.10

## Developing Applications with Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm139685894188080)

**Abstract**

This guide is for developers writing Camel applications on top of Red Hat build of Apache Camel for Quarkus.

## [Preface Copy link](#idm139685902733072)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. To create a ticket, click this link: [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Introduction to developing applications with Red Hat build of Apache Camel for Quarkus Copy link](#introduction_to_developing_applications_with_red_hat_build_of_apache_camel_for_quarkus)

This guide is for developers writing Camel applications on top of Red Hat build of Apache Camel for Quarkus.

Camel components which are supported in Red Hat build of Apache Camel for Quarkus have an associated Red Hat build of Apache Camel for Quarkus extension. For more information about the Red Hat build of Apache Camel for Quarkus extensions supported in this distribution, see the [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) reference guide.

## [Chapter 2. Dependency management Copy link](#camel-quarkus-extensions-dependency-management)

A specific Red Hat build of Apache Camel for Quarkus release is supposed to work only with a specific Quarkus release.

### [2.1. Quarkus tooling for starting a new project Copy link](#quarkus_tooling_for_starting_a_new_project)

The easiest and most straightforward way to get the dependency versions right in a new project is to use one of the Quarkus tools:

- [code.camel.redhat.com](https://code.camel.redhat.com/) - an online project generator,
- [Quarkus Maven plugin](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.20/html-single/getting_started_with_red_hat_build_of_quarkus/index#con-apache-maven-plug-ins-and-quarkus_quarkus-getting-started)

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
        <!-- The latest 3.20.x version from https://maven.repository.redhat.com/ga/com/redhat/quarkus/platform/quarkus-bom -->
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

## [Chapter 3. REST DSL in Red Hat build of Apache Camel for Quarkus Copy link](#rest-dsl-in-camel-quarkus)

Apache Camel offers a REST styled DSL which you can use to define REST services (hosted by Camel) using a REST style with verbs such as `get` , `post` , `delete` , and so on.

### [3.1. How it works Copy link](#how_it_works)

The REST DSL is a facade that builds [Rest](https://rhaetor.github.io/rh-camel/components/4.10.x/rest-component.html) endpoints as consumers for Camel routes. The actual REST transport is leveraged by using Camel REST components such as [Netty HTTP](https://rhaetor.github.io/rh-camel/components/4.10.x/netty-http-component.html) , [Servlet](https://rhaetor.github.io/rh-camel/components/4.10.x/servlet-component.html) , and others that have native REST integration.

### [3.2. Components supporting REST DSL Copy link](#components_supporting_rest_dsl)

The following Camel components support the REST DSL:

- [camel-rest](https://rhaetor.github.io/rh-camel/components/4.10.x/rest-component.html) **required**
- [camel-netty-http](https://rhaetor.github.io/rh-camel/components/4.10.x/netty-http-component.html)
- [camel-jetty](https://rhaetor.github.io/rh-camel/components/4.10.x/jetty-component.html)
- [camel-platform-http](https://rhaetor.github.io/rh-camel/components/4.10.x/platform-http-component.html) **recommended**
- [camel-servlet](https://rhaetor.github.io/rh-camel/components/4.10.x/servlet-component.html)
- [camel-undertow](https://rhaetor.github.io/rh-camel/components/4.10.x/undertow-component.html)

### [3.3. REST DSL with code first Copy link](#camel-quarkus-extensions-rest-dsl-code-first)

Note

This section describes a *code-first* approach to working with REST DSL.

For a more modern *contract-first* approach, see the [REST DSL with OpenAPI contract first](#camel-quarkus-extensions-rest-dsl-contract-first)

#### [3.3.1. REST DSL with Java DSL Copy link](#rest_dsl_with_java_dsl)

To use the REST DSL in Java DSL, then just do as with regular Camel routes by extending the `RouteBuilder` and define the routes in the `configure` method.

A simple REST service can be defined as follows, where we use `rest()` to define the services as shown below:

```
@Override
public void configure() throws Exception {
    rest("/say")
        .get("/hello").to("direct:hello")
        .get("/bye").consumes("application/json").to("direct:bye")
        .post("/bye").to("mock:update");

    from("direct:hello")
        .transform().constant("Hello World");

    from("direct:bye")
        .transform().constant("Bye World");
}
```

Copy to Clipboard

Toggle word wrap

This defines a REST service with the following url mappings:

Expand

| Base Path      | Uri template     | Verb         | Consumes                 |
|----------------|------------------|--------------|--------------------------|
| *``` /say ```* | *``` /hello ```* | ``` get ```  | *all*                    |
| *``` /say ```* | *``` /bye ```*   | ``` get ```  | ``` application/json ``` |
| *``` /say ```* | *``` /bye ```*   | ``` post ``` | *all*                    |

Show more

Notice that in the REST service we route directly to a Camel endpoint using `to()` . This is because the REST DSL has a shorthand for routing directly to an endpoint using `to()` .

#### [3.3.2. REST DSL with XML DSL Copy link](#rest_dsl_with_xml_dsl)

The example above can be defined in XML as shown below:

```
<camelContext xmlns="http://camel.apache.org/schema/spring">
  <rest path="/say">
    <get path="/hello">
      <to uri="direct:hello"/>
    </get>
    <get path="/bye" consumes="application/json">
      <to uri="direct:bye"/>
    </get>
    <post path="/bye">
      <to uri="mock:update"/>
    </post>
  </rest>
  <route>
    <from uri="direct:hello"/>
    <transform>
      <constant>Hello World</constant>
    </transform>
  </route>
  <route>
    <from uri="direct:bye"/>
    <transform>
      <constant>Bye World</constant>
    </transform>
  </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

#### [3.3.3. Using a base path Copy link](#using_a_base_path)

The REST DSL allows defining a base path to help applying the *"don't repeat yourself"* (DRY) practice. For example, to define a customer path, we can set the base path in `rest("/customer")` and then provide the uri templates in the verbs, as shown below:

```
rest("/customers/")
    .get("/{id}").to("direct:customerDetail")
    .get("/{id}/orders").to("direct:customerOrders")
    .post("/neworder").to("direct:customerNewOrder");
```

Copy to Clipboard

Toggle word wrap

And using XML DSL, it becomes:

```
<rest path="/customers/">
  <get path="/{id}">
    <to uri="direct:customerDetail"/>
  </get>
  <get path="/{id}/orders">
    <to uri="direct:customerOrders"/>
  </get>
  <post path="/neworder">
    <to uri="direct:customerNewOrder"/>
  </post>
</rest>
```

Copy to Clipboard

Toggle word wrap

Tip

The REST DSL will take care of duplicate path separators when using base path and uri templates. In the example above the rest base path ends with a slash `/` and the verb starts with a slash `/` . Camel will take care of this and remove the duplicated slash.

It is not required to use both base path and uri templates. You can omit the base path and define the base path and uri template in the verbs only. The example above can be defined as:

```
<rest>
  <get path="/customers/{id}">
    <to uri="direct:customerDetail"/>
  </get>
  <get path="/customers/{id}/orders">
    <to uri="direct:customerOrders"/>
  </get>
  <post path="/customers/neworder">
    <to uri="direct:customerNewOrder"/>
  </post>
</rest>
```

Copy to Clipboard

Toggle word wrap

You can combine path parameters to build complex expressions. For example:

```
rest("items/")
     .get("{id}/{filename}.{content-type}")
     .to("direct:item")
```

Copy to Clipboard

Toggle word wrap

#### [3.3.4. Managing REST services Copy link](#managing_rest_services)

Each of the rest services becomes a Camel route, so in the first example, we have 2 x get and 1 x post REST service, which each becomes a Camel route. This makes it *the same* from Apache Camel to manage and run these services, as they are just Camel routes. This means any tooling and API today that deals with Camel routes, also work with the REST services.

Note

To use JMX with Camel then `camel-management` JAR must be included in the classpath.

This means you can use JMX to stop/start routes, and also get the JMX metrics about the routes, such as the number of messages processed, and their performance statistics.

There is also a REST Registry JMX MBean that contains a registry of all REST services that has been defined.

#### [3.3.5. Inline REST DSL as a single route Copy link](#inline_rest_dsl_as_a_single_route)

Important

Camel 4.4 or older has inline-routes disabled by default. Camel 4.5 or newer has inline-routes enabled by default.

Each of the rest services becomes a Camel route, and this means, that if the rest service is calling another Camel route via `direct` , which is a widespread practice. This means that each rest service then becomes two routes. This can become harder to manage if you have many rest services.

When you use `direct` endpoints then you can enable REST DSL to automatically *inline* the direct route in the rest route, meaning that there is only one route per rest service.

Warning

When using inline-routes, then each REST endpoint should link 1:1 to a unique `direct` endpoint. The linked *direct* routes are inlined and therefore does not **exists** as independent routes, and they cannot be called from other regular Camel routes. In other words the inlined routes are essentially moved inside the rest-dsl and does not exist as a route. See more detils further below.

To do this you **MUST** use `direct` endpoints, and each endpoint must be unique name per service. And the option `inlineRoutes` must be enabled.

For example, in the Java DSL below we have enabled inline routes and each rest service uses `direct` endpoints with unique names.

```
restConfiguration().inlineRoutes(true);

rest("/customers/")
    .get("/{id}").to("direct:customerDetail")
    .get("/{id}/orders").to("direct:customerOrders")
    .post("/neworder").to("direct:customerNewOrder");
```

Copy to Clipboard

Toggle word wrap

And in XML:

```
<restConfiguration inlineRoutes="true"/>

<rest>
  <get path="/customers/{id}">
    <to uri="direct:customerDetail"/>
  </get>
  <get path="/customers/{id}/orders">
    <to uri="direct:customerOrders"/>
  </get>
  <post path="/customers/neworder">
    <to uri="direct:customerNewOrder"/>
  </post>
</rest>
```

Copy to Clipboard

Toggle word wrap

If you use Camel Main, Camel Spring Boot, Camel Quarkus or Camel JBang, you can also enable this in `application.properties` such as:

```
camel.rest.inline-routes = true
```

Copy to Clipboard

Toggle word wrap

Notice the REST services above each use a unique 1:1 linked direct endpoint (direct:customerDetail, direct:customerOrders direct:customerNewOrder). This means that you cannot call these routes from another route such as the following would not function:

```
from("kafka:new-order")
   .to("direct:customerNewOrder");
```

Copy to Clipboard

Toggle word wrap

So if you desire to call common routes from both REST DSL and other regular Camel routes then keep these in separate routes as shown:

```
restConfiguration().inlineRoutes(true);

rest("/customers/")
    .get("/{id}").to("direct:customerDetail")
    .get("/{id}/orders").to("direct:customerOrders")
    .post("/neworder").to("direct:customerNewOrder");

from("direct:customerNewOrder")
  // do some stuff here
  .to("direct:commonCustomerNewOrder"); // call common route

from("direct:commonCustomerNewOrder")
  // do stuff here
  .log("Created new order");

from("kafka:new-order")
   .to("direct:commonCustomerNewOrder"); // make sure to call the common route
```

Copy to Clipboard

Toggle word wrap

Notice how the common shared route is separated into the route `direct:commonCustomerNewOrder` . Which can be called from both REST DSL and regular Camel routes.

#### [3.3.6. Disabling REST services Copy link](#disabling_rest_services)

While developing REST services using REST DSL, you may want to temporary disabled some REST endpoints, which you can do using `disabled` as shown in the following.

```
rest("/customers/")
    .get("/{id}").to("direct:customerDetail")
    .get("/{id}/orders").to("direct:customerOrders").disabled("{{ordersEnabled}}")
    .post("/neworder").to("direct:customerNewOrder").disabled();
```

Copy to Clipboard

Toggle word wrap

And in XML:

```
<rest>
  <get path="/customers/{id}">
    <to uri="direct:customerDetail"/>
  </get>
  <get path="/customers/{id}/orders" disabled="{{ordersEnabled}}">
    <to uri="direct:customerOrders"/>
  </get>
  <post path="/customers/neworder" disabled="true">
    <to uri="direct:customerNewOrder"/>
  </post>
</rest>
```

Copy to Clipboard

Toggle word wrap

In this example the last two REST endpoints are configured with `disabled` . You can use [Property Placeholder](manual:ROOT:using-propertyplaceholder.xml) to let an external configuration determine if the REST endpoint is disabled or not. In this example the `/customers/{id}/orders` endpoint is disabled via a placeholder. The last REST endpoint is hardcoded to be disabled.

#### [3.3.7. Binding to POJOs using Copy link](#binding_to_pojos_using)

The REST DSL supports automatic binding json/xml contents to/from POJOs using data formats. By default, the binding mode is off, meaning there is no automatic binding happening for incoming and outgoing messages.

You may want to use binding if you develop POJOs that maps to your REST services request and response types. This allows you as a developer to work with the POJOs in Java code.

The binding modes are:

Expand

| Binding Mode     | Description                                                                                                                                                                                                                                    |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` off ```      | Binding is turned off. This is the default option.                                                                                                                                                                                             |
| ``` auto ```     | Binding is enabled, and Camel is relaxed and supports JSON, XML or both if the necessary data formats are included in the classpath. Notice that if for example  ``` camel-jaxb ```  is not on the classpath, then XML binding is not enabled. |
| ``` json ```     | Binding to/from JSON is enabled, and requires a JSON capable data format on the classpath. By default, Camel will use  ``` jackson ```  as the data format.                                                                                    |
| ``` xm ```       | Binding to/from XML is enabled, and requires  ``` camel-jaxb ```  on the classpath.                                                                                                                                                            |
| ``` json_xml ``` | Binding to/from JSON and XML is enabled and requires both data formats to be on the classpath.                                                                                                                                                 |

Show more

When using camel-jaxb for XML bindings, then you can use the option `mustBeJAXBElement` to relax the output message body must be a class with JAXB annotations. You can use this in situations where the message body is already in XML format, and you want to use the message body as-is as the output type. If that is the case, then set the dataFormatProperty option `mustBeJAXBElement` to `false` value.

The binding from POJO to JSon/JAXB will only happen if the `content-type` header includes the word `json` or `xml` representatively. This allows you to specify a custom content-type if the message body should not attempt to be marshalled using the binding. For example, if the message body is a custom binary payload, and so on.

When automatic binding from POJO to JSON/JAXB takes place the existing `content-type` header will by default be replaced with either `application/json` or `application/xml` . To disable the default behavior and be able to produce JSON/JAXB responses with custom `content-type` headers (e.g. `application/user.v2+json` ) you configure this in Java DSL as shown below:

```
restConfiguration().dataFormatProperty("contentTypeHeader", "false");
```

Copy to Clipboard

Toggle word wrap

To use binding you must include the necessary data formats on the classpath, such as `camel-jaxb` and/or `camel-jackson` . And then enable the binding mode. You can configure the binding mode globally on the rest configuration, and then override per rest service as well.

To enable binding, you configure this in Java DSL as shown below:

```
restConfiguration().component("netty-http").host("localhost").port(portNum).bindingMode(RestBindingMode.auto);
```

Copy to Clipboard

Toggle word wrap

And in XML DSL:

```
<restConfiguration bindingMode="auto" component="netty-http" port="8080"/>
```

Copy to Clipboard

Toggle word wrap

When binding is enabled, Camel will bind the incoming and outgoing messages automatic, accordingly to the content type of the message. If the message is JSON, then JSON binding happens; and so if the message is XML, then XML binding happens. The binding happens for incoming and reply messages. The table below summaries what binding occurs for incoming and reply messages.

Expand

| Message Body   | Direction   | Binding Mode       | Message Body   |
|----------------|-------------|--------------------|----------------|
| XML            | Incoming    | auto,xml,json_xml  | POJO           |
| POJO           | Outgoing    | auto,xml, json_xml | XML            |
| JSON           | Incoming    | auto,json,json_xml | POJO           |
| POJO           | Outgoing    | auto,json,json_xml | JSON           |

Show more

When using binding, you must also configure what POJO type to map to. This is mandatory for incoming messages, and optional for outgoing.

Note

When using binding mode `json` , `xml` or `json_xml` then Camel will automatically set `consumers` and `produces` on the rest endpoint (according to the mode), if not already explicit configured. For example, with binding mode `json` and setting the outType as `UserPojo` then Camel will define this rest endpoint as producing `application/json` .

For example, to map from xml/json to a pojo class `UserPojo` you do this in Java DSL as shown below:

```
// configure to use netty-http on localhost with the given port
// and enable auto binding mode
restConfiguration().component("netty-http").host("localhost").port(portNum).bindingMode(RestBindingMode.auto);

// use the rest DSL to define the rest services
rest("/users/")
    .post().type(UserPojo.class)
        .to("direct:newUser");
```

Copy to Clipboard

Toggle word wrap

Notice we use `type` to define the incoming type. We can optionally define an outgoing type (which can be a good idea, to make it known from the DSL and also for tooling and JMX APIs to know both the incoming and outgoing types of the REST services). To define the outgoing type, we use `outType` as shown below:

```
// configure to use netty-http on localhost with the given port
// and enable auto binding mode
restConfiguration().component("netty-http").host("localhost").port(portNum).bindingMode(RestBindingMode.auto);

// use the rest DSL to define the rest services
rest("/users/")
    .post().type(UserPojo.class).outType(CountryPojo.class)
        .to("direct:newUser");
```

Copy to Clipboard

Toggle word wrap

And in XML DSL:

```
<rest path="/users/">
  <post type="UserPojo" outType="CountryPojo">
    <to uri="direct:newUser"/>
  </post>
</rest>
```

Copy to Clipboard

Toggle word wrap

To specify input and/or output using an array, append `[]` to the end of the canonical class name as shown in the following Java DSL:

```
// configure to use netty-http on localhost with the given port
// and enable auto binding mode
restConfiguration().component("netty-http").host("localhost").port(portNum).bindingMode(RestBindingMode.auto);

// use the rest DSL to define the rest services
rest("/users/")
    .post().type(UserPojo[].class).outType(CountryPojo[].class)
        .to("direct:newUser");
```

Copy to Clipboard

Toggle word wrap

The `UserPojo` is just a plain pojo with getter/setter as shown:

```
public class UserPojo {
    private int id;
    private String name;
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
```

Copy to Clipboard

Toggle word wrap

The `UserPojo` only supports JSON, as XML requires using JAXB annotations, so we can add those annotations if we want to support XML also

```
@XmlRootElement(name = "user")
@XmlAccessorType(XmlAccessType.FIELD)
public class UserPojo {
    @XmlAttribute
    private int id;
    @XmlAttribute
    private String name;
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
```

Copy to Clipboard

Toggle word wrap

By having the JAXB annotations, the POJO supports both JSON and XML bindings.

##### [3.3.7.1. Camel Rest-DSL configurations Copy link](#camel_rest_dsl_configurations)

The REST DSL supports the following options:

Expand

| Name                         | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Default                         | Type                 |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|----------------------|
| **apiComponent**             | Sets the name of the Camel component to use as the REST API (such as swagger or openapi)                                                                                                                                                                                                                                                                                                                                                                                                                                 |                                 | String               |
| **apiContextPath**           | Sets a leading API context-path the REST API services will be using. This can be used when using components such as camel-servlet where the deployed web application is deployed using a context-path.                                                                                                                                                                                                                                                                                                                   |                                 | String               |
| **apiHost**                  | To use a specific hostname for the API documentation (such as swagger or openapi) This can be used to override the generated host with this configured hostname                                                                                                                                                                                                                                                                                                                                                          |                                 | String               |
| **apiProperties**            | Sets additional options on api level                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |                                 | Map                  |
| **apiVendorExtension**       | Whether a vendor extension is enabled in the REST APIs. If enabled, then Camel will include additional information as a vendor extension (e.g., keys starting with  *``` x- ```*  ) such as route ids, class names , and so on. Not all third party API gateways and tools support vendor-extensions when importing your API docs.                                                                                                                                                                                       | false                           | boolean              |
| **bindingMode**              | Sets the binding mode to be used by the REST consumer                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | RestBindingMode.off             | RestBindingMode      |
| **clientRequestValidation**  | Whether to enable validation of the client request to check: 1) Content-Type header matches what the REST DSL consumes; returns HTTP Status 415 if validation error. 2) Accept header matches what the REST DSL produces; returns HTTP Status 406 if validation error. 3) Missing required data (query parameters, HTTP headers, body); returns HTTP Status 400 if validation error. 4) Parsing error of the message body (JSON, XML or Auto binding mode must be enabled); returns HTTP Status 400 if validation error. | false                           | boolean              |
| **clientResponseValidation** | Whether to check what Camel is returning as response to the client: 1) Status-code and Content-Type matches REST DSL response messages. 2) Check whether expected headers is included according to the REST DSL repose message headers. 3) If the response body is JSon then check whether its valid JSon. Returns 500 if validation error detected.                                                                                                                                                                     | false                           | boolean              |
| **component**                | Sets the name of the Camel component to use as the REST consumer                                                                                                                                                                                                                                                                                                                                                                                                                                                         |                                 | String               |
| **componentProperties**      | Sets additional options on component level                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |                                 | Map                  |
| **consumerProperties**       | Sets additional options on consumer level                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |                                 | Map                  |
| **contextPath**              | Sets a leading context-path the REST services will be using. This can be used when using components such as camel-servlet where the deployed web application is deployed using a context-path. Or for components such as camel-jetty or camel-netty-http that includes a HTTP server.                                                                                                                                                                                                                                    |                                 | String               |
| **corsHeaders**              | Sets the CORS headers to use if CORS has been enabled.                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |                                 | Map                  |
| **dataFormatProperties**     | Sets additional options on data format level                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |                                 | Map                  |
| **enableCORS**               | To specify whether to enable CORS, which means Camel will automatically include CORS in the HTTP headers in the response. This option is default false                                                                                                                                                                                                                                                                                                                                                                   | false                           | boolean              |
| **enableNoContentResponse**  | To specify whether to return HTTP 204 with an empty body when a response contains an empty JSON object or XML root object.                                                                                                                                                                                                                                                                                                                                                                                               | false                           | boolean              |
| **endpointProperties**       | Sets additional options on endpoint level                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |                                 | Map                  |
| **host**                     | Sets the hostname to use by the REST consumer                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |                                 | String               |
| **hostNameResolver**         | Sets the resolver to use for resolving hostname                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | RestHostNameResolver.allLocalIp | RestHostNameResolver |
| **inlineRoutes**             | Inline routes in rest-dsl which are linked using direct endpoints. By default, each service in REST DSL is an individual route, meaning that you would have at least two routes per service (rest-dsl, and the route linked from rest-dsl). Enabling this allows Camel to optimize and inline this as a single route. However, this requires using direct endpoints, which must be unique per service. This option is default false.                                                                                     | false                           | boolean              |
| **jsonDataFormat**           | Sets a custom JSON data format to be used Important: This option is only for setting a custom name of the data format, not to refer to an existing data format instance.                                                                                                                                                                                                                                                                                                                                                 |                                 | String               |
| **port**                     | Sets the port to use by the REST consumer                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |                                 | int                  |
| **producerApiDoc**           | Sets the location of the api document (swagger api) the REST producer will use to validate the REST uri and query parameters are valid accordingly to the api document. This requires adding camel-openapi-java to the classpath, and any miss configuration will let Camel fail on startup and report the error(s). The location of the api document is loaded from classpath by default, but you can use file: or http: to refer to resources to load from file or http url.                                           |                                 | String               |
| **producerComponent**        | Sets the name of the Camel component to use as the REST producer                                                                                                                                                                                                                                                                                                                                                                                                                                                         |                                 | String               |
| **scheme**                   | Sets the scheme to use by the REST consumer                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |                                 | String               |
| **skipBindingOnErrorCode**   | Whether to skip binding output if there is a custom HTTP error code, and instead use the response body as-is. This option is default true.                                                                                                                                                                                                                                                                                                                                                                               | true                            | boolean              |
| **useXForwardHeaders**       | Whether to use X-Forward headers to set host , and so on. for Swagger. This option is default true.                                                                                                                                                                                                                                                                                                                                                                                                                      | true                            | boolean              |
| **xmlDataFormat**            | Sets a custom XML data format to be used. Important: This option is only for setting a custom name of the data format, not to refer to an existing data format instance.                                                                                                                                                                                                                                                                                                                                                 |                                 | String               |

Show more

For example, to configure to use the jetty component on port 9091, then we can do as follows:

```
restConfiguration().component("jetty").port(9091).componentProperty("foo", "123");
```

Copy to Clipboard

Toggle word wrap

And with XML DSL:

```
<restConfiguration component="jetty" port="9091">
  <componentProperty key="foo" value="123"/>
</restConfiguration>
```

Copy to Clipboard

Toggle word wrap

If no component has been explicitly configured, then Camel will look up if there is a Camel component that integrates with the REST DSL, or if a `org.apache.camel.spi.RestConsumerFactory` is registered in the registry. If either one is found, then that is being used.

You can configure properties on these levels.

- component - Is used to set any options on the Component class. You can also configure these directly on the component.
- endpoint - Is used set any option on the endpoint level. Many of the Camel components has many options you can set on endpoint level.
- consumer - Is used to set any option on the consumer level.
- data format - Is used to set any option on the data formats. For example, to enable pretty print in the JSON data format.
- cors headers - If cors is enabled, then custom CORS headers can be set. See below for the default values which are in used. If a custom header is set then that value takes precedence over the default value.

You can set multiple options of the same level, so you can, for example, configure two component options, and three endpoint options, and so on.

#### [3.3.8. Enabling or disabling Jackson JSON features Copy link](#enabling_or_disabling_jackson_json_features)

When using JSON binding, you may want to turn specific Jackson features on or off. For example, to disable failing on unknown properties (e.g., JSON input has a property which cannot be mapped to a POJO) then configure this using the `dataFormatProperty` as shown below:

```
restConfiguration().component("jetty").host("localhost").port(getPort()).bindingMode(RestBindingMode.json)
   .dataFormatProperty("json.in.disableFeatures", "FAIL_ON_UNKNOWN_PROPERTIES");
```

Copy to Clipboard

Toggle word wrap

You can disable more features by separating the values using comma, such as:

```
.dataFormatProperty("json.in.disableFeatures", "FAIL_ON_UNKNOWN_PROPERTIES,ADJUST_DATES_TO_CONTEXT_TIME_ZONE");
```

Copy to Clipboard

Toggle word wrap

Likewise, you can enable features using the enableFeatures such as:

```
restConfiguration().component("jetty").host("localhost").port(getPort()).bindingMode(RestBindingMode.json)
   .dataFormatProperty("json.in.disableFeatures", "FAIL_ON_UNKNOWN_PROPERTIES,ADJUST_DATES_TO_CONTEXT_TIME_ZONE")
   .dataFormatProperty("json.in.enableFeatures", "FAIL_ON_NUMBERS_FOR_ENUMS,USE_BIG_DECIMAL_FOR_FLOATS");
```

Copy to Clipboard

Toggle word wrap

The values that can be used for enabling and disabling features on Jackson are the names of the enums from the following three Jackson classes

- com.fasterxml.jackson.databind.SerializationFeature
- com.fasterxml.jackson.databind.DeserializationFeature
- com.fasterxml.jackson.databind.MapperFeature

The rest configuration is, of course, also possible using XML DSL:

```
<restConfiguration component="jetty" host="localhost" port="9090" bindingMode="json">
  <dataFormatProperty key="json.in.disableFeatures" value="FAIL_ON_UNKNOWN_PROPERTIES,ADJUST_DATES_TO_CONTEXT_TIME_ZONE"/>
  <dataFormatProperty key="json.in.enableFeatures" value="FAIL_ON_NUMBERS_FOR_ENUMS,USE_BIG_DECIMAL_FOR_FLOATS"/>
</restConfiguration>
```

Copy to Clipboard

Toggle word wrap

#### [3.3.9. Default CORS headers Copy link](#default_cors_headers)

If CORS is enabled, then the *"follow headers"* is in use by default. You can configure custom CORS headers that take precedence over the default value.

Expand

| Key                                  | Value                                                                                                         |
|--------------------------------------|---------------------------------------------------------------------------------------------------------------|
| ``` Access-Control-Allow-Origin ```  | *                                                                                                             |
| ``` Access-Control-Allow-Methods ``` | GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH                                                  |
| ``` Access-Control-Allow-Headers ``` | Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers |
| ``` Access-Control-Max-Age ```       | 3600                                                                                                          |

Show more

#### [3.3.10. Defining a custom error message as-is Copy link](#defining_a_custom_error_message_as_is)

If you want to define custom error messages to be sent back to the client with a HTTP error code (e.g., such as 400, 404 , and so on.) then you set a header with the key `Exchange.HTTP_RESPONSE_CODE` to the error code (must be 300+) such as 404. And then the message body with any reply message, and optionally set the content-type header as well. There is a little example shown below:

```
restConfiguration().component("netty-http").host("localhost").port(portNum).bindingMode(RestBindingMode.json);
// use the rest DSL to define the rest services
rest("/users/")
    .post("lives").type(UserPojo.class).outType(CountryPojo.class)
    .to("direct:users-lives");

from("direct:users-lives")
    .choice()
        .when().simple("${body.id} < 100")
            .bean(new UserErrorService(), "idToLowError")
        .otherwise()
            .bean(new UserService(), "livesWhere");
```

Copy to Clipboard

Toggle word wrap

In this example, if the input id is a number that is below 100, we want to send back a custom error message, using the UserErrorService bean, which is implemented as shown:

```
public class UserErrorService {
    public void idToLowError(Exchange exchange) {
        exchange.getIn().setBody("id value is too low");
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
    }
}
```

Copy to Clipboard

Toggle word wrap

In the *`UserErrorService`* bean, we build our custom error message, and set the HTTP error code to 400. This is important, as that tells rest-dsl that this is a custom error message, and the message should not use the output pojo binding (e.g., would otherwise bind to *`CountryPojo`* ).

##### [3.3.10.1. Catching JsonParserException and returning a custom error message Copy link](#catching_jsonparserexception_and_returning_a_custom_error_message)

You can return a custom message as-is (see previous section). So we can leverage this with Camel error handler to catch `JsonParserException` , handle that exception and build our custom response message. For example, to return a HTTP error code 400 with a hardcoded message, we can do as shown below:

```
onException(JsonParseException.class)
    .handled(true)
    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
    .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
    .setBody().constant("Invalid json data");
```

Copy to Clipboard

Toggle word wrap

#### [3.3.11. Query/Header Parameter default Values Copy link](#queryheader_parameter_default_values)

You can specify default values for parameters in the rest-dsl, such as the verbose parameter below:

```
rest("/customers/")
      .get("/{id}").to("direct:customerDetail")
      .get("/{id}/orders")
        .param().name("verbose").type(RestParamType.query).defaultValue("false").description("Verbose order details").endParam()
          .to("direct:customerOrders")
      .post("/neworder").to("direct:customerNewOrder");
```

Copy to Clipboard

Toggle word wrap

The default value is automatic set as header on the incoming Camel `Message` . So if the call to `/customers/id/orders` do not include a query parameter with key `verbose` then Camel will now include a header with key `verbose` and the value `false` because it was declared as the default value. This functionality is only applicable for query parameters. Request headers may also be defaulted in the same way.

```
rest("/customers/")
      .get("/{id}").to("direct:customerDetail")
      .get("/{id}/orders")
        .param().name("indicator").type(RestParamType.header).defaultValue("disabled").description("Feature Enabled Indicator").endParam()
          .to("direct:customerOrders")
      .post("/neworder").to("direct:customerNewOrder");
```

Copy to Clipboard

Toggle word wrap

#### [3.3.12. Client Request and Response Validation Copy link](#client_request_and_response_validation)

It is possible to enable validation of the incoming client request. The validation checks for the following:

- Content-Type header matches what the REST DSL consumes. (Returns HTTP Status 415)
- Accept header matches what the REST DSL produces. (Returns HTTP Status 406)
- Missing required data (query parameters, HTTP headers, body). (Returns HTTP Status 400)
- Checking if query parameters or HTTP headers has not-allowed values. (Returns HTTP Status 400)
- Parsing error of the message body (JSON, XML or Auto binding mode must be enabled). (Returns HTTP Status 400)

If the validation fails, then REST DSL will return a response with an HTTP error code.

The validation is by default turned off (to be backwards compatible). It can be turned on via `clientRequestValidation` as shown below:

```
restConfiguration().component("jetty").host("localhost")
    .clientRequestValidation(true);
```

Copy to Clipboard

Toggle word wrap

The validator is pluggable and Camel provides a default implementation out of the box. However, the `camel-openapi-validator` uses the third party [Atlassian Swagger Request Validator](https://bitbucket.org/atlassian/swagger-request-validator/src/master/) library instead for client request validator. This library is a more extensive validator than the default validator from `camel-core` , such as being able to validate the payload is structured according to the OpenAPI specification.

In **Camel 4.13** we added a *response validator* as well which is intended more as development assistance that you can enable while building your Camel integrations, and help ensure what Camel is sending back to the HTTP client is valid. The response validator checks for the following:

- Status-code and Content-Type matches REST DSL response messages.
- Check whether expected headers is included according to the REST DSL repose message headers.
- If the response body is JSon then check whether its valid JSon.

If any error is detected the HTTP Status 500 is returned.

Also, the `camel-openapi-validator` can be added to the classpath to have a more powerful response validator, that can be used to validate the response payload is structured according to the OpenAPI specification.

#### [3.3.13. OpenAPI / Swagger API Copy link](#openapi_swagger_api)

The REST DSL supports OpenAPI and Swagger by the `camel-openapi-java` modules.

You can define each parameter fine-grained with details such as name, description, data type, parameter type and so on, using the `param` . For example, to define the id path parameter, you can do as shown below:

```
<!-- this is a rest GET to view an user by the given id -->
<get path="/{id}" outType="org.apache.camel.example.rest.User">
  <description>Find user by id</description>
  <param name="id" type="path" description="The id of the user to get" dataType="int"/>
  <to uri="bean:userService?method=getUser(${header.id})"/>
</get>
```

Copy to Clipboard

Toggle word wrap

And in Java DSL

```
.get("/{id}").description("Find user by id").outType(User.class)
    .param().name("id").type(path).description("The id of the user to get").dataType("int").endParam()
    .to("bean:userService?method=getUser(${header.id})")
```

Copy to Clipboard

Toggle word wrap

The body parameter type requires to use body as well for the name. For example, a REST PUT operation to create/update an user could be done as:

```
<!-- this is a rest PUT to create/update an user -->
<put type="org.apache.camel.example.rest.User">
  <description>Updates or create a user</description>
  <param name="body" type="body" description="The user to update or create"/>
  <to uri="bean:userService?method=updateUser"/>
</put>
```

Copy to Clipboard

Toggle word wrap

And in Java DSL:

```
.put().description("Updates or create a user").type(User.class)
    .param().name("body").type(body).description("The user to update or create").endParam()
    .to("bean:userService?method=updateUser")
```

Copy to Clipboard

Toggle word wrap

##### [3.3.13.1. Vendor Extensions Copy link](#vendor_extensions)

The generated API documentation can be configured to include vendor extensions ( [https://swagger.io/specification/#specificationExtensions](https://swagger.io/specification/#specificationExtensions) ) which document the operations and definitions with additional information, such as class name of model classes, camel context id and route id's. This information can be very helpful for developers, especially during troubleshooting. However, at production usage you may wish to not have this turned on to avoid leaking implementation details into your API docs.

The vendor extension information is stored in the API documentation with keys starting with `x-` .

Note

Not all third party API gateways and tools support vendor-extensions when importing your API docs.

The vendor extensions can be turned on `RestConfiguration` via the `apiVendorExtension` option:

```
restConfiguration()
    .component("servlet")
    .bindingMode(RestBindingMode.json)
    .dataFormatProperty("prettyPrint", "true")
    .apiContextPath("api-doc")
    .apiVendorExtension(true)
        .apiProperty("api.title", "User API").apiProperty("api.version", "1.0.0")
        .apiProperty("cors", "true");
```

Copy to Clipboard

Toggle word wrap

And in XML DSL:

```
<restConfiguration component="servlet" bindingMode="json"
                       apiContextPath="api-docs"
                       apiVendorExtension="true">

      <!-- we want json output in pretty mode -->
      <dataFormatProperty key="prettyPrint" value="true"/>

      <!-- setup swagger api descriptions -->
      <apiProperty key="api.version" value="1.0.0"/>
      <apiProperty key="api.title" value="User API"/>

</restConfiguration>
```

Copy to Clipboard

Toggle word wrap

##### [3.3.13.2. Supported API properties Copy link](#supported_api_properties)

The following table lists supported API properties and explains their effect. To set them use `apiProperty(String, String)` in the Java DSL or `<apiProperty>` when defining the REST API via XML configuration. Properties in **bold** are required by the OpenAPI 2.0 specification. Most of the properties affect the OpenAPI [Info object](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#infoObject) , [License object](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#licenseObject) or [Contact object](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#contact-object) .

Expand

| Property                           | Description                                                                                      |
|------------------------------------|--------------------------------------------------------------------------------------------------|
| **api.version**                    | Version of the API                                                                               |
| **api.title**                      | Title of the API                                                                                 |
| api.description                    | Description of the API                                                                           |
| api.termsOfService                 | API Terms of Service of the API                                                                  |
| api.license.name                   | License information of the API                                                                   |
| api.license.url                    | URL for the License of the API                                                                   |
| api.contact.name                   | The identifying name of the contact person/organization                                          |
| api.contact.url                    | The URL pointing to the contact information                                                      |
| api.contact.email                  | The email address of the contact person/organization                                             |
| api.specification.contentType.json | The Content-Type of the served OpenAPI JSON specification,  ``` application/json ```  by default |
| api.specification.contentType.yaml | The Content-Type of the served OpenAPI YAML specification,  ``` text/yaml ```  by default        |
| externalDocs.url                   | The URI for the target documentation. This must be in the form of a URI                          |
| externalDocs.description           | A description of the target documentation                                                        |

Show more

### [3.4. REST DSL with OpenAPI contract first Copy link](#camel-quarkus-extensions-rest-dsl-contract-first)

Note

This section describes a *contract-first* approach to working with REST DSL, using a vanilla OpenAPI specification.

For the legacy *code-first* approach, see the section [REST DSL with OpenAPI code first](#camel-quarkus-extensions-rest-dsl-code-first)

From **Camel 4.6** onwards, the [REST DSL](rest-dsl.xml) has been improved with a *contract-first* approach using vanilla OpenAPI specification.

#### [3.4.1. How it works Copy link](#how_it_works_2)

The REST DSL OpenAPI is a facade that builds [REST OpenAPI](https://rhaetor.github.io/rh-camel/components/4.10.x/rest-openapi-component.html) endpoint as consumer for Camel routes. The actual HTTP transport is leveraged by using the [Platform HTTP](https://rhaetor.github.io/rh-camel/components/4.10.x/platform-http-component.html) , which makes it plugin to Camel Spring Boot, Camel Quarkus or can run standalone with Camel Main.

##### [3.4.1.1. Limitations Copy link](#limitations)

Camel does not support websockets from the OpenAPI 3.1 specification. Neither is (at this time of writing) any security aspects from the OpenAPI specification in use.

#### [3.4.2. Contract first Copy link](#contract_first)

The *contract-first* approach requires you to have an existing OpenAPI v3 specification file. This contract is a standard OpenAPI contract, and you can use any existing API design tool to build such contracts.

Tip

Camel support OpenAPI v3.0 and v3.1.

In Camel, you then use the REST DSL in *contract-first* mode. For example, having a contract in a file named `my-contract.json` , you can then copy this file to `src/main/resources` so it's loaded from classpath.

In Camel REST DSL you can then very easily define *contract-first* as shown below:

Java

```
@Override
public void configure() throws Exception {
    rest().openApi("petstore-v3.json");
}
```

Copy to Clipboard

Toggle word wrap

XML

```
<rest>
  <openApi specification="petstore-v3.json"/>
</rest>
```

Copy to Clipboard

Toggle word wrap

YAML

```
- rest : openApi : specification : petstore - v3.json
```

Copy to Clipboard

Toggle word wrap

When Camel starts, the OpenAPI specification file is loaded and parsed. For every API, Camel builds HTTP REST endpoint, which are routed 1:1 to Camel routes using the `direct:operationId` naming convention.

The *pestore* has 18 APIs here we look at the 5 user APIs:

```
http://0.0.0.0:8080/api/v3/user                       (POST)   (accept:application/json,application/x-www-form-urlencoded,application/xml produce:application/json,application/xml)
 http://0.0.0.0:8080/api/v3/user/createWithList        (POST)   (accept:application/json produce:application/json,application/xml)
 http://0.0.0.0:8080/api/v3/user/login                 (GET)    (produce:application/json,application/xml)
 http://0.0.0.0:8080/api/v3/user/logout                (GET)
 http://0.0.0.0:8080/api/v3/user/{username}            (DELETE,GET,PUT)
```

Copy to Clipboard

Toggle word wrap

These APIs are outputted using the URI that clients can use to call the service. Each of these APIs has a unique *operation id* which is what Camel uses for calling the route. This gives:

```
http://0.0.0.0:8080/api/v3/user                       direct:createUser
 http://0.0.0.0:8080/api/v3/user/createWithList        direct:createUsersWithListInput
 http://0.0.0.0:8080/api/v3/user/login                 direct:loginUser
 http://0.0.0.0:8080/api/v3/user/logout                direct:logoutUser
 http://0.0.0.0:8080/api/v3/user/{username}            direct:getUserByName
```

Copy to Clipboard

Toggle word wrap

You should then implement a route for each API that starts from those direct endpoints listed above, such as:

Java

```
@Override
public void configure() throws Exception {
    rest().openApi("petstore-v3.json");

    from("direct:getUserByName")
       ... // do something here
}
```

Copy to Clipboard

Toggle word wrap

XML

```
<rest>
  <openApi specification="petstore-v3.json"/>
</rest>
<route>
  <from uri="direct:getUserByName"/>
  // do something here
</route>
```

Copy to Clipboard

Toggle word wrap

YAML

```
- rest : openApi : specification : petstore - v3.json - route : from : uri : direct : getUserByName steps : - log : message : "do something here"
```

Copy to Clipboard

Toggle word wrap

##### [3.4.2.1. Configuring Base Path Copy link](#configuring_base_path)

By default, Camel uses the base path specified in the OpenAPI contract such as:

```
"basePath": {
            "default": "/api/v3"
    }
```

Copy to Clipboard

Toggle word wrap

You can configure Camel to use a different base path (such as `cheese` ) by either setting the base-path on the REST OpenAPI component in `application.properties` , or configure it in the *rest configuration* YAML:

Properties

```
camel.component.rest-openapi.base-path = /cheese
```

Copy to Clipboard

Toggle word wrap

YAML

```
- restConfiguration : clientRequestValidation : true contextPath : /cheese - rest : openApi : specification : petstore - v3.json
```

Copy to Clipboard

Toggle word wrap

##### [3.4.2.2. Ignoring missing API operations Copy link](#ignoring_missing_api_operations)

When using OpenAPI with *contract-first* , then Camel will on startup check if there is a corresponding `direct:operationId` route for every API service. If some operations are missing, then Camel will fail on startup with an error.

During development, you can use `missingOperation` to ignore this as shown:

```
rest().openApi("petstore-v3.json").missingOperation("ignore");
```

Copy to Clipboard

Toggle word wrap

This allows you to implement the APIs one by one over time.

##### [3.4.2.3. Mocking API operations Copy link](#mocking_api_operations)

This is similar to ignoring missing API operations, as you can tell Camel to mock instead, as shown:

```
rest().openApi("petstore-v3.json").missingOperation("mock");
```

Copy to Clipboard

Toggle word wrap

When using *mock* , then Camel will (for missing operations) simulate a successful response:

1. attempting to load canned responses from the file system.
2. for GET verbs then attempt to use example inlined in the OpenAPI `response` section.
3. for other verbs (DELETE, PUT, POST, PATCH) then return the input body as response.
4. if none of the above, then return empty body.

This allows you to have a set of files that you can use for development and testing purposes.

The files should be stored in `camel-mock` when using Camel JBang, and `src/main/resources/camel-mock` for Maven/Gradle based projects.

For example, the following [Camel JBang example](https://github.com/apache/camel-kamelets-examples/tree/main/jbang/open-api-contract-first) is structured as:

```
README.md
camel-mock/pet/123.json
petstore-v3.json
petstore.camel.yaml
```

Copy to Clipboard

Toggle word wrap

And the Camel route:

```
- restConfiguration : clientRequestValidation : true
- rest : openApi : missingOperation : mock specification : petstore - v3.json
```

Copy to Clipboard

Toggle word wrap

When running this example, you can call the APIs and have an empty successful response. However, for the url `pet/123` the file `camel-mock/pet/123.json` will be loaded as the response as shown below:

```
$ curl http://0.0.0.0:8080/api/v3/pet/123 { "pet" : "donald the dock"
}
```

Copy to Clipboard

Toggle word wrap

If no file is found, then Camel will attempt to find an example from the *response* section in the OpenAPI specification.

In the response section below, then for success GET response (200) then for the `application/json` content-type, we have an inlined example. Note if there are multiple examples for the same content-type, then Camel will pick the first example, so make sure it's the best example you want to let Camel use as mocked response body.

```
"responses": {
    "200": {
        "description": "successful operation",
        "content": {
            "application/xml": {
                "schema": {
                    "$ref": "#/components/schemas/Pet"
                }
            },
            "application/json": {
                "schema": {
                    "$ref": "#/components/schemas/Pet"
                },
                "examples": {
                    "success": {
                        "summary": "A cat",
                        "value": "{\"pet\": \"Jack the cat\"}"
                    }
                }
            }
        }
    },
    "400": {
        "description": "Invalid ID supplied"
    },
    "404": {
        "description": "Pet not found"
    }
```

Copy to Clipboard

Toggle word wrap

##### [3.4.2.4. Binding to POJO classes Copy link](#binding_to_pojo_classes)

*contract-first* REST DSL with OpenAPI also supports binding mode to JSON and XML. This works the same as *code first* [REST DSL](rest-dsl.xml) .

However, we have added the `bindingPackageScan` configuration to make it possible for Camel to automatically discover POJO classes from classpath.

When using Spring Boot or Quarkus, then you must configure the package names (base) in Java or in `application.properties` :

Java

```
// turn on json binding and scan for POJO classes in the model package
restConfiguration().bindingMode(RestBindingMode.json)
        .bindingPackageScan("sample.petstore.model");
```

Copy to Clipboard

Toggle word wrap

Properties

```
camel.rest.bindingMode = json
camel.rest.bindingPackageScan = sample.petstore.model
```

Copy to Clipboard

Toggle word wrap

Then Camel will automatically for every OpenAPI operation detect the specified schemas for incoming and outgoing responses, and map that to Java POJO classes by class name.

For example, the `getPetById` operation in the OpenAPI contract:

```
"responses": {
    "200": {
        "description": "successful operation",
        "content": {
            "application/xml": {
                "schema": {
                    "$ref": "#/components/schemas/Pet"
                }
            },
            "application/json": {
                "schema": {
                    "$ref": "#/components/schemas/Pet"
                }
            }
        }
    },
```

Copy to Clipboard

Toggle word wrap

Here Camel will detect the `schema` part:

```
"schema": {
    "$ref": "#/components/schemas/Pet"
}
```

Copy to Clipboard

Toggle word wrap

And compute the class name as `Pet` and attempt to discover this class from classpath scanning specified via the `bindingPackageScan` option.

You can also use `title` attribute of the Schema to provide the name of the POJO class. This is helpful when you need to use one name for the Schema in the OpenAPI contract and use another name for the actual POJO class in the implementation.

```
"components": {
        "schemas": {
            "Pet": {
                "type": "object",
                "title": "PetResponseDto",
                "properties": {
                    ...
                }
            }
        }
    },
```

Copy to Clipboard

Toggle word wrap

Here Camel will detect the class name as `PetResponseDto` and try to discover it from the classpath. This can be used for both Responses and RequestBodies.

You can source code generate Java POJO classes from an OpenAPI specification via tooling such as the `swagger-codegen-maven-plugin` Maven plugin. For more details, see this [Spring Boot example](https://github.com/apache/camel-spring-boot-examples/tree/main/openapi-contract-first) .

##### [3.4.2.5. Expose API specification Copy link](#expose_api_specification)

The OpenAPI specification is by default not exposed on the HTTP endpoint. You can make this happen by setting the rest-configuration as follows:

```
- restConfiguration : apiContextPath : /api - doc
```

Copy to Clipboard

Toggle word wrap

Then the specification is accessible on `/api-doc` on the embedded HTTP server, so typically that would be [`http://localhost:8080/api-doc`](http://localhost:8080/api-doc) .

In the returned API specification the `server` section has been modified to return the IP of the current server. This can be controlled via:

```
- restConfiguration : apiContextPath : /api - doc hostNameResolver : localIp
```

Copy to Clipboard

Toggle word wrap

And you can turn this off by setting the value to `none` so the server part is taken verbatim from the specification file.

```
- restConfiguration : apiContextPath : /api - doc hostNameResolver : none
```

Copy to Clipboard

Toggle word wrap

#### [3.4.3. Examples Copy link](#examples)

You can find a few examples such as:

- [https://github.com/apache/camel-kamelets-examples/tree/main/jbang/open-api-contract-first](https://github.com/apache/camel-kamelets-examples/tree/main/jbang/open-api-contract-first)
- [https://github.com/apache/camel-spring-boot-examples/tree/main/openapi-contract-first](https://github.com/apache/camel-spring-boot-examples/tree/main/openapi-contract-first)

## [Chapter 4. Defining Camel routes Copy link](#camel-quarkus-extensions-routes)

In Red Hat build of Apache Camel for Quarkus, you can define Camel routes using the following languages:

- [Java DSL](#camel-quarkus-extensions-routes-java)
- [Endpoint DSL](#camel-quarkus-extensions-routes-endpoint)
- [XML IO DSL](#camel-quarkus-extensions-routes-xml-io)
- [YAML DSL](#camel-quarkus-extensions-routes-yaml)

### [4.1. Java DSL Copy link](#camel-quarkus-extensions-routes-java)

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

### [4.2. Endpoint DSL Copy link](#camel-quarkus-extensions-routes-endpoint)

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

### [4.3. XML IO DSL Copy link](#camel-quarkus-extensions-routes-xml-io)

In order to configure Camel routes, rests or templates in XML, you must add a Camel XML parser dependency to the classpath. Since Camel Quarkus 1.8.0, `link:https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/ #extensions-xml-io-dsl[camel-quarkus-xml-io-dsl]` is the best choice.

With Camel Main, you can set a property that points to the location of resources XML files such as routes, [REST DSL](https://camel.apache.org/manual/rest-dsl.html) and [Route templates](https://camel.apache.org/manual/route-template.html) :

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

**REST DSL**

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

### [4.4. YAML DSL Copy link](#camel-quarkus-extensions-routes-yaml)

To configure routes with YAML, you must add the `camel-quarkus-yaml-dsl` dependency to the classpath.

With Camel Main, you can set a property that points to the location of YAML files containing routes, [REST DSL](https://camel.apache.org/manual/rest-dsl.html) and [Route templates](https://camel.apache.org/manual/route-template.html) definitions:

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

**REST DSL**

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

## [Chapter 5. Testing routes in Camel Quarkus Copy link](#testing-routes-in-camel-quarkus)

### [5.1. Testing Camel Quarkus Extensions Copy link](#camel-quarkus-testing-guide)

Testing offers a good way to ensure Camel routes behave as expected over time. If you haven't already, read the Camel Quarkus user guide [First Steps](https://camel.apache.org/camel-quarkus/3.20.x/user-guide/first-steps.html) and the Quarkus documentation [Testing your application](https://quarkus.io/guides/getting-started-testing) section.

When it comes to testing a route in the context of Quarkus, the recommended approach is to write local integration tests. This has the advantage of covering both JVM and native mode.

In JVM mode, you can use the [`CamelTestSupport`](#cameltestsupport_style_of_testing) [style of testing](#cameltestsupport_style_of_testing) .

#### [5.1.1. Running in JVM mode Copy link](#running_in_jvm_mode)

In JVM mode, use the `@QuarkusTest` annotation to bootstrap Quarkus and start Camel routes *before* the `@Test` logic executes.

For example:

```
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MyTest {
    @Test
    public void test() {
        // Use any suitable code that sends test data to the route and then assert outcomes
        ...
    }
}
```

Copy to Clipboard

Toggle word wrap

Tip

You can find a sample implementation in the Camel Quarkus source:

- [MessageTest.java](https://github.com/apache/camel-quarkus/blob/main/integration-tests/bindy/src/test/java/org/apache/camel/quarkus/component/bindy/it/MessageTest.java)

#### [5.1.2. Running in native mode Copy link](#native-tests)

Note

Always test that your application works in native mode for all supported extensions.

You can reuse the test logic defined for JVM mode by inheriting the logic from the respective JVM mode class.

Add the `@QuarkusIntegrationTest` annotation to tell the Quarkus JUnit extension to compile the application under test to native image and start it before running the tests.

```
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class MyIT extends MyTest {
   ...
}
```

Copy to Clipboard

Toggle word wrap

Tip

You can find a sample implementation in the Camel Quarkus source:

- [MessageRecordIT.java](https://github.com/apache/camel-quarkus/blob/main/integration-tests/bindy/src/test/java/org/apache/camel/quarkus/component/bindy/it/MessageRecordIT.java)

#### [5.1.3. Differences between @QuarkusTest and @QuarkusIntegrationTest Copy link](#jvm-vs-native-tests)

A native executable does not need a JVM to run, and cannot run in a JVM, because it is native code, not bytecode.

There is no point in compiling tests to native code so they run using a traditional JVM.

This means that communication between tests and the application must go over the network (HTTP/REST, or any other protocol your application speaks), through watching filesystems (log files for example), or any other interprocess communication.

##### [5.1.3.1. @QuarkusTest in JVM mode Copy link](#quarkustest_in_jvm_mode)

In JVM mode, tests annotated with `@QuarkusTest` execute in the same JVM as the application under test.

This means you can use `@Inject` to add beans from the application into the test code.

You can also define new beans or even override the beans from the application using `@jakarta.enterprise.inject.Alternative` and `@jakarta.annotation.Priority` .

##### [5.1.3.2. @QuarkusIntegrationTest in native mode Copy link](#quarkusintegrationtest_in_native_mode)

In native mode, tests annotated with `@QuarkusIntegrationTest` execute in a JVM hosted in a process separate from the running native application.

An important consequence of this, is that all communication between the tests and the native application, must take one or more of the following forms:

- Network calls. Typically, HTTP or any other network protocol your application supports.
- Watching the filesystem for changes. (For example via Camel `file` endpoints.)
- Any other kind of interprocess communication.

`QuarkusIntegrationTest` provides additional features that are not available through `@QuarkusTest` :

- In JVM mode, you can launch and test the runnable application JAR produced by the Quarkus build.
- In native mode, you can launch and test the native application produced by the Quarkus build.
- If you add a container image to the build, a container starts, and tests execute against it.

For more information about `QuarkusIntegrationTest` , see the [Quarkus testing guide](https://quarkus.io/guides/getting-started-testing#quarkus-integration-test) .

#### [5.1.4. Testing with external services Copy link](#testing_with_external_services)

##### [5.1.4.1. Testcontainers Copy link](#testcontainers)

Sometimes your application needs to access some external resource, such as a messaging broker, a database, or other service.

If a container image is available for the service of interest, you can use [Testcontainers](https://www.testcontainers.org/) to start and configure the services during testing.

##### [5.1.4.1.1. Passing configuration data with QuarkusTestResourceLifecycleManager Copy link](#passing_configuration_data_with_quarkustestresourcelifecyclemanager)

For the application to work properly, it is often essential to pass the connection configuration data (host, port, user, password of the remote service) to the application before it starts.

In the Quarkus ecosystem, `QuarkusTestResourceLifecycleManager` serves this purpose.

You can start one or more Testcontainers in the `start()` method and return the connection configuration from the method in the form of a `Map` .

The entries of this map are then passed to the application in different ways depending on the mode:

- Native mode: a command line ( `-Dkey=value` )
- JVM Mode: a special MicroProfile configuration provider

Note

Command line and MicroProfile settings have a higher precedence than the settings in the `application.properties` file.

```
import java.util.Map;
import java.util.HashMap;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MyTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> myContainer;

    @Override
    public Map<String, String> start() {
        // Start the needed container(s)
        myContainer = new GenericContainer(DockerImageName.parse("my/image:1.0.0"))
                .withExposedPorts(1234)
                .waitingFor(Wait.forListeningPort());

        myContainer.start();

        // Pass the configuration to the application under test
        // You can also pass camel component property names / values to automatically configure Camel components
        return new HashMap<>() {{
                put("my-container.host", container.getHost());
                put("my-container.port", "" + container.getMappedPort(1234));
        }};
    }

    @Override
    public void stop() {
        // Stop the needed container(s)
        myContainer.stop();
        ...
    }
}
```

Copy to Clipboard

Toggle word wrap

Reference the defined test resource from the test classes with `@QuarkusTestResource` :

```
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(MyTestResource.class)
class MyTest {
   ...
}
```

Copy to Clipboard

Toggle word wrap

Tip

You can find a sample implementation in the Camel Quarkus source:

- [NatsTestResource.java](https://github.com/apache/camel-quarkus/blob/main/integration-tests/nats/src/test/java/org/apache/camel/quarkus/component/nats/it/NatsTestResource.java)

##### [5.1.4.2. WireMock Copy link](#wiremock)

Instead of having the tests connect to live endpoints, for example, if they are unavailable, unreliable, or expensive, you can stub HTTP interactions with third-party services &amp; APIs.

You can use [WireMock](https://wiremock.org/) for mocking &amp; recording HTTP interactions. It is used extensively throughout the Camel Quarkus test suite for various component extensions.

##### [5.1.4.2.1. Setting up WireMock Copy link](#setting_up_wiremock)

**Procedure**

1. Set up the WireMock server. Note Always configure the Camel component under test to pass any HTTP interactions through the WireMock proxy. You can achieve this by configuring a component property that determines the API endpoint URL. `import static com.github.tomakehurst.wiremock.client.WireMock.aResponse; import static com.github.tomakehurst.wiremock.client.WireMock.get; import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo; import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig; import java.util.HashMap; import java.util.Map; import com.github.tomakehurst.wiremock.WireMockServer; import io.quarkus.test.common.QuarkusTestResourceLifecycleManager; public class WireMockTestResource implements QuarkusTestResourceLifecycleManager { private WireMockServer server; @Override public Map<String, String> start() { // Setup & start the server server = new WireMockServer( wireMockConfig().dynamicPort() ); server.start(); // Stub an HTTP endpoint. WireMock also supports a record and playback mode // https://wiremock.org/docs/record-playback/ server.stubFor( get(urlEqualTo("/api/greeting")) .willReturn(aResponse() .withHeader("Content-Type", "application/json") .withBody("{\"message\": \"Hello World\"}"))); // Ensure the camel component API client passes requests through the WireMock proxy Map<String, String> conf = new HashMap<>(); conf.put("camel.component.foo.server-url", server.baseUrl()); return conf; } @Override public void stop() { if (server != null) { server.stop(); } } }` Copy to Clipboard Toggle word wrap
2. Ensure your test class has the `@QuarkusTestResource` annotation with the appropriate test resource class specified as the value. The WireMock server will be started before all tests are executed and will be shut down when all tests are finished.

```
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class MyTest {
   ...
}
```

Copy to Clipboard

Toggle word wrap

The WireMock server starts before all tests execute and shuts down when all tests finish.

Tip

You can find a sample implementation in the Camel Quarkus integration test source tree:

- [Geocoder](https://github.com/apache/camel-quarkus/tree/main/integration-tests/geocoder) .

#### [5.1.5. CamelTestSupport style of testing with CamelQuarkusTestSupport Copy link](#cameltestsupport_style_of_testing)

Since Camel Quarkus 2.13.0, you can use `CamelQuarkusTestSupport` for testing. It is a replacement for `CamelTestSupport` , which does not work well with Quarkus.

Important

`CamelQuarkusTestSupport` only works in JVM mode. If you need to test in native mode, then use one of the alternate test strategies described above.

##### [5.1.5.1. Testing with CamelQuarkusTestSupport in JVM mode Copy link](#testing_with_camelquarkustestsupport_in_jvm_mode)

Add the following dependency into your module (preferably in the `test` scope):

```
<dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

Copy to Clipboard

Toggle word wrap

You can use `CamelQuarkusTestSupport` in your test like this:

```
@QuarkusTest
@TestProfile(SimpleTest.class) //necessary only if "newly created" context is required for the test (worse performance)
public class SimpleTest extends CamelQuarkusTestSupport {
    ...
}
```

Copy to Clipboard

Toggle word wrap

##### [5.1.5.2. Customizing the CamelContext for testing Copy link](#customizing_the_camelcontext_for_testing)

You can customize the `CamelContext` for testing with [configuration profiles](https://quarkus.io/guides/config-reference#profiles) , CDI beans, observers, [mocks](https://quarkus.io/guides/getting-started-testing#mock-support) etc. You can also override the `createCamelContext` method and interact directly with the `CamelContext` .

Important

When using `createCamelContext` you **MUST NOT** instantiate and return a new `CamelContext` . Instead, invoke `super.createCamelContext()` and modify the returned `CamelContext` as needed. Failing to follow this rule will result in an exception being thrown.

```
@QuarkusTest
class SimpleTest extends CamelQuarkusTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // Must call super to get a handle on the application scoped CamelContext
        CamelContext context = super.createCamelContext();
        // Apply customizations
        context.setTracing(true);
        // Return the modified CamelContext
        return context;
    }
}
```

Copy to Clipboard

Toggle word wrap

##### [5.1.5.3. Configuring routes for testing Copy link](#configuring_routes_for_testing)

Any classes that extend `RouteBuilder` in your application will have their routes automatically added to the `CamelContext` . Similarly, any XML or YAML routes configured from `camel.main.routes-include-pattern` will also be loaded.

This may not always be desirable for your tests. You control which routes get loaded at test time with configuration properties:

- quarkus.camel.routes-discovery.include-patterns
- `quarkus.camel.routes-discovery.exclude-patterns` ,
- camel.main.routes-include-pattern
- `camel.main.routes-exclude-pattern` .

You can also define test specific routes per test class by overriding `createRouteBuilder` :

```
@QuarkusTest
class SimpleTest extends CamelQuarkusTestSupport {
    @Test
    void testGreeting() {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        mockEndpoint.assertIsSatisified();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .transform().simple("Hello ${body}")
                    .to("mock:result");
            }
        };
    }
}
```

Copy to Clipboard

Toggle word wrap

##### [5.1.5.4. CamelContext test lifecycle Copy link](#camelcontext_test_lifecycle)

One of the main differences in `CamelQuarkusTestSupport` compared to `CamelTestSupport` is how the `CamelContext` lifecycle is managed.

On Camel Quarkus, a single `CamelContext` is created for you automatically by the runtime. By default, this `CamelContext` is shared among all tests and remains started for the duration of the entire test suite execution.

This can potentially have some unintended side effects for your tests. If you need to have the `CamelContext` restarted between tests, then you can create a custom [test profile](https://quarkus.io/guides/getting-started-testing#testing_different_profiles) , which will force the application under test to be restarted.

For example, to define a test profile:

```
@QuarkusTest
class MyTestProfile implements QuarkusTestProfile {
    ...
}
```

Copy to Clipboard

Toggle word wrap

Then reference it on the test class with `@TestProfile` :

```
// @TestProfile will trigger the application to be restarted
@TestProfile(MyTestProfile.class)
@QuarkusTest
class SimpleTest extends CamelQuarkusTestSupport {
    ...
}
```

Copy to Clipboard

Toggle word wrap

Note

You cannot manually restart the `CamelContext` by invoking its `stop()` and `start()` methods. This will result in an exception.

##### [5.1.5.5. Examples Copy link](#examples_2)

##### [5.1.5.5.1. Simple RouteBuilder and test class Copy link](#simple_routebuilder_and_test_class)

Simple `RouteBuilder` :

```
public class MyRoutes extends RouteBuilder {
    @Override
    public void configure() {
        from("direct:start")
            .transform().simple("Hello ${body}")
            .to("mock:result");
    }
}
```

Copy to Clipboard

Toggle word wrap

Test sending a message payload to the `direct:start` endpoint:

```
@QuarkusTest
class SimpleTest extends CamelQuarkusTestSupport {
    @Test
    void testGreeting() {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        mockEndpoint.assertIsSatisified();
    }
}
```

Copy to Clipboard

Toggle word wrap

##### [5.1.5.5.2. Using AdviceWith Copy link](#using_advicewith)

```
@QuarkusTest
class SimpleTest extends CamelQuarkusTestSupport {
    @BeforeEach
    public void beforeEach() throws Exception {
        AdviceWith.adviceWith(this.context, "advisedRoute", route -> {
            route.replaceFromWith("direct:replaced");
        });
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("advisedRoute")
                    .transform().simple("Hello ${body}")
                    .to("mock:result");
            }
        };
    }

    @Test
    void testAdvisedRoute() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello World");

        template.sendBody("direct:replaced", "World");

        mockEndpoint.assertIsSatisfied();
    }
}
```

Copy to Clipboard

Toggle word wrap

##### [5.1.5.5.3. Explicitly enabling advice Copy link](#explicitly_enabling_advice)

When explicitly [enabling advice](https://camel.apache.org/manual/advice-with.html#_enabling_advice_during_testing) you must invoke `startRouteDefinitions` when completing your `AdviceWith` setup.

Note

Invoking `startRouteDefinitions` is only required if you have routes configured that are **NOT** being advised.

##### [5.1.5.6. Limitations Copy link](#limitations_2)

##### [5.1.5.6.1. Test lifecycle methods inherited from CamelTestSupport Copy link](#test_lifecycle_methods_inherited_from_cameltestsupport)

`CamelQuarkusTestSupport` inherits some test lifecycle methods from `CamelTestSupport` . However, they should not be used and instead are replaced with equivalent methods in `CamelQuarkusTestSupport` .

Expand

| CamelTestSupport lifecycle methods               | CamelQuarkusTestSupport equivalent   |
|--------------------------------------------------|--------------------------------------|
| ``` afterAll ```                                 | ``` doAfterAll ```                   |
| ``` afterEach ```  ,  ``` afterTestExecution ``` | ``` doAfterEach ```                  |
| ``` beforeAll ```                                | ``` doAfterConstruct ```             |
| ``` beforeEach ```                               | ``` doBeforeEach ```                 |

Show more

##### [5.1.5.6.2. Creating a custom Camel registry is not supported Copy link](#creating_a_custom_camel_registry_is_not_supported)

The `CamelQuarkusTestSupport` implementation of `createCamelRegistry` will throw `UnsupportedOperationException` .

If you need to bind or unbind objects to the Camel registry, then you can do it by one of the following methods.

- Produce named CDI beans `public class MyBeanProducers { @Produces @Named("myBean") public MyBean createMyBean() { return new MyBean(); } }` Copy to Clipboard Toggle word wrap
- Override `createCamelContext` (see example above) and invoke `camelContext.getRegistry().bind("foo", fooBean)`
- Use the `@BindToRegistry` annotation `@QuarkusTest class SimpleTest extends CamelQuarkusTestSupport { @BindToRegistry("myBean") MyBean myBean = new MyBean(); }` Copy to Clipboard Toggle word wrap Note Beans bound to the Camel registry from individual test classes, will persist for the duration of the test suite execution. This could have unintended consequences, depending on your test expectations. You can use test profiles to restart the `CamelContext` to avoid this.

## [Chapter 6. Configuration Copy link](#camel-quarkus-extensions-configuration)

Camel Quarkus automatically configures and deploys a Camel Context bean which by default is started/stopped according to the Quarkus Application lifecycle. The configuration step happens at build time during Quarkus' augmentation phase, and it is driven by the Camel Quarkus extensions which can be tuned using Camel Quarkus specific `quarkus.camel.*` properties.

Note

`quarkus.camel.*` configuration properties are documented on the individual extension pages - for example see [Camel Quarkus Core](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-core) .

After the configuration is done, a minimal Camel Runtime is assembled and started in the [RUNTIME\_INIT](https://quarkus.io/guides/writing-extensions#bootstrap-three-phases) phase.

### [6.1. Configuring Camel components Copy link](#configuring_camel_components)

#### [6.1.1. application.properties Copy link](#application_properties)

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

#### [6.1.2. CDI Copy link](#cdi)

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

##### [6.1.2.1. Producing a @Named component instance Copy link](#producing_a_named_component_instance)

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

### [6.2. Configuration by convention Copy link](#configuration_by_convention)

In addition to support configuring Camel through properties, `camel-quarkus-core` allows you to use conventions to configure the Camel behavior. For example, if there is a single `ExchangeFormatter` instance in the CDI container, then it will automatically wire that bean to the `LogComponent` .

**Additional resources**

- [Configuring and using Metering in OpenShift Container Platform](https://docs.redhat.com/en/documentation/openshift_container_platform/3.20.x/html/metering/index)

## [Chapter 7. Contexts and Dependency Injection (CDI) in Camel Quarkus Copy link](#camel-quarkus-extensions-cdi)

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

### [7.1. Accessing CamelContext Copy link](#accessing_camelcontext)

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

### [7.2. @EndpointInject and @Produce Copy link](#endpointinject_and_produce)

If you are used to `@org.apache.camel.EndpointInject` and `@org.apache.camel.Produce` from [plain Camel](https://camel.apache.org/manual/pojo-producing.html) or from Camel on SpringBoot, you can continue using them on Quarkus too.

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

### [7.3. CDI and the Camel Bean component Copy link](#cdi_and_the_camel_bean_component)

#### [7.3.1. Refer to a bean by name Copy link](#refer_to_a_bean_by_name)

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

We aim at supporting all use cases listed in [Bean binding](https://camel.apache.org/manual/bean-binding.html) section of Camel documentation. Do not hesitate to [file an issue](https://github.com/apache/camel-quarkus/issues) if some bean binding scenario does not work for you.

#### [7.3.2. @Consume Copy link](#consume)

Since Camel Quarkus 2.0.0, the `camel-quarkus-bean` artifact brings support for `@org.apache.camel.Consume` - see the [Pojo consuming](https://camel.apache.org/manual/pojo-consuming.html) section of Camel documentation.

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

## [Chapter 8. Observability Copy link](#camel-quarkus-extensions-observability)

### [8.1. Health &amp; liveness checks Copy link](#camel-quarkus-extensions-health)

Health &amp; liveness checks are supported via the MicroProfile Health extension. They can be configured via the [Camel Health](https://camel.apache.org/manual/health-check.html) API or via [Quarkus MicroProfile Health](https://quarkus.io/guides/microprofile-health) .

All configured checks are available on the standard MicroProfile Health endpoint URLs:

- [http://localhost:8080/q/health](http://localhost:8080/q/health)
- [http://localhost:8080/q/health/live](http://localhost:8080/q/health/live)
- [http://localhost:8080/q/health/ready](http://localhost:8080/q/health/ready)

#### [8.1.1. Health endpoint Copy link](#health_endpoint)

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

### [8.2. Metrics Copy link](#camel-quarkus-extensions-metrics)

We provide [MicroProfile Metrics](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-microprofile-metrics) for exposing metrics.

Some basic Camel metrics are provided for you out of the box, and these can be supplemented by configuring additional metrics in your routes.

Metrics are available on the standard Quarkus metrics endpoint:

- [http://localhost:8080/q/metrics](http://localhost:8080/q/metrics)

### [8.3. Monitoring a Camel application Copy link](#monitoring-ceq-application)

With monitoring of your applications, you can collect information about how your application behaves, such as metrics, health checks and distributed tracing.

Note

This section uses the `Observability` example listed in the [Red Hat build of Quarkus examples](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/observability) , adding observability with `micrometer` and `opentelemetry` .

Tip

Check the [Apache Camel extensions for Quarkus User Guide](https://camel.apache.org/camel-quarkus/3.20.x/user-guide/latest/first-steps.html) for prerequisites and other general information.

#### [8.3.1. Creating a project Copy link](#creating_a_project)

1. Start in the **Development** mode
2. Run the maven `compile` command: `$ mvn clean compile quarkus:dev` Copy to Clipboard Toggle word wrap This compiles the project, starts the application and lets the Quarkus tooling watch for changes in your workspace. Any modifications in your project automatically take effect in the running application. Tip Refer to the [Development mode](https://camel.apache.org/camel-quarkus/3.20.x/user-guide/first-steps.html#_development_mode) section of [Apache Camel extensions for Quarkus User Guide](https://camel.apache.org/camel-quarkus/3.20.x/user-guide/latest/first-steps.html) for more details.

#### [8.3.2. Enabling metrics Copy link](#enabling_metrics)

To enable observability features in Camel Quarkus, you must add additional dependencies to the project's pom.xml file. The most important ones are `camel-quarkus-opentelemetry` and `quarkus-micrometer-registry-prometheus` .

1. Add the dependencies to your project `pom.xml` : `<dependencies> ... <dependency> <groupId>org.apache.camel.quarkus</groupId> <artifactId>camel-quarkus-opentelemetry</artifactId> </dependency> <dependency> <groupId>io.quarkiverse.micrometer.registry</groupId> <artifactId>quarkus-micrometer-registry-prometheus</artifactId> </dependency> ... </dependencies>` Copy to Clipboard Toggle word wrap With these dependencies you benefit from both [Camel Micrometer](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-micrometer) and [Quarkus Micrometer](https://quarkus.io/guides/micrometer) .

#### [8.3.3. Creating meters Copy link](#creating_meters)

You can create meters for custom metrics in multiple ways:

- [Section 8.3.3.1, "Using Camel micrometer component"](#camel-micrometer-component)
- [Section 8.3.3.2, "Using CDI dependency injection"](#cdi-dependency-injection)
- [Section 8.3.3.3, "Using Micrometer annotations"](#micrometer-annotations)

##### [8.3.3.1. Using Camel micrometer component Copy link](#camel-micrometer-component)

With this method you use [Routes.java](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product//observability/src/main/java/org/acme/observability/Routes.java) .

```
.to("micrometer:counter:org.acme.observability.greeting-provider?tags=type=events,purpose=example")
```

Copy to Clipboard

Toggle word wrap

Which will count each call to the `platform-http:/greeting-provider` endpoint.

##### [8.3.3.2. Using CDI dependency injection Copy link](#cdi-dependency-injection)

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

##### [8.3.3.3. Using Micrometer annotations Copy link](#micrometer-annotations)

With this method you use [Micrometer annotations](https://quarkus.io/guides/micrometer#does-micrometer-support-annotations) , by defining a bean [`TimerCounter.java`](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product//observability/src/main/java/org/acme/observability/micrometer/TimerCounter.java) as follows:

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

It can then be invoked from Camel via the bean EIP (see [`TimerRoute.java`](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product//observability/src/main/java/org/acme/observability/TimerRoute.java) ):

```
.bean("timerCounter", "count")
```

Copy to Clipboard

Toggle word wrap

It will increment the counter metric each time the Camel timer is fired.

##### [8.3.3.4. Browsing metrics Copy link](#browsing_metrics)

Metrics are exposed on an HTTP endpoint at `/q/metrics` on port `9000` .

Note

Note we are using a different port (9000) for the management endpoint then our application (8080) is listening on. This is configured in `application.properties` via [`quarkus.management.enabled = true`](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product//observability/src/main/resources/application.properties#L22) .

See the [Quarkus management interface guide](https://quarkus.io/guides/management-interface-reference) for more information.

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

If you want to use JSON format instead of Prometheus, follow the [Quarkus Micrometer management interface configuration guide](https://quarkus.io/guides/micrometer#management-interface) .

#### [8.3.4. Tracing Copy link](#tracing)

To be able to diagnose problems in Camel Quarkus applications, you can trace messages. We will use OpenTelemetry standard suited for cloud environments.

You must add the dependencies `camel-quarkus-opentelemetry` and `quarkus-micrometer-registry-prometheus` to your project `pom.xml` :

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

Configure the OpenTelemetry exporter in `application.properties` , and remove the disabled tracing option:

```
# We are using a property placeholder to be able to test this example in convenient way in a cloud environment
quarkus.otel.exporter.otlp.traces.endpoint = http://${TELEMETRY_COLLECTOR_COLLECTOR_SERVICE_HOST:localhost}:4317
# To enable tracing (turn off the disabled property)
quarkus.otel.sdk.disabled=false
```

Copy to Clipboard

Toggle word wrap

Note

For information about other OpenTelemetry exporters, refer to the [OpenTelemetry extension documentation](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-opentelemetry-usage-exporters) .

To view tracing events, start a tracing server. A simple way of doing this is with Docker Compose:

```
$ docker-compose up -d
```

Copy to Clipboard

Toggle word wrap

With the server running, browse to [http://localhost:16686](http://localhost:16686/) . Then choose 'camel-quarkus-observability' from the 'Service' drop down and click the 'Find Traces' button.

The `platform-http` consumer route introduces a random delay to simulate latency, hence the overall time of each trace should be different. When viewing a trace, you should see a hierarchy of 6 spans showing the progression of the message exchange through each endpoint.

#### [8.3.5. Packaging and running the application Copy link](#packaging_and_running_the_application)

Once you are done with developing you can package and run the application.

Tip

For more details about the JVM mode and Native mode, refer to the [Package and run](https://camel.apache.org/camel-quarkus/3.20.x/user-guide/first-steps.html#_development_mode) section of [Apache Camel extensions for Quarkus User Guide](https://camel.apache.org/camel-quarkus/3.20.x/user-guide/latest/first-steps.html) .

##### [8.3.5.1. JVM mode Copy link](#jvm_mode)

```
$ mvn clean package
$ java -jar target/quarkus-app/quarkus-run.jar
...
[io.quarkus] (main) camel-quarkus-examples-... started in 1.163s. Listening on: http://0.0.0.0:8080
```

Copy to Clipboard

Toggle word wrap

##### [8.3.5.2. Native mode Copy link](#native_mode)

Important

Native mode requires having GraalVM and other tools installed.

Refer to the [Prerequisites](https://camel.apache.org/camel-quarkus/3.20.x/user-guide/first-steps.htmlfirst-steps.html#_prerequisites) section of the Apache Camel extensions for Quarkus User Guide for details.

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

## [Chapter 9. Native mode Copy link](#camel-quarkus-native-mode)

For additional information about compiling and testing application in native mode, see [Producing a native executable](https://access.redhat.com/documentation/en-us/red_hat_build_of_quarkus/quarkus-2-7/guide/c9fdb950-554d-427d-aa49-cc3da15ae860#proc_producing-native-executable_quarkus-building-native-executable) in the *Compiling your Quarkus applications to native executables* guide.

### [9.1. Character encodings Copy link](#charsets)

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

### [9.2. Locale Copy link](#locale)

By default, only the building JVM default locale is included in the native image. Quarkus provides a way to set the locale via `application.properties` , so that you do not need to rely on `LANG` and `LC_*` environement variables:

```
quarkus.native.user-country=US
quarkus.native.user-language=en
```

Copy to Clipboard

Toggle word wrap

There is also support for embedding multiple locales into the native image and for selecting the default locale via Mandrel command line options `-H:IncludeLocales=fr,en` , `H:+IncludeAllLocales` and `-H:DefaultLocale=de` . You can set those via the Quarkus `quarkus.native.additional-build-args` property.

### [9.3. Embedding resources in the native executable Copy link](#embedding-resource-in-native-executable)

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

Refer to [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) Reference for more details.

### [9.4. Using the onException clause in native mode Copy link](#using-onexception-clause-in-native-mode)

When using [Camel](https://camel.apache.org/manual/exception-clause.html) [`onException`](https://camel.apache.org/manual/exception-clause.html) [handling](https://camel.apache.org/manual/exception-clause.html) in native mode, it is your responsibility to register the exception classes for reflection.

For instance, having a camel context with `onException` handling:

```
onException(MyException.class).handled(true);
from("direct:route-that-could-produce-my-exception").throw(MyException.class);
```

Copy to Clipboard

Toggle word wrap

The class `mypackage.MyException` should be registered for reflection. For more information, see [Registering classes for reflection](#camel-quarkus-native-mode-reflection) .

### [9.5. Registering classes for reflection Copy link](#camel-quarkus-native-mode-reflection)

By default, dynamic reflection is not available in native mode. Classes for which reflective access is needed, have to be registered for reflection at compile time.

In many cases, application developers do not need to care because Quarkus extensions are able to detect the classes that require the reflection and register them automatically.

However, in some situations, Quarkus extensions may miss some classes and it is up to the application developer to register them. There are two ways to do that:

1. The [`@io.quarkus.runtime.annotations.RegisterForReflection`](https://quarkus.io/guides/writing-native-applications-tips#alternative-with-registerforreflection) annotation can be used to register classes on which it is used, or it can also register third party classes via its `targets` attribute. `import io.quarkus.runtime.annotations.RegisterForReflection; @RegisterForReflection class MyClassAccessedReflectively { } @RegisterForReflection( targets = { org.third-party.Class1.class, org.third-party.Class2.class } ) class ReflectionRegistrations { }` Copy to Clipboard Toggle word wrap
2. The `quarkus.camel.native.reflection` options in `application.properties` : `quarkus.camel.native.reflection.include-patterns = org.apache.commons.lang3.tuple.* quarkus.camel.native.reflection.exclude-patterns = org.apache.commons.lang3.tuple.*Triple` Copy to Clipboard Toggle word wrap For these options to work properly, the artifacts containing the selected classes must either contain a Jandex index ('META-INF/jandex.idx') or they must be registered for indexing using the 'quarkus.index-dependency.*' options in 'application.properties' - for example: `quarkus.index-dependency.commons-lang3.group-id = org.apache.commons quarkus.index-dependency.commons-lang3.artifact-id = commons-lang3` Copy to Clipboard Toggle word wrap

### [9.6. Registering classes for serialization Copy link](#serialization)

If serialization support is requested via `quarkus.camel.native.reflection.serialization-enabled` , the classes listed in [CamelSerializationProcessor.BASE\_SERIALIZATION\_CLASSES](https://github.com/apache/camel-quarkus/blob/main/extensions-core/core/deployment/src/main/java/org/apache/camel/quarkus/core/deployment/CamelSerializationProcessor.java) are automatically registered for serialization.

You can register more classes using `@RegisterForReflection(serialization = true)` .

## [Chapter 10. Kubernetes Copy link](#camel-quarkus-extensions-reference-kubernetes)

This guide describes different ways to configure and deploy a Camel Quarkus application on kubernetes. It also describes some specific use cases for Knative and Service Binding.

### [10.1. Kubernetes Copy link](#kubernetes)

Quarkus supports generating resources for vanilla Kubernetes, OpenShift and Knative. Furthermore, Quarkus can deploy the application to a target Kubernetes cluster by applying the generated manifests to the target cluster's API Server. For more information, see the [`Quarkus Kubernetes guide`](https://quarkus.io/guides/deploying-to-kubernetes) .

### [10.2. Knative Copy link](#knative)

The Camel Quarkus extensions whose consumers support Knative deployment are:

- [camel-quarkus-cxf-soap](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-cxf-soap)
- [camel-quarkus-grpc](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-grpc)
- [camel-quarkus-netty-http](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#netty-http)
- [camel-quarkus-platform-http](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-platform-http)
- [camel-quarkus-rest](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-rest)
- [camel-quarkus-servlet](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-servlet)
- [camel-quarkus-vertx-websocket](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-vertx-websocket)

### [10.3. Service binding Copy link](#service_binding)

Quarkus also supports the [Service Binding Specification for Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes#service_binding) to bind services to applications.

The following Camel Quarkus extensions can be used with Service Binding:

- [camel-quarkus-kafka](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-kafka)

## [Chapter 11. Quarkus CXF security guide Copy link](#quarkus-cxf-reference-intro-quarkus-cxf-security-guide)

This chapter provides information about security when working with Quarkus CXF extensions.

### [11.1. Security guide Copy link](#security-guide-index-quarkus-cxf-security-guide)

The security guide documents various security related aspects of Quarkus CXF:

- [SSL, TLS and HTTPS](#ssl-tls-https)
- [Authentication and authorization](#authentication-authorization)
- [Authentication enforced by WS-SecurityPolicy](#ws-securitypolicy-authentication-authorization)

#### [11.1.1. SSL, TLS and HTTPS Copy link](#ssl-tls-https)

This section documents various use cases related to SSL, TLS and HTTPS.

Note

The sample code snippets used in this section come from the [WS-SecurityPolicy integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy) in the source tree of Quarkus CXF

##### [11.1.1.1. Client SSL configuration Copy link](#client_ssl_configuration)

If your client is going to communicate with a server whose SSL certificate is not trusted by the client's operating system, then you need to set up a custom trust store for your client.

Tools like `openssl` or Java `keytool` are commonly used for creating and maintaining truststores.

We have examples for both tools in the Quarkus CXF source tree:

- [Create truststore with Java 'keytool' (wrapped by a Maven plugin)](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy/pom.xml#L185-L520)
- [Create truststore with](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy/generate-certs.sh) [`openssl`](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy/generate-certs.sh)

Once you have prepared the trust store, you need to configure your client to use it.

##### [11.1.1.1.1. Set the client trust store in application.properties Copy link](#set_the_client_trust_store_in_application_properties)

This is the easiest way to set the client trust store. The key role is played by the following properties:

- [quarkus.cxf.client."client-name".trust-store](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus-cxf_quarkus-cxf-client-client-name-trust-store)
- [quarkus.cxf.client."client-name".trust-store-type](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus-cxf_quarkus-cxf-client-client-name-trust-store-type)
- [quarkus.cxf.client."client-name".trust-store-password](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus-cxf_quarkus-cxf-client-client-name-trust-store-password)

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

##### [11.1.1.2. Server SSL configuration Copy link](#server_ssl_configuration)

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

##### [11.1.1.3. Mutual TLS (mTLS) authentication Copy link](#mtls-quarkus-cxf-security-guide)

So far, we have explained the simple or single-sided case where only the server proves its identity through an SSL certificate and the client has to be set up to trust that certificate. Mutual TLS authentication goes by letting also the client prove its identity using the same means of public key cryptography.

Hence, for the Mutual TLS (mTLS) authentication, in addition to setting up the server keystore and client truststore as described above, you need to set up the keystore on the client side and the truststore on the server side.

The tools for creating and maintaining the stores are the same and the configuration properties to use are pretty much analogous to the ones used in the Simple TLS case.

The [mTLS integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/mtls) in the Quarkus CXF source tree can serve as a good starting point.

The keystores and truststores are created with [`openssl`](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/mtls/generate-certs.sh) (or alternatively with Java [Java](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/mtls/pom.xml#L140-L408) [`keytool`](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/mtls/pom.xml#L140-L408) )

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

##### [11.1.1.4. Enforce SSL through WS-SecurityPolicy Copy link](#enforce_ssl_through_ws_securitypolicy)

The requirement for the clients to connect through HTTPS can be defined in a policy.

The functionality is provided by [`quarkus-cxf-rt-ws-security`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#-rt-ws-security) extension.

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

#### [11.1.2. Authentication and authorization Copy link](#authentication-authorization)

Note

The sample code snippets shown in this section come from the [Client and server integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/client-server) in the source tree of Quarkus CXF. You may want to use it as a runnable example.

##### [11.1.2.1. Client HTTP basic authentication Copy link](#client-http-basic-authentication)

Use the following client configuration options provided by [`quarkus-cxf`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#) extension to pass the username and password for HTTP basic authentication:

- [quarkus.cxf.client."client-name".username](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus-cxf_quarkus-cxf-client-client-name-username)
- [quarkus.cxf.client."client-name".password](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus-cxf_quarkus-cxf-client-client-name-password)

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

##### [11.1.2.1.1. Accessing WSDL protected by basic authentication Copy link](#accessing_wsdl_protected_by_basic_authentication)

By default, the clients created by Quarkus CXF do not send the `Authorization` header, unless you set the [`quarkus.cxf.client."client-name".secure-wsdl-access`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus-cxf_quarkus-cxf-client-client-name-secure-wsdl-access) to `true` :

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

##### [11.1.2.2. Mutual TLS (mTLS) authentication Copy link](#mutual_tls_mtls_authentication)

See the [Mutual TLS (mTLS) authentication](#mtls-quarkus-cxf-security-guide) section in SSL, TLS and HTTPS guide.

##### [11.1.2.3. Securing service endpoints Copy link](#securing-service-endpoints-quarkus-cxf-security-guide)

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

#### [11.1.3. Authentication enforced by WS-SecurityPolicy Copy link](#ws-securitypolicy-authentication-authorization)

You can enforce authentication through WS-SecurityPolicy, instead of [Mutual TLS](#mtls-quarkus-cxf-security-guide) and Basic HTTP authentication for [clients](#client-http-basic-authentication) and [services](#securing-service-endpoints-quarkus-cxf-security-guide) .

To enforce authentication through WS-SecurityPolicy, follow these steps:

1. Add a supporting tokens policy to an endpoint in the WSDL contract.
2. On the server side, implement an authentication callback handler and associate it with the endpoint in `application.properties` or via environment variables. Credentials received from clients are authenticated by the callback handler.
3. On the client side, provide credentials through either configuration in `application.properties` or environment variables. Alternatively, you can implement an authentication callback handler to pass the credentials.

##### [11.1.3.1. Specifying an Authentication Policy Copy link](#Auth-Policy)

If you want to enforce authentication on a service endpoint, associate a *supporting tokens* policy assertion with the relevant endpoint binding and specify one or more *token assertions* under it.

There are several different kinds of supporting tokens policy assertions, whose XML element names all end with `SupportingTokens` (for example, `SupportingTokens` , `SignedSupportingTokens` , and so on). For a complete list, see the [Supporting Tokens](https://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/ws-securitypolicy-1.2-spec-os.html#_Toc161826561) section of the WS-SecurityPolicy specification.

##### [11.1.3.2. UsernameToken policy assertion example Copy link](#usernametoken_policy_assertion_example)

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
- Include the policy [in your WSDL contract](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L163) and reference it via [`PolicyReference`](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L95) [element](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L95) .

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

In the above listing, `usernameTokenPasswordCallback` is a name of a `@jakarta.inject.Named` bean implementing `javax.security.auth.callback.CallbackHandler` . Quarkus CXF will lookup a bean with this [name](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#beanRefs) in the CDI container.

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

##### [11.1.3.3. SAML v1 and v2 policy assertion examples Copy link](#saml_v1_and_v2_policy_assertion_examples)

The [WS-SecurityPolicy integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy) contains also analogous examples with SAML v1 and SAML v2 assertions.

## [Chapter 12. Camel Security Copy link](#camel-security)

This chapter provides information about Camel route security options.

### [12.1. Camel security overview Copy link](#camel-security-overview)

Camel offers several forms &amp; levels of security capabilities that can be utilized on Camel routes. These various forms of security may be used in conjunction with each other or separately.

The broad categories offered are:

- *Route Security* - Authentication and Authorization services to proceed on a route or route segment
- *Payload Security* - Data Formats that offer encryption/decryption services at the payload level
- *Endpoint Security* - Security offered by components that can be utilized by endpointUri associated with the component
- *Configuration Security* - Security offered by encrypting sensitive information from configuration files or external Secured Vault systems.

Camel offers the [JSSE Utility](https://camel.apache.org/manual/camel-configuration-utilities.html) for configuring SSL/TLS related aspects of a number of Camel components.

### [12.2. Route Security Copy link](#route_security)

Authentication and Authorization Services

Camel offers [Route Policy](https://camel.apache.org/manual/route-policy.html) driven security capabilities that may be wired into routes or route segments. A route policy in Camel utilizes a strategy pattern for applying interceptors on Camel Processors. It's offering the ability to apply cross-cutting concerns (for example. security, transactions etc) of a Camel route.

### [12.3. Payload Security Copy link](#payload_security)

Camel offers encryption/decryption services to secure payloads or selectively apply encryption/decryption capabilities on portions/sections of a payload.

The dataformats offering encryption/decryption of payloads utilizing [Marshal](https://rhaetor.github.io/rh-camel/components/4.10.x//eips/marshal-eip.html) are:

- [Crypto](https://rhaetor.github.io/rh-camel/components/4.10.x//dataformats/crypto-dataformat.html)
- [PGP](https://rhaetor.github.io/rh-camel/components/4.10.x//dataformats/pgp-dataformat.html)

### [12.4. Endpoint Security Copy link](#endpoint_security)

Some components in Camel offer an ability to secure their endpoints (using interceptors etc) and therefore ensure that they offer the ability to secure payloads as well as provide authentication/authorization capabilities at endpoints created using the components.

### [12.5. Configuration Security Copy link](#configuration_security)

Camel offers the [Properties](https://rhaetor.github.io/rh-camel/components/4.10.x//properties-component.html) component to externalize configuration values to properties files. Those values could contain sensitive information such as usernames and passwords.

Those values can be encrypted and automatic decrypted by Camel using:

- [Jasypt](https://rhaetor.github.io/rh-camel/components/4.10.x/others/jasypt.html)

Camel also support accessing the secured configuration from an external vault systems.

#### [12.5.1. Configuration Security using Vaults Copy link](#configuration_security_using_vaults)

The following *Vaults* are supported by Camel:

- [AWS Secrets Manager](https://rhaetor.github.io/rh-camel/components/4.10.x//aws-secrets-manager-component.html)
- [Google Secret Manager](https://rhaetor.github.io/rh-camel/components/4.10.x//google-secret-manager-component.html)
- [Azure Key Vault](https://rhaetor.github.io/rh-camel/components/4.10.x//azure-key-vault-component.html)
- [Hashicorp Vault](https://rhaetor.github.io/rh-camel/components/4.10.x//hashicorp-vault-component.html)

##### [12.5.1.1. Using AWS Vault Copy link](#using_aws_vault)

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

##### [12.5.1.2. Using Google Secret Manager GCP Vault Copy link](#using_google_secret_manager_gcp_vault)

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

##### [12.5.1.3. Using Azure Key Vault Copy link](#using_azure_key_vault)

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

##### [12.5.1.4. Using Hashicorp Vault Copy link](#using_hashicorp_vault)

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

##### [12.5.1.5. Automatic Camel context reloading on Secret Refresh while using AWS Secrets Manager Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_aws_secrets_manager)

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

##### [12.5.1.6. Automatic Camel context reloading on Secret Refresh while using AWS Secrets Manager with Eventbridge and AWS SQS Services Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_aws_secrets_manager_with_eventbridge_and_aws_sqs_services)

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

##### [12.5.1.7. Automatic Camel context reloading on Secret Refresh while using Google Secret Manager Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_google_secret_manager)

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

##### [12.5.1.8. Automatic Camel context reloading on Secret Refresh while using Azure Key Vault Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_azure_key_vault)

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

## [Legal Notice Copy link](#idm139685894188080)

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