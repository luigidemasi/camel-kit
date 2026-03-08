## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Getting Started with Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm140334151173632)
3. [1. Red Hat build of Apache Camel for Quarkus overview](#camel-quarkus-overview_cq-getting-started)
4. [2. Access integrated open source capabilities](#camel-integrations)
5. 3. Tooling
6. 4. Getting Started with Red Hat build of Apache Camel for Quarkus
7. [5. Deploying Quarkus applications](#deploying-quarkus-applications)
8. 6. Testing
9. 7. Setting up Maven locally
10. 8. Sample applications
11. [Legal Notice](#idm140334142968112)

Format Multi-page Single-page View full doc as PDF

# Getting Started with Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.10

## Getting Started with Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm140334142968112)

**Abstract**

This guide introduces Red Hat build of Apache Camel for Quarkus and explains the various ways to create and deploy an application using Red Hat build of Apache Camel for Quarkus.

## [Preface Copy link](#idm140334151173632)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. To create a ticket, click this link: [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Red Hat build of Apache Camel for Quarkus overview Copy link](#camel-quarkus-overview_cq-getting-started)

Red Hat build of Apache Camel for Quarkus brings the integration capabilities of Apache Camel and its vast component library to the Quarkus runtime.

The benefits of using Red Hat build of Apache Camel for Quarkus include the following:

- Enables users to take advantage of the performance benefits, developer joy and the container first ethos which Quarkus provides.
- Provides Quarkus extensions for many of the Apache Camel components.
- Takes advantage of the many performance improvements made in Camel, which results in a lower memory footprint, less reliance on reflection and faster startup times.

## [Chapter 2. Access integrated open source capabilities Copy link](#camel-integrations)

Red Hat build of Apache Camel is certified and supported in a large variety of environments, combining the best of open source integration projects into a powerful, enterprise-ready toolkit, designed to simplify and accelerate cloud-native integration for modern businesses.

These integrations include:

- **Apache Camel integration framework** which implements enterprise integration patterns and offers hundreds of prebuilt components and connectors.
- **Kaoto visual designer** for Apache Camel.
- **HawtIO modular web console** for troubleshooting and remote management of integrations.
- **Apache CXF** for developing and consuming Simple Object Access Protocol (SOAP) web services.
- **Camel CLI** for iterative integration prototyping.
- **VS Code development tools** for code assistance and debugging.
- **Extra Camel components** requiring licensed libraries.
- **Camel golden path templates** for Backstage.
- **Monitoring and tracing** via Prometheus and OpenTelemetry.
- **Narayana** transaction manager.
- **Quarkus and Spring Boot** runtimes.
- **Member of Quarkus Platform** with simultaneous security updates.

## [Chapter 3. Tooling Copy link](#tooling-camel-quarkus-extensions_cq-getting-started)

### [3.1. Languages Copy link](#camel-extensions-tooling)

In Red Hat build of Apache Camel for Quarkus, you can define Camel routes using the following languages:

- Java DSL
- YAML
- XML IO

### [3.2. HawtIO Diagnostic Console Copy link](#hawtio_diagnostic_console)

HawtIO Diagnostic Console is a pluggable Web diagnostic console for Red Hat build of Apache Camel built with modern Web technologies such as React and PatternFly. HawtIO provides a central interface to examine and manage the details of one or more deployed HawtIO-enabled containers, depending on your enabled plugins. You can monitor HawtIO and system resources, perform updates, and start or stop services.

For more information, see the [Setting up Quarkus applications for HawtIO Online with Jolokia](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html/hawtio_diagnostic_console_guide/setting-applications-for-hawtio-online) section in the [HawtIO Diagnostic Console](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html/hawtio_diagnostic_console_guide/index) documentation

### [3.3. Kaoto Copy link](#kaoto)

Kaoto (Kamel Orchestration Tool) is a low and no code integration designer based on Apache Camel that allows you to create and edit integrations. Kaoto is extendable, flexible, and adaptable to different use cases.

For more information, see the [Creating Camel routes](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html/kaoto/getting-started-with-kaoto#creating-camel-routes-kaoto) section in the [Kaoto](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html/kaoto/index) documentation.

### [3.4. Camel CLI Copy link](#camel_cli)

Camel CLI is a Camel application based on JBang that you can use to create and run Camel routes.

For more information, see the [Creating and running Camel routes](https://docs.redhat.com/en/documentation/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/4.10/html-single/tooling_guide_for_red_hat_build_of_apache_camel/camel-cli-cq) section in the [Red Hat build of Apache Camel for Quarkus Tooling Guide](https://docs.redhat.com/en/documentation/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/4.10/html-single/tooling_guide_for_red_hat_build_of_apache_camel/) documentation.

### [3.5. IDE plugins Copy link](#ide_plugins)

You can install plugins with language support, code/configuration completion, project creation wizards and much more. The plugins are available at each respective IDE marketplace.

Note

Not all of these plugins are provided by Red Hat, and some of them are not currently supported, but are offered here as options for consideration. Check the plugin documentation for information about how you can create projects for your preferred IDE.

#### [3.5.1. Supported Copy link](#supported)

- VS Code:

#### [3.5.2. Not supported Copy link](#not_supported)

- VS Code:
- Eclipse:
- Jetbrains:

### [3.6. Camel content assist Copy link](#camel_content_assist)

The following plugins provide support for content assist when editing Camel routes and `application.properties` :

- Eclipse:
- JetBrains:
- VS Code:
- Other:

Tip

For more information about Tooling in Red Hat build of Apache Camel, see [Tooling Guide](https://docs.redhat.com/en/documentation/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/4.10/html-single/%7Bproduct-section-tooling-guide%7D/index) .

For more information about scope of development support, see [Development Support Scope of Coverage](https://access.redhat.com/support/offerings/developer/soc) in the Red Hat Support Portal (requires login).

## [Chapter 4. Getting Started with Red Hat build of Apache Camel for Quarkus Copy link](#getting-started-with-camel-quarkus-extensions_camel-quarkus-extensions-getting-started)

This guide introduces Red Hat build of Apache Camel for Quarkus, the various ways to create a project and how to get started building an application using Red Hat build of Apache Camel for Quarkus:

### [4.1. Red Hat build of Apache Camel for Quarkus overview Copy link](#camel-quarkus-overview_camel-quarkus-extensions-getting-started)

Red Hat build of Apache Camel for Quarkus brings the integration capabilities of Apache Camel and its vast component library to the Quarkus runtime.

The benefits of using Red Hat build of Apache Camel for Quarkus include the following:

- Enables users to take advantage of the performance benefits, developer joy and the container first ethos which Quarkus provides.
- Provides Quarkus extensions for many of the Apache Camel components.
- Takes advantage of the many performance improvements made in Camel, which results in a lower memory footprint, less reliance on reflection and faster startup times.

### [4.2. Building your first project with Red Hat build of Apache Camel for Quarkus Copy link](#building-your-first-project-with-camel-extensions-for-quarkus)

#### [4.2.1. Overview Copy link](#overview)

You can use [code.quarkus.redhat.com](https://code.quarkus.redhat.com/) to generate a Quarkus Maven project which automatically adds and configures the extensions that you want to use in your application.

This section walks you through the process of creating a Quarkus Maven project with Red Hat build of Apache Camel for Quarkus including:

- Creating the skeleton application using [code.quarkus.redhat.com](https://code.quarkus.redhat.com/)
- Adding a simple Camel route
- Exploring the application code
- Compiling the application in development mode
- Testing the application

#### [4.2.2. Generating the skeleton application with code.quarkus.redhat.com Copy link](#generating_the_skeleton_application_with_code_quarkus_redhat_com)

You can bootstrap and generate projects on [code.quarkus.redhat.com](https://code.quarkus.redhat.com/) .

The Red Hat build of Apache Camel for Quarkus extensions are located under the 'Integration' heading.

If you need additional extensions, use the 'search' field to find them.

Select the component extensions that you want to work with and click 'Generate your application' to download a basic skeleton project.

You can also push the project directly to GitHub.

For more information about using `code.quarkus.redhat.com` to generate Quarkus Maven projects, see [Creating a Quarkus Maven project using code.quarkus.redhat.com](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.20/html-single/getting_started_with_red_hat_build_of_quarkus/index#proc-creating-quarkus-project-using-code-quarkus-redhat-com_quarkus-getting-started) in the *Getting started with Red Hat build of Quarkus* guide.

**Procedure**

1. In the [code.quarkus.redhat.com](https://code.quarkus.redhat.com/) website, select the following extensions:
2. Navigate to the directory where you extracted the generated project files from the previous step: `$ cd <directory_name>` Copy to Clipboard Toggle word wrap

#### [4.2.3. Explore the application code Copy link](#explore_the_application_code)

The application has two compile dependencies which are managed within the `com.redhat.quarkus.platform:quarkus-camel-bom` that is imported in `<dependencyManagement>` .:

**pom.xml**

```
<quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
<quarkus.platform.group-id>com.redhat.quarkus.platform</quarkus.platform.group-id>
<quarkus.platform.version>
    <!-- The latest 3.20.x version from https://maven.repository.redhat.com/ga/com/redhat/quarkus/platform/quarkus-bom -->
</quarkus.platform.version>

...

<dependency>
    <groupId>${quarkus.platform.group-id}</groupId>
    <artifactId>${quarkus.platform.artifact-id}</artifactId>
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
```

Copy to Clipboard

Toggle word wrap

Note

For more information about BOM dependency management, see [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/4.10/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)

The application is configured by properties defined within `src/main/resources/application.properties` , for example, the `camel.context.name` can be set there.

#### [4.2.4. Adding a simple Camel route Copy link](#adding_a_simple_camel_route)

Note

In this example, we use the simple example from the [camel-quarkus-examples](https://github.com/apache/camel-quarkus-examples/blob/main/rest-json/src/main/java/org/acme/rest/json/) repository. It consists of the two simple classes `Fruit.java` , `Legume.java` and the route definitions `Routes.java` .

**Procedure**

1. Create a file named `Fruit.java` in the `src/main/java/org/acme/` subfolder.
2. Add the class as shown in the following code snippet: **Fruit.java** `package org.acme.rest.json; import java.util.Objects; import io.quarkus.runtime.annotations.RegisterForReflection; /** * A REST entity representing a fruit. */ @RegisterForReflection // Lets Quarkus register this class for reflection during the native build public class Fruit { private String name; private String description; public Fruit() { } public Fruit(String name, String description) { this.name = name; this.description = description; } public String getName() { return name; } public void setName(String name) { this.name = name; } public String getDescription() { return description; } public void setDescription(String description) { this.description = description; } @Override public boolean equals(Object obj) { if (!(obj instanceof Fruit)) { return false; } Fruit other = (Fruit) obj; return Objects.equals(other.name, this.name); } @Override public int hashCode() { return Objects.hash(this.name); } }` Copy to Clipboard Toggle word wrap
3. Create a file named `Legume.java` in the `src/main/java/org/acme/` subfolder.
4. Add the class as shown in the following code snippet: **Legume.java** `package org.acme.rest.json; import java.util.Objects; import io.quarkus.runtime.annotations.RegisterForReflection; /** * A REST entity representing a legume. */ @RegisterForReflection // Lets Quarkus register this class for reflection during the native build public class Legume { private String name; private String description; public Legume() { } public Legume(String name, String description) { this.name = name; this.description = description; } public String getName() { return name; } public void setName(String name) { this.name = name; } public String getDescription() { return description; } public void setDescription(String description) { this.description = description; } @Override public boolean equals(Object obj) { if (!(obj instanceof Legume)) { return false; } Legume other = (Legume) obj; return Objects.equals(other.name, this.name); } @Override public int hashCode() { return Objects.hash(this.name); } }` Copy to Clipboard Toggle word wrap
5. Create a file named `Routes.java` in the `src/main/java/org/acme/` subfolder.
6. Add a Camel Rest route as shown in the following code snippet: **Routes.java** `package org.acme.rest.json; import java.util.Collections; import java.util.LinkedHashSet; import java.util.Set; import org.apache.camel.builder.RouteBuilder; import org.apache.camel.model.rest.RestBindingMode; /** * Camel route definitions. */ public class Routes extends RouteBuilder { private final Set<Fruit> fruits = Collections.synchronizedSet(new LinkedHashSet<>()); private final Set<Legume> legumes = Collections.synchronizedSet(new LinkedHashSet<>()); public Routes() { /* Let's add some initial fruits */ this.fruits.add(new Fruit("Apple", "Winter fruit")); this.fruits.add(new Fruit("Pineapple", "Tropical fruit")); /* Let's add some initial legumes */ this.legumes.add(new Legume("Carrot", "Root vegetable, usually orange")); this.legumes.add(new Legume("Zucchini", "Summer squash")); } @Override public void configure() throws Exception { restConfiguration().bindingMode(RestBindingMode.json); rest("/fruits") .get() .to("direct:getFruits") .post() .type(Fruit.class) .to("direct:addFruit"); rest("/legumes") .get() .to("direct:getLegumes"); from("direct:getFruits") .setBody().constant(fruits); from("direct:addFruit") .process().body(Fruit.class, fruits::add) .setBody().constant(fruits); from("direct:getLegumes") .setBody().constant(legumes); } }` Copy to Clipboard Toggle word wrap For more information about this example, see [camel-quarkus-examples repository](https://github.com/apache/camel-quarkus-examples/blob/main/rest-json/src/main/java/org/acme/rest/json/Routes.java) .

#### [4.2.5. Development mode Copy link](#development_mode)

```
$ mvn clean compile quarkus:dev
```

Copy to Clipboard

Toggle word wrap

This command compiles the project, starts your application, and lets the Quarkus tooling watch for changes in your workspace. Any modifications you make to your project will automatically take effect in the running application.

You can check the application in your browser. (For example, for the `rest-json` sample application, access [`http://localhost:8080/fruits`](http://localhost:8080/fruits) )

If you change the application code, for example, change 'Apple' to 'Orange', your application automatically updates. To see the changes applied, refresh your browser.

Refer to Quarkus documentation [Development mode](https://quarkus.io/guides/maven-tooling#dev-mode) section for more details about the development mode.

#### [4.2.6. Packaging and running the application Copy link](#packaging-your-first-camel-extensions-for-quarkus-project)

##### [4.2.6.1. JVM mode Copy link](#jvm_mode)

**Procedure**

1. Run `mvn package` to prepare a thin `jar` for running on a stock JVM: `$ mvn clean package $ ls -lh target/quarkus-app ... -rw-r--r--. 1 user user 238K Oct 11 18:55 quarkus-run.jar ...` Copy to Clipboard Toggle word wrap Note The thin `jar` contains just the application code. You also need the dependencies in `target/quarkus-app/lib` to run it.
2. Run the jar as follows: `$ java -jar target/quarkus-app/quarkus-run.jar ... [io.quarkus] (main) Quarkus started in 1.163s. Listening on: http://[::]:8080` Copy to Clipboard Toggle word wrap

Note

The boot time should be around a second.

##### [4.2.6.2. Native mode Copy link](#native_mode)

**Procedure**

To prepare a native executable, do as follows:

1. Run the command `mvn clean package -Pnative` : `$ mvn clean package -Pnative $ ls -lh target ... -rwxr-xr-x. 1 user user 46M Oct 11 18:57 code-with-quarkus-1.0.0-SNAPSHOT-runner ...` Copy to Clipboard Toggle word wrap Note The `runner` has no `.jar` extension and has the `x` (executable) permission set. You can run it directly: `$ ./target/*-runner ... [io.quarkus] (main) Quarkus started in 0.013s. Listening on: http://[::]:8080 ...` Copy to Clipboard Toggle word wrap The application started in 13 milliseconds.
2. View the memory usage with the `ps -o rss,command -p $(pgrep code-with)` command : `$ ps -o rss,command -p $(pgrep code-with) RSS COMMAND 65852 ./target/code-with-quarkus-1.0.0-SNAPSHOT-runner` Copy to Clipboard Toggle word wrap The application uses 65 MB of memory.

Tip

See [Producing a native executable](https://access.redhat.com/documentation/en-us/red_hat_build_of_quarkus/quarkus-2-13/guide/c9fdb950-554d-427d-aa49-cc3da15ae860#_a6ed2e58-5517-45ee-825c-ce3e7c40763c) in the *Compiling your Quarkus applications to native executables* guide for additional information about preparing a native executable.

Tip

[Quarkus Native executable guide](https://quarkus.io/guides/building-native-image-guide.html) contains more details, including [steps for creating a container image](https://quarkus.io/guides/building-native-image#creating-a-container) .

## [Chapter 5. Deploying Quarkus applications Copy link](#deploying-quarkus-applications)

You can deploy your Quarkus application on OpenShift by using any of the following build strategies:

- Docker build
- S2I Binary
- Source S2I

For more details about each of these build strategies, see the [*OpenShift Container Platform build strategies and Red Hat build of Quarkus*](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.20/html/deploying_your_red_hat_build_of_quarkus_applications_to_openshift_container_platform#ref_openshift-build-strategies-and-quarkus_quarkus-openshift) of the *Deploying your Quarkus applications to OpenShift Container Platform* guide.

Note

The OpenShift Docker build strategy is the preferred build strategy that supports Quarkus applications targeted for JVM as well as Quarkus applications compiled to native executables. You can configure the deployment strategy using the `quarkus.openshift.build-strategy` property.

## [Chapter 6. Testing Copy link](#testing-camel-quarkus-extensions_cq-getting-started)

### [6.1. Testing Camel Quarkus Extensions Copy link](#camel-quarkus-testing-guide)

Testing offers a good way to ensure Camel routes behave as expected over time. If you haven't already, read the Camel Quarkus user guide [First Steps](https://camel.apache.org/camel-quarkus/3.20.x/user-guide/first-steps.html) and the Quarkus documentation [Testing your application](https://quarkus.io/guides/getting-started-testing) section.

When it comes to testing a route in the context of Quarkus, the recommended approach is to write local integration tests. This has the advantage of covering both JVM and native mode.

In JVM mode, you can use the [`CamelTestSupport`](#cameltestsupport_style_of_testing) [style of testing](#cameltestsupport_style_of_testing) .

#### [6.1.1. Running in JVM mode Copy link](#running_in_jvm_mode)

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

#### [6.1.2. Running in native mode Copy link](#native-tests)

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

#### [6.1.3. Differences between @QuarkusTest and @QuarkusIntegrationTest Copy link](#jvm-vs-native-tests)

A native executable does not need a JVM to run, and cannot run in a JVM, because it is native code, not bytecode.

There is no point in compiling tests to native code so they run using a traditional JVM.

This means that communication between tests and the application must go over the network (HTTP/REST, or any other protocol your application speaks), through watching filesystems (log files for example), or any other interprocess communication.

##### [6.1.3.1. @QuarkusTest in JVM mode Copy link](#quarkustest_in_jvm_mode)

In JVM mode, tests annotated with `@QuarkusTest` execute in the same JVM as the application under test.

This means you can use `@Inject` to add beans from the application into the test code.

You can also define new beans or even override the beans from the application using `@jakarta.enterprise.inject.Alternative` and `@jakarta.annotation.Priority` .

##### [6.1.3.2. @QuarkusIntegrationTest in native mode Copy link](#quarkusintegrationtest_in_native_mode)

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

#### [6.1.4. Testing with external services Copy link](#testing_with_external_services)

##### [6.1.4.1. Testcontainers Copy link](#testcontainers)

Sometimes your application needs to access some external resource, such as a messaging broker, a database, or other service.

If a container image is available for the service of interest, you can use [Testcontainers](https://www.testcontainers.org/) to start and configure the services during testing.

##### [6.1.4.1.1. Passing configuration data with QuarkusTestResourceLifecycleManager Copy link](#passing_configuration_data_with_quarkustestresourcelifecyclemanager)

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

##### [6.1.4.2. WireMock Copy link](#wiremock)

Instead of having the tests connect to live endpoints, for example, if they are unavailable, unreliable, or expensive, you can stub HTTP interactions with third-party services &amp; APIs.

You can use [WireMock](https://wiremock.org/) for mocking &amp; recording HTTP interactions. It is used extensively throughout the Camel Quarkus test suite for various component extensions.

##### [6.1.4.2.1. Setting up WireMock Copy link](#setting_up_wiremock)

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

#### [6.1.5. CamelTestSupport style of testing with CamelQuarkusTestSupport Copy link](#cameltestsupport_style_of_testing)

Since Camel Quarkus 2.13.0, you can use `CamelQuarkusTestSupport` for testing. It is a replacement for `CamelTestSupport` , which does not work well with Quarkus.

Important

`CamelQuarkusTestSupport` only works in JVM mode. If you need to test in native mode, then use one of the alternate test strategies described above.

##### [6.1.5.1. Testing with CamelQuarkusTestSupport in JVM mode Copy link](#testing_with_camelquarkustestsupport_in_jvm_mode)

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

##### [6.1.5.2. Customizing the CamelContext for testing Copy link](#customizing_the_camelcontext_for_testing)

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

##### [6.1.5.3. Configuring routes for testing Copy link](#configuring_routes_for_testing)

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

##### [6.1.5.4. CamelContext test lifecycle Copy link](#camelcontext_test_lifecycle)

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

##### [6.1.5.5. Examples Copy link](#examples)

##### [6.1.5.5.1. Simple RouteBuilder and test class Copy link](#simple_routebuilder_and_test_class)

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

##### [6.1.5.5.2. Using AdviceWith Copy link](#using_advicewith)

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

##### [6.1.5.5.3. Explicitly enabling advice Copy link](#explicitly_enabling_advice)

When explicitly [enabling advice](https://camel.apache.org/manual/advice-with.html#_enabling_advice_during_testing) you must invoke `startRouteDefinitions` when completing your `AdviceWith` setup.

Note

Invoking `startRouteDefinitions` is only required if you have routes configured that are **NOT** being advised.

##### [6.1.5.6. Limitations Copy link](#limitations)

##### [6.1.5.6.1. Test lifecycle methods inherited from CamelTestSupport Copy link](#test_lifecycle_methods_inherited_from_cameltestsupport)

`CamelQuarkusTestSupport` inherits some test lifecycle methods from `CamelTestSupport` . However, they should not be used and instead are replaced with equivalent methods in `CamelQuarkusTestSupport` .

Expand

| CamelTestSupport lifecycle methods               | CamelQuarkusTestSupport equivalent   |
|--------------------------------------------------|--------------------------------------|
| ``` afterAll ```                                 | ``` doAfterAll ```                   |
| ``` afterEach ```  ,  ``` afterTestExecution ``` | ``` doAfterEach ```                  |
| ``` beforeAll ```                                | ``` doAfterConstruct ```             |
| ``` beforeEach ```                               | ``` doBeforeEach ```                 |

Show more

##### [6.1.5.6.2. Creating a custom Camel registry is not supported Copy link](#creating_a_custom_camel_registry_is_not_supported)

The `CamelQuarkusTestSupport` implementation of `createCamelRegistry` will throw `UnsupportedOperationException` .

If you need to bind or unbind objects to the Camel registry, then you can do it by one of the following methods.

- Produce named CDI beans `public class MyBeanProducers { @Produces @Named("myBean") public MyBean createMyBean() { return new MyBean(); } }` Copy to Clipboard Toggle word wrap
- Override `createCamelContext` (see example above) and invoke `camelContext.getRegistry().bind("foo", fooBean)`
- Use the `@BindToRegistry` annotation `@QuarkusTest class SimpleTest extends CamelQuarkusTestSupport { @BindToRegistry("myBean") MyBean myBean = new MyBean(); }` Copy to Clipboard Toggle word wrap Note Beans bound to the Camel registry from individual test classes, will persist for the duration of the test suite execution. This could have unintended consequences, depending on your test expectations. You can use test profiles to restart the `CamelContext` to avoid this.

## [Chapter 7. Setting up Maven locally Copy link](#set-up-maven-locally)

Typical Red Hat build of Apache Camel application development uses Maven to build and manage projects.

### [7.1. Preparing to set up Maven Copy link](#prepare-to-set-up-maven)

Maven is a free, open source, build tool from Apache. Typically, you use Maven to build Fuse applications.

**Procedure**

1. Download Maven 3.9.9 or later from the [Maven download page](http://maven.apache.org/download.html) . Tip To verify that you have the correct Maven and JDK version installed, open a command terminal and enter the following command: `mvn --version` Copy to Clipboard Toggle word wrap Check the output to verify that Maven is version 3.9.9 or newer, and is using OpenJDK 17.
2. Ensure that your system is connected to the Internet. While building a project, the default behavior is that Maven searches external repositories and downloads the required artifacts. Maven looks for repositories that are accessible over the Internet. You can change this behavior so that Maven searches only repositories that are on a local network. That is, Maven can run in an offline mode. In offline mode, Maven looks for artifacts in its local repository. See [Section 7.3, "Using local Maven repositories"](#use-local-maven-repositories) .

### [7.2. Adding Red Hat repositories to Maven Copy link](#add-red-hat-repositories-to-maven)

To access artifacts that are in Red Hat Maven repositories, you need to add those repositories to Maven's `settings.xml` file.

Maven looks for the `settings.xml` file in the `.m2` directory of the user's home directory. If there is not a user specified `settings.xml` file, Maven uses the system-level `settings.xml` file at `M2_HOME/conf/settings.xml` .

**Prerequisite**

You know the location of the `settings.xml` file in which you want to add the Red Hat repositories.

**Procedure**

In the `settings.xml` file, add `repository` elements for the Red Hat repositories as shown in this example:

Note

If you are using the `camel-jira` component, also add the atlassian repository.

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
            <id>atlassian</id>
            <url>https://packages.atlassian.com/maven-external/</url>
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

### [7.3. Using local Maven repositories Copy link](#use-local-maven-repositories)

If you are running a container without an Internet connection, and you need to deploy an application that has dependencies that are not available offline, you can use the Maven dependency plug-in to download the application's dependencies into a Maven offline repository. You can then distribute this customized Maven offline repository to machines that do not have an Internet connection.

**Procedure**

1. In the project directory that contains the `pom.xml` file, download a repository for a Maven project by running a command such as the following: `mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.0:go-offline -Dmaven.repo.local=/tmp/my-project` Copy to Clipboard Toggle word wrap In this example, Maven dependencies and plug-ins that are required to build the project are downloaded to the `/tmp/my-project` directory.
2. Distribute this customized Maven offline repository internally to any machines that do not have an Internet connection.

### [7.4. Setting Maven mirror using environmental variables or system properties Copy link](#set-maven-mirror-url)

When running the applications you need access to the artifacts that are in the Red Hat Maven repositories. These repositories are added to Maven's `settings.xml` file. Maven checks the following locations for `settings.xml` file:

- looks for the specified url
- if not found looks for `${user.home}/.m2/settings.xml`
- if not found looks for `${maven.home}/conf/settings.xml`
- if not found looks for `${M2_HOME}/conf/settings.xml`
- if no location is found, empty `org.apache.maven.settings.Settings` instance is created.

#### [7.4.1. About Maven mirror Copy link](#maven-mirror)

Maven uses a set of remote repositories to access the artifacts, which are currently not available in local repository. The list of repositories almost always contains Maven Central repository, but for Red Hat Fuse, it also contains Maven Red Hat repositories. In some cases where it is not possible or allowed to access different remote repositories, you can use a mechanism of Maven mirrors. A mirror replaces a particular repository URL with a different one, so all HTTP traffic when remote artifacts are being searched for can be directed to a single URL.

#### [7.4.2. Adding Maven mirror to settings.xml Copy link](#add-maven-mirror-url-settings-xml)

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

#### [7.4.3. Setting Maven mirror using environmental variable or system property Copy link](#set-maven-mirror-url-using-env-variables)

To set the Maven mirror using either environmental variable or system property, you can add:

- Environmental variable called **MAVEN\_MIRROR\_URL** to `bin/setenv` file
- System property called **mavenMirrorUrl** to `etc/system.properties` file

#### [7.4.4. Using Maven options to specify Maven mirror url Copy link](#set-maven-mirror-url-using-maven-options)

To use an alternate Maven mirror url, other than the one specified by environmental variables or system property, use the following maven options when running the application:

- `-DmavenMirrorUrl=mirrorId::mirrorUrl` for example, `-DmavenMirrorUrl=my-mirror::http://mirror.net/repository`
- `-DmavenMirrorUrl=mirrorUrl` for example, `-DmavenMirrorUrl=http://mirror.net/repository` . In this example, the &lt;id&gt; of the &lt;mirror&gt; is just a mirror.

### [7.5. About Maven artifacts and coordinates Copy link](#about-maven-coordinates)

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

## [Chapter 8. Sample applications Copy link](#camel-quarkus-examples_cq-examples)

### [8.1. Red Hat build of Quarkus Examples Copy link](#camel-quarkus-examples)

The [Red Hat build of Quarkus examples](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/) repository contains a number of examples on how to integrate with Camel for a variety of use cases. They provide best practice advice and describe common patterns that we see in integration and messaging problems.

The examples can be run using Maven. When using the `mvn` command, Maven will attempt to download the required dependencies from a central repository to your local repository.

### [8.2. Examples repository Copy link](#camel-quarkus-examples-list)

Expand

| Example                                                                                                                                                                                                | Description                                                                                                                       |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| [Artemis to ElasticSearch](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/artemis-elasticsearch)                                                                             | Shows how the message is consumed from the Apache Artemis broker using MQTT protocol, transformed, and loaded into ElasticSearch. |
| [Leader election in Kubernetes](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/cluster-leader-election)                                                                      | Shows how to use Camel's master component for Kubernetes leader election.                                                         |
| [Custom](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/timer-log-main)  [`main()`](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/timer-log-main) | Shows how to start Camel from a custom  ``` main() ```  method.                                                                   |
| [Camel Quarkus CXF SOAP example](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/cxf-soap)                                                                                    | Shows how to use Camel CXF SOAP component.                                                                                        |
| [Extract, Transform, and Load between DBs](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/jdbc-datasource)                                                                   | Shows how to extract, transform, and load between two databases.                                                                  |
| [File consumer with Bindy &amp; FTP](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/file-bindy-ftp)                                                                          | Shows how to consume CSV files, marshal & unmarshal the data, and send it via FTP.                                                |
| [Health](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/health)                                                                                                              | Shows how to use Camel health-checks with Quarkus.                                                                                |
| [HTTP with vanilla JAX-RS or Camel HTTP](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/http-log)                                                                            | Shows how to create HTTP endpoints using either platform-http or RESTEasy.                                                        |
| [JMS and JPA](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/jms-jpa)                                                                                                        | Demonstrates a Camel Quarkus application supporting JTA transactions across multiple transactional resources.                     |
| [JPA idempotent repository](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/jpa-idempotent)                                                                                   | Shows how to consume a message only once, even when delivered multiple times.                                                     |
| [JTA and JPA](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/jta-jpa)                                                                                                        | Demonstrates JTA transactions across MySQL and a simulated XAResource.                                                            |
| [Kafka example](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/kafka)                                                                                                        | Shows how to produce and consume messages in a Kafka topic using Strimzi Operator.                                                |
| [Kamelet Chuck Norris](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/kamelet-chuck-norris)                                                                                  | Shows how to build a simple Kamelet for use in Camel applications.                                                                |
| [Message Bridge](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/message-bridge)                                                                                              | Demonstrates AMQ and IBM MQ clients with connection pooling and XA transactions.                                                  |
| [Observability](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/observability)                                                                                                | Demonstrates adding metrics, health checks, and distributed tracing support.                                                      |
| [OpenAPI Contract First](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/openapi-contract-first)                                                                              | Shows how to run with Contract First OpenAPI.                                                                                     |
| [Platform HTTP security with Keycloak](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/platform-http-keycloak)                                                                | Shows how to secure platform HTTP with Keycloak.                                                                                  |
| [REST with Jackson](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/rest-json)                                                                                                | Demonstrates creating a REST service using Camel REST DSL and Jackson.                                                            |
| [Saga and LRA](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/saga)                                                                                                          | Shows how to use saga and LRA patterns.                                                                                           |
| [Timer to Log](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/timer-log)                                                                                                     | Uses the Camel timer component to output a Hello world message to the console.                                                    |
| [Tokenize a CSV file](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/file-split-log-xml)                                                                                     | Shows how to define a Camel route in XML for tokenizing a CSV file.                                                               |
| [Vertx-Websocket Chat](https://github.com/jboss-fuse/camel-quarkus-examples/tree/3.20.0-product/vertx-websocket-chat)                                                                                  | Shows how to configure a WebSocket server and interact with connected peers.                                                      |

Show more

## [Legal Notice Copy link](#idm140334142968112)

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