## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Release Notes for Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm140117255338480)
3. 1. Release notes for Red Hat build of Apache Camel for Quarkus 3.20 / 3.20.4.SP2
4. [Legal Notice](#idm140117247157200)

Format Multi-page Single-page View full doc as PDF

# Release Notes for Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.10

## Release Notes for Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm140117247157200)

**Abstract**

Red Hat build of Apache Camel for Quarkus provides Quarkus extensions for many of the Camel components. Release Notes for Red Hat build of Apache Camel for Quarkus provides the latest details on what's new in this release.

## [Preface Copy link](#idm140117255338480)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. To create a ticket, click this link: [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Release notes for Red Hat build of Apache Camel for Quarkus 3.20 / 3.20.4.SP2 Copy link](#camel-quarkus-relnotes_cq-release-notes)

### [1.1. Red Hat build of Apache Camel for Quarkus features Copy link](#camel-quarkus-relnotes-features)

Fast startup and low RSS memory Using the optimized build-time and ahead-of-time (AOT) compilation features of Quarkus, your Camel application can be pre-configured at build time resulting in fast startup times. Application generator Use the [Quarkus application generator](https://code.quarkus.redhat.com/) to bootstrap your application and discover its extension ecosystem. Highly configurable

All the important aspects of a Red Hat build of Apache Camel for Quarkus application can be set up programmatically with CDI (Contexts and Dependency Injection) or by using configuration properties. By default, a CamelContext is configured and automatically started for you.

Check out the [Configuring your Quarkus applications by using a properties file](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.20/html/configuring_your_red_hat_build_of_quarkus_applications_by_using_a_properties_file/index) guide for more information on the different ways to bootstrap and configure an application.

Integrates with existing Quarkus extensions Red Hat build of Apache Camel for Quarkus provides extensions for libraries and frameworks that are used by some Camel components which inherit native support and configuration options.

### [1.2. Supported platforms, configurations, databases, and extensions Copy link](#camel-quarkus-relnotes-supported-platforms)

For information about supported platforms, configurations, and databases in Red Hat build of Apache Camel for Quarkus version 3.20, see the [Supported Configuration](https://access.redhat.com/articles/6507531) page on the Customer Portal (login required).

For a list of Red Hat Red Hat build of Apache Camel for Quarkus extensions and the Red Hat support level for each extension, see the [Extensions Overview](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) chapter of the *Red Hat build of Apache Camel for Quarkus Reference* (login required).

### [1.3. BOM files for Red Hat build of Apache Camel for Quarkus Copy link](#camel-quarkus-relnotes-bom)

- To configure your Red Hat Red Hat build of Apache Camel for Quarkus version 3.20 projects to use the supported extensions, use the latest Bill Of Materials (BOM) version `3.20.4.SP2-redhat-00001` or newer, from the [Redhat Maven Repository](https://maven.repository.redhat.com/ga/com/redhat/quarkus/platform/quarkus-bom) .

For more information about BOM dependency management, see [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)

### [1.4. Technology preview extensions Copy link](#camel-quarkus-relnotes-tech-preview)

Items designated as *Technology Preview* in the [Extensions Overview](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) chapter of the *Red Hat build of Apache Camel for Quarkus Reference* have limited supportability, as defined by the Technology Preview Features Support Scope.

### [1.5. Product errata and security advisories Copy link](#camel-quarkus-relnotes-product-errata-advisories)

#### [1.5.1. Red Hat build of Apache Camel for Quarkus Copy link](#red_hat_build_of_apache_camel_for_quarkus)

For the latest Red Hat build of Apache Camel for Quarkus product errata and security advisories, see the [Red Hat Product Errata](https://access.redhat.com/errata-search/?q=&p=1&sort=portal_publication_date+desc&rows=10&portal_product=Red%5C+Hat%5C+Build%5C+of%5C+Apache%5C+Camel&extIdCarryOver=true&sc_cid=701f2000001Css5AAC&portal_publication_date=2025) page.

#### [1.5.2. Red Hat build of Quarkus Copy link](#red_hat_build_of_quarkus)

For the latest Red Hat build of Quarkus product errata and security advisories, see the [Red Hat Product Errata](https://access.redhat.com/errata-search/?q=&p=1&sort=portal_publication_date+desc&rows=10&portal_product=Red%5C+Hat%5C+build%5C+of%5C+Quarkus&extIdCarryOver=true&intcmp=7013a000003SwrTAAS&sc_cid=701f2000001Css5AAC&portal_product_version=3&portal_publication_date=2025) page.

### [1.6. JUnit breaking changes in Red Hat build of Apache Camel for Quarkus version 3.20 Copy link](#camel-quarkus-relnotes-testing)

In Quarkus 3.20.1, JUnit was upgraded from 5.10 to 5.12.

This upgrade changees the behavior of reflection and inheritance of annotated methods because a different search algorithm is used.

If your unit test suites rely on the old behavior, you need to change your tests to take the new behavior into account.

You can emulate with the new JUnit version by setting the reflection parameter:

```
-Djunit.platform.reflection.search.useLegacySemantics=true
```

Copy to Clipboard

Toggle word wrap

For details, see the [New Features and Improvements](https://junit.org/junit5/docs/5.11.1/release-notes/#release-notes-5.11.0-junit-platform-deprecations-and-breaking-changes) section in the JUnit 5.11.0 release documentation.

### [1.7. Known issues Copy link](#camel-quarkus-relnotes-known-issues)

#### [1.7.1. Refactored .endChoice() behavior with Java DSL Copy link](#refactored_endchoice_behavior_with_java_dsl)

The `endChoice` method is changed to `end().endChoice()` (see [CEQ-11181](https://issues.redhat.com/browse/CEQ-11181) ).

When using Choice EIP then in some situations you may need to use `.endChoice()` to be able to either continue added more nodes to the current Choice EIP, or that you are working with nested Choice EIPs (choice inside choice), then you may also need to use `endChoice` to go back to the parent choice to continue from there.

However, there has been some regressions from upgrading older Camel releases to 4.11, and therefore we have refactored `endChoice` to work more consistent.

For example, the following code:

```
from("direct:start")
    .choice()
        .when(header("foo").isGreaterThan(1))
            .choice()
                .when(header("foo").isGreaterThan(5))
                    .to("mock:big")
                .otherwise()
                    .to("mock:med")
            .endChoice()
        .otherwise()
            .to("mock:low")
        .end();
```

Copy to Clipboard

Toggle word wrap

Should now be:

```
from("direct:start")
    .choice()
        .when(header("foo").isGreaterThan(1))
            .choice()
                .when(header("foo").isGreaterThan(5))
                    .to("mock:big")
                .otherwise()
                    .to("mock:med")
            .end().endChoice()
        .otherwise()
            .to("mock:low")
        .end();
```

Copy to Clipboard

Toggle word wrap

The change of `endChoice` method to `end().endChoice()` makes the calls consistent.

This ends the current (inner) choice and changes the scope to `Choice EIP` to be able to continue in the previous (outer) `choice` .

This informs Java DSL that the scope is Choice EIP and you can add the `otherwise` block to the outer `choice` , which would otherwise not work.

#### [1.7.2. Websocket + Knative does not work with HTTP2 Copy link](#websocket_knative_does_not_work_with_http2)

We support both [`camel-quarkus-grpc`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-grpc) and [`camel-vertx-websocket`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-vertx-websocket) with Knative.

`gRPC` [needs HTTP2](https://quarkus.io/guides/grpc) (you can find instructions on how to enable it here: [HTTP2 on Knative](https://docs.openshift.com/serverless/1.31/knative-serving/external-ingress-routing/using-http2-gRPC.html) ).

Unfortunately, Websockets with Knative does not work with HTTP2 (see [Ingress Operator in OpenShift Container Platform](https://docs.openshift.com/container-platform/4.15/networking/ingress-operator.html) ).

Consequently, if you have an application that is intended to accept WebSocket connections, it must not allow negotiating the HTTP/2 protocol or else clients will fail to upgrade to the WebSocket protocol.

#### [1.7.3. Error when running maven update Copy link](#error_when_running_maven_update)

When running a maven update with a specified version:

```
mvn com.redhat.quarkus.platform:quarkus-maven-plugin:3.20.4.SP2-redhat-00001:update -Drewrite
```

Copy to Clipboard

Toggle word wrap

This can cause an error similar to this:

```
Failed to apply the updates: The project is missing the Quarkus platform BOM in module foo.
```

Copy to Clipboard

Toggle word wrap

The affected modules are specific to your project, so you need to read the error message to find the affected modules.

Workaround

To avoid the error, add the `quarkus-resteasy` dependency to the modules mentioned in the error message.

In our example, the `foo` module, add the dependency to `foo/pom.xml` :

```
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

### [1.8. Important notes Copy link](#camel-quarkus-relnotes-important-notes)

#### [1.8.1. Support for IBM Power and IBM Z Copy link](#support_for_ibm_power_and_ibm_z)

Red Hat build of Apache Camel for Quarkus is supported on IBM Power and IBM Z.

#### [1.8.2. Minimum Java version - JDK 17 Copy link](#minimum_java_version_jdk_17)

Red Hat build of Apache Camel for Quarkus version 3.20 requires JDK 17 or newer.

#### [1.8.3. Support for OpenJDK Copy link](#support_for_openjdk)

Red Hat build of Apache Camel for Quarkus version 3.20 includes support for OpenJDK 21.

#### [1.8.4. Support for AdoptiumJDK Copy link](#support_for_adoptiumjdk)

Red Hat build of Apache Camel for Quarkus version 3.20 includes support for AdoptiumJDK 17 and AdoptiumJDK 21.

### [1.9. Camel upgraded from version 4.8 to version 4.10 Copy link](#camel-quarkus-relnotes-upgrades)

#### [1.9.1. Upgrading Camel from version 4.8 to version 4.10 Copy link](#apache-camel-upgrade)

For important information about upgrading Camel, see the [Apache Camel manual](https://camel.apache.org/manual/) sections:

- [Upgrade guide 4.8 → 4.9](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_9.html)
- [Upgrade guide 4.9 → 4.10](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_10.html)

#### [1.9.2. Camel release notes from version 4.8 to version 4.10 Copy link](#apache-camel-relnotes)

Red Hat build of Apache Camel for Quarkus version 3.20 has been upgraded from Camel version 4.8 to Camel version 4.10. For additional information about each intervening Camel patch release, refer to the following:

- [Apache Camel 4.10.3 Release Notes](https://camel.apache.org/releases/release-4.10.3)
- [Apache Camel 4.10.2 Release Notes](https://camel.apache.org/releases/release-4.10.2)
- [Apache Camel 4.10.1 Release Notes](https://camel.apache.org/releases/release-4.10.1)
- [Apache Camel 4.10.0 Release Notes](https://camel.apache.org/releases/release-4.10.0)
- [Apache Camel 4.9.0 Release Notes](https://camel.apache.org/releases/release-4.9.0)
- [Apache Camel 4.8.7 Release Notes](https://camel.apache.org/releases/release-4.8.7)
- [Apache Camel 4.8.6 Release Notes](https://camel.apache.org/releases/release-4.8.6)
- [Apache Camel 4.8.5 Release Notes](https://camel.apache.org/releases/release-4.8.5)
- [Apache Camel 4.8.4 Release Notes](https://camel.apache.org/releases/release-4.8.4)
- [Apache Camel 4.8.3 Release Notes](https://camel.apache.org/releases/release-4.8.3)
- [Apache Camel 4.8.2 Release Notes](https://camel.apache.org/releases/release-4.8.2)
- [Apache Camel 4.8.1 Release Notes](https://camel.apache.org/releases/release-4.8.1)
- [Apache Camel 4.8.0 Release Notes](https://camel.apache.org/releases/release-4.8.0)

### [1.10. Camel Quarkus upgraded from version 3.15 to version 3.20 Copy link](#apache-camel-quarkus-relnotes)

Red Hat build of Apache Camel for Quarkus version 3.20 has been upgraded from Camel Quarkus version 3.8 to Camel Quarkus version 3.20. For additional information about each intervening Camel Quarkus patch release, refer to the following:

- [Apache Camel 3.20.4 Release Notes](https://camel.apache.org/releases/release-3.20.4)
- [Apache Camel 3.20.3 Release Notes](https://camel.apache.org/releases/q-3.20.3)
- [Apache Camel 3.20.2 Release Notes](https://camel.apache.org/releases/q-3.20.2)
- [Apache Camel 3.20.1 Release Notes](https://camel.apache.org/releases/q-3.20.1)
- [Apache Camel 3.20.0 Release Notes](https://camel.apache.org/releases/q-3.20.0)
- [Apache Camel 3.19.0 Release Notes](https://camel.apache.org/releases/q-3.19.0)
- [Apache Camel 3.18.0 Release Notes](https://camel.apache.org/releases/q-3.18.0)
- [Apache Camel 3.17.0 Release Notes](https://camel.apache.org/releases/q-3.17.0)
- [Apache Camel 3.16.0 Release Notes](https://camel.apache.org/releases/q-3.16.0)
- [Apache Camel 3.15.3 Release Notes](https://camel.apache.org/releases/q-3.15.3)
- [Apache Camel 3.15.2 Release Notes](https://camel.apache.org/releases/q-3.15.2)
- [Apache Camel 3.15.1 Release Notes](https://camel.apache.org/releases/q-3.15.1)
- [Apache Camel 3.15.0 Release Notes](https://camel.apache.org/releases/q-3.15.0)

### [1.11. Resolved issues Copy link](#camel-quarkus-relnotes-resolved)

The following lists shows known issues that were affecting Red Hat build of Apache Camel for Quarkus, which have been fixed in Red Hat build of Apache Camel for Quarkus version 3.20.

Expand

|    | Issue                                                   | Description                                                                                                       |
|----|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
|  1 | [CEQ-12238](https://issues.redhat.com/browse/CEQ-12238) | Failover in ClusteredRoutePolicyFactory using the FileLockClusterService is unreliable when running multiple JVMs |

Show more

Expand

|    | Issue                                                   | Description                                                                             |
|----|---------------------------------------------------------|-----------------------------------------------------------------------------------------|
|  2 | [CEQ-12242](https://issues.redhat.com/browse/CEQ-12242) | CVE-2025-66566 lz4-java: Information Disclosure via Insufficient Output Buffer Clearing |

Show more

Expand

|    | Issue                                                   | Description                                                                                                                                                                       |
|----|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  1 | [CEQ-11181](https://issues.redhat.com/browse/CEQ-11181) | EIP: endChoice() cannot get the parent in nested choices                                                                                                                          |
|  2 | [CEQ-11708](https://issues.redhat.com/browse/CEQ-11708) | [CVE-2025-58056](https://access.redhat.com/security/cve/cve-2025-58056)  netty-codec-http: Netty is vulnerable to request smuggling due to incorrect parsing of chunk extensions  |
|  3 | [CEQ-11711](https://issues.redhat.com/browse/CEQ-11711) | [CVE-2025-58056](https://access.redhat.com/security/cve/cve-2025-58056)  netty-codec-http2: Netty is vulnerable to request smuggling due to incorrect parsing of chunk extensions |
|  4 | [CEQ-11650](https://issues.redhat.com/browse/CEQ-11650) | Split Brain in Camel Master with File Cluster Service.                                                                                                                            |

Show more

Expand

|    | Issue                                                   | Description                                                                                                                              |
|----|---------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
|  1 | [CEQ-11593](https://issues.redhat.com/browse/CEQ-11593) | [CVE-2025-55163](https://access.redhat.com/security/cve/cve-2025-55163)  netty-codec-http2: Netty MadeYouReset HTTP/2 DDoS Vulnerability |

Show more

Expand

|    | Issue                                                                                                               | Description                                                                                                                                                                                         |
|----|---------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  1 | [CEQ-11410](https://issues.redhat.com/browse/CEQ-11410)  /  [CEQ-11407](https://issues.redhat.com/browse/CEQ-11407) | [CVE-2025-48734](https://access.redhat.com/security/cve/cve-2025-48734)  quarkus-cxf-bom: Apache Commons BeanUtils: PropertyUtilsBean does not suppress an enum's declaredClass property by default |
|  2 | [CEQ-11414](https://issues.redhat.com/browse/CEQ-11414)                                                             | [CAMEL-22125](https://issues.apache.org/jira/browse/CAMEL-22125)  camel-platform-http-vertx - Writing response should favour input stream over ByteBuffer                                           |
|  3 | [CEQ-11420](https://issues.redhat.com/browse/CEQ-11420)                                                             | [CAMEL-22130](https://issues.apache.org/jira/browse/CAMEL-22130)  camel-platform-http-vertx - Add timeout option                                                                                    |

Show more

Expand

|    | Issue                                                   | Description                                                                                                                                           |
|----|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
|  1 | [CEQ-10290](https://issues.redhat.com/browse/CEQ-10290) | expose api to override opentelemetry trace id for camel route                                                                                         |
|  2 | [CEQ-10667](https://issues.redhat.com/browse/CEQ-10667) | The "quarkus-camel-bom-3.15.2.redhat-00003.pom" is missing artifact "io.quarkiverse.artemis/quarkus-test-artemis"                                     |
|  3 | [CEQ-10977](https://issues.redhat.com/browse/CEQ-10977) | [CAMEL-21884](https://issues.apache.org/jira/browse/CAMEL-21884)  Backport Request                                                                    |
|  4 | [CEQ-11165](https://issues.redhat.com/browse/CEQ-11165) | Camel Quarkus JPA can not work with the named persistence unit                                                                                        |
|  5 | [CEQ-11227](https://issues.redhat.com/browse/CEQ-11227) | [CAMEL-22001](https://issues.apache.org/jira/browse/CAMEL-22001)  camel-core - Kamelet and EIPs should propagate exchange variables                   |
|  6 | [CEQ-11300](https://issues.redhat.com/browse/CEQ-11300) | [CAMEL-21495](https://issues.apache.org/jira/browse/CAMEL-21495)  Backport Request: camel-quarkus: REST route inlining works incorrectly when testing |

Show more

### [1.12. Deprecated features in Red Hat build of Apache Camel for Quarkus version 3.20 Copy link](#camel-quarkus-relnotes-deprecated)

The following capabilities are not available in the next major release of Red Hat build of Apache Camel for Quarkus, and are deprecated in this release.

#### [1.12.1. camel-quarkus-jolokia register-management-endpoint default changed to false Copy link](#camel-quarkus-relnotes-deprecated-jolokia)

When using `camel-quarkus-jolokia` , the `/q/jolokia` Quarkus management endpoint is no longer registered by default. It has been deprecated for removal in a future release.

If you need to restore the previous behavior and expose `/q/jolokia` , you can add the following to `application.properties` .

```
quarkus.camel.jolokia.register-management-endpoint=true
```

Copy to Clipboard

Toggle word wrap

However, as mentioned above, this option will eventually be removed.

### [1.13. Extensions added in Red Hat build of Apache Camel for Quarkus version 3.20 Copy link](#camel-quarkus-relnotes-extensions-added)

The following table lists the extensions added in the Red Hat build of Apache Camel for Quarkus version 3.20 release .

Expand

|    | Extension              | Artifact                                                                                                                                                                                                                 | Description                                                                                             |
|----|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
|  1 | CICS                   | [`camel-quarkus-cics`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-cics)                                     | Interact with IBM CICS systems.                                                                         |
|  2 | Groovy                 | [`camel-quarkus-groovy`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-groovy)                                 | Evaluate a Groovy script.                                                                               |
|  3 | Jolokia                | [`camel-quarkus-jolokia`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-jolokia)                               | Expose runtime metrics and management operations via JMX with Jolokia.                                  |
|  4 | Observability services | [`camel-quarkus-observability-services`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-observability-services) | Camel Observability Services.                                                                           |
|  5 | Olingo4                | [`camel-quarkus-olingo4`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-olingo4)                               | Communicate with OData 4.0 services using Apache Olingo OData API.                                      |
|  6 | Smooks                 | [`camel-quarkus-smooks`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-smooks)                                 | Use Smooks to transform, route, and bind both XML and non-XML data, including EDI, CSV, JSON, and YAML. |
|  7 | SSH                    | [`camel-quarkus-ssh`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-ssh)                                       | Execute commands on remote hosts using SSH.                                                             |

Show more

### [1.14. Extensions removed in Red Hat build of Apache Camel for Quarkus version 3.20 Copy link](#camel-quarkus-relnotes-extensions-removed)

No extensions are removed in the Red Hat build of Apache Camel for Quarkus version 3.20 release.

### [1.15. Extensions with changed support in Red Hat build of Apache Camel for Quarkus version 3.20 Copy link](#camel-quarkus-relnotes-extensions-changed-support)

The following table lists the extensions that have changed support levels in the Red Hat build of Apache Camel for Quarkus version 3.20 release.

Expand

|    | Extension                                                                                                                                                                                              | Artifact                                    | Description                                                                                                |
|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|------------------------------------------------------------------------------------------------------------|
|  8 | [AWS Secrets Manager](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-aws-secrets-manager)     | ``` camel-quarkus-aws-secrets-manager ```   | Manage AWS Secrets Manager services using AWS SDK version 2.x.                                             |
|  9 | [Azure Key Vault](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-azure-key-vault)             | ``` camel-quarkus-azure-key-vault ```       | Manage secrets and keys in Azure Key Vault Service.                                                        |
| 10 | [Azure Event Bus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-azure-servicebus)            | ``` camel-quarkus-azure-servicebus ```      | Send and receive messages to/from Azure Event Bus.                                                         |
| 11 | [Beanio](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-beanio)                               | ``` camel-quarkus-beanio ```                | Marshal and unmarshal Java beans to and from flat files (such as CSV, delimited, or fixed length formats). |
| 12 | [Flink](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-flink)                                 | ``` camel-quarkus-flink ```                 | Send DataSet jobs to an Apache Flink cluster.                                                              |
| 13 | [Google Secret Manager](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-google-secret-manager) | ``` camel-quarkus-google-secret-manager ``` | Manage Google Secret Manager Secrets                                                                       |
| 14 | [JQ](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-jq)                                       | ``` camel-quarkus-jq ```                    | Evaluates a JQ expression against a JSON message body.                                                     |
| 15 | [Kubernetes](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-kubernetes)                       | ``` camel-quarkus-kubernetes ```            | Perform operations against Kubernetes API                                                                  |

Show more

Note

For information about support levels, see [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference//camel-quarkus-extensions-overview)

### [1.16. Data formats added in Red Hat build of Apache Camel for Quarkus version 3.20 Copy link](#camel-quarkus-relnotes-added-dataformats)

No data formats have been added in the Red Hat build of Apache Camel for Quarkus version 3.20 release.

### [1.17. Aggregated Quarkus CXF release notes 3.15.3 LTS → 3.20.2 LTS Copy link](#quarkus-cxf-aggregated-rn-3_20_2)

This section may help when upgrading from the 3.15 LTS stream to 3.20 LTS stream.

#### [1.17.1. Important dependency upgrades Copy link](#important_dependency_upgrades)

- Quarkus 3.15.x → 3.20.x
- CXF 4.0.5 → 4.1.1 - [release notes](https://cxf.apache.org/download.html) , [changelog](https://github.com/apache/cxf/compare/cxf-4.0.5...cxf-4.1.1)
- Santuario XML Security 3.0.4 → 4.0.4 - [changelog](https://github.com/apache/santuario-xml-security-java/compare/xmlsec-3.0.4...xmlsec-4.0.4)
- WSS4J 3.0.3 → 4.0.0

#### [1.17.2. New and noteworthy in Quarkus CXF Copy link](#new_and_noteworthy_in_quarkus_cxf)

##### [1.17.2.1. #1486 TLS Registry support Copy link](#onethousandfourhundredandeighty-six_tls_registry_support)

[Quarkus TLS registry](https://quarkus.io/version/3.15/guides/tls-registry-reference) is an extension provided by Quarkus that centralizes the TLS configuration, making it easier to manage and maintain secure connections across your application.

Note

`io.quarkus:quarkus-tls-registry` is a transitive dependency of `io.quarkiverse.cxf:quarkus-cxf` since Quarkus CXF 3.16.0, so you do not have to add it manually.

Quarkus TLS registry is the new recommended way of configuring trust stores, keystores and other TLS/SSL related settings in Quarkus CXF 3.16.0+:

**application.properties**

```
# Define a TLS configuration with name "hello-tls"
```

1

```
quarkus.tls.hello-tls.trust-store.p12.path = client-truststore.pkcs12
quarkus.tls.hello-tls.trust-store.p12.password = client-truststore-password

# Basic client settings
quarkus.cxf.client.hello.client-endpoint-url = https://localhost:${quarkus.http.test-ssl-port}/services/hello
quarkus.cxf.client.hello.service-interface = io.quarkiverse.cxf.it.security.policy.HelloService

# Use "hello-tls" defined above for this client
quarkus.cxf.client.hello.tls-configuration-name = hello-tls
```

Copy to Clipboard

Toggle word wrap

[1](#CO1-1) The referenced `client-truststore.pkcs12` file has to be available either in the classpath or in the file system.

All client-related options provided by [Quarkus TLS registry](https://quarkus.io/version/3.15/guides/tls-registry-reference#configuration-reference) are supported for Vert.x based CXF clients.

##### [1.17.2.1.1. Limitations with other clients Copy link](#limitations_with_other_clients)

The named TLS configurations provided by TLS registry can be also used for CXF clients having `http-conduit-factory` set to `URLConnectionHTTPConduitFactory` , `HttpClientHTTPConduitFactory` or with Async CXF clients on top of Apache HttpClient 5. However, in those cases, the following TLS options are not supported and using them will lead to an exception at runtime:

- [quarkus.tls."tls-bucket-name".trust-all](https://quarkus.io/version/3.15/guides/tls-registry-reference#quarkus-tls-registry_quarkus-tls-tls-bucket-name-trust-all)
- [quarkus.tls."tls-bucket-name".hostname-verification-algorithm](https://quarkus.io/version/3.15/guides/tls-registry-reference#quarkus-tls-registry_quarkus-tls-tls-bucket-name-hostname-verification-algorithm)
- [quarkus.tls."tls-bucket-name".reload-period](https://quarkus.io/version/3.15/guides/tls-registry-reference#quarkus-tls-registry_quarkus-tls-tls-bucket-name-reload-period)

##### [1.17.2.1.2. Deprecated stores Copy link](#deprecated_stores)

The [older way](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-trust-store) of configuring client trust stores and key stores is still supported, but deprecated since Quarkus CXF 3.16.0:

**application.properties**

```
# Deprecated way of setting the client trust store
quarkus.cxf.client.hello.trust-store-type = pkcs12
quarkus.cxf.client.hello.trust-store = client-truststore.pkcs12
quarkus.cxf.client.hello.trust-store-password = client-truststore-password
```

Copy to Clipboard

Toggle word wrap

##### [1.17.2.2. Vert.x HttpClient based HTTP Conduit is the new default Copy link](#vert_x_httpclient_based_http_conduit_is_the_new_default)

Vert.x HttpClient based HTTP Conduit was [introduced](https://docs.quarkiverse.io/quarkus-cxf/dev/release-notes/3.13.0.html#vert-x-httpclient-based-http-conduit) in Quarkus CXF 3.13.0. Its usage was optional through setting the `VertxHttpClientHTTPConduitFactory` on either of the options [`quarkus.cxf.client."client-name".http-conduit-factory`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-http-conduit-factory) or [`quarkus.cxf.http-conduit-factory`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-http-conduit-factory) :

**application.properties**

```
# Before Quarkus CXF 3.16.0, VertxHttpClientHTTPConduitFactory had to be set explicitly
# Set the HTTPConduitFactory per-client
quarkus.cxf.client."client-name".http-conduit-factory = VertxHttpClientHTTPConduitFactory
# Set the HTTPConduitFactory globally
quarkus.cxf.http-conduit-factory = VertxHttpClientHTTPConduitFactory
```

Copy to Clipboard

Toggle word wrap

Since then, it went through some improvements and testing so that we are now confident to make it default.

The main motivations for using Vert.x HttpClient based HTTP Conduit as a default are as follows:

- Support for HTTP/2
- Seamless integration with Quarkus, especially in the areas of worker thread poolling and SSL/TLS configuration.

##### [1.17.2.2.1. Force the old default Copy link](#force_the_old_default)

Before this change, the effective default was `URLConnectionHTTPConduitFactory` . It is still supported and tested regularly.

You can get back to the old default in any one of three ways:

1. Set the `QUARKUS_CXF_DEFAULT_HTTP_CONDUIT_FACTORY` environment variable to `URLConnectionHTTPConduitFactory`
2. Set the global [quarkus.cxf.http-conduit-factory](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-http-conduit-factory) option to `URLConnectionHTTPConduitFactory`
3. Set the per client [quarkus.cxf.client."client-name".http-conduit-factory](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-http-conduit-factory) option to `URLConnectionHTTPConduitFactory`

##### [1.17.2.2.2. Hostname verifiers not supported in combination with VertxHttpClientHTTPConduitFactory Copy link](#hostname_verifiers_not_supported_in_combination_with_vertxhttpclienthttpconduitfactory)

Since Quarkus CXF 3.16.0, setting [`quarkus.cxf.client."client-name".hostname-verifier`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-hostname-verifier) together with using the default `VertxHttpClientHTTPConduitFactory` leads to an exception at runtime.

The `AllowAllHostnameVerifier` value of that option can be replaced by using a [named TLS configuration](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) with [`hostname-verification-algorithm`](https://quarkus.io/version/3.15/guides/tls-registry-reference#trusting-all-certificates-and-hostname-verification) set to `NONE` .

Here is an example: if your configuration before Quarkus CXF 3.16.0 was as follows

**application.properties**

```
# A configuration that worked before Quarkus CXF 3.16.0
quarkus.cxf.client.helloAllowAll.client-endpoint-url = https://localhost:8444/services/hello
quarkus.cxf.client.helloAllowAll.service-interface = io.quarkiverse.cxf.it.security.policy.HelloService
quarkus.cxf.client.helloAllowAll.trust-store = client-truststore.pkcs12
quarkus.cxf.client.helloAllowAll.trust-store-password = secret
quarkus.cxf.client.helloAllowAll.hostname-verifier = AllowAllHostnameVerifier
```

Copy to Clipboard

Toggle word wrap

then an equivalent configuration for Quarkus CXF 3.16.0+ is

**application.properties**

```
# An equivalent configuration for Quarkus CXF 3.16.0+
quarkus.tls.helloAllowAll.trust-store.p12.path = client-truststore.pkcs12
quarkus.tls.helloAllowAll.trust-store.p12.password = secret
quarkus.tls.helloAllowAll.hostname-verification-algorithm = NONE
quarkus.cxf.client.helloAllowAll.client-endpoint-url = https://localhost:8444/services/hello
quarkus.cxf.client.helloAllowAll.service-interface = io.quarkiverse.cxf.it.security.policy.HelloService
quarkus.cxf.client.helloAllowAll.tls-configuration-name = helloAllowAll
```

Copy to Clipboard

Toggle word wrap

##### [1.17.2.3. #1447 Support asynchronous mode with VertxHttpClientHTTPConduit Copy link](#onethousandfourhundredandforty-seven_support_asynchronous_mode_with_vertxhttpclienthttpconduit)

Before Quarkus CXF 3.17.0, CXF clients based on `VertxHttpClientHTTPConduit` could only be called synchronously:

```
@CXFClient("hello")
HelloService hello;

String callHello() {
    // Synchronous CXF client call
    hello.hello("Joe");
}
```

Copy to Clipboard

Toggle word wrap

Quarkus CXF 3.17.0 introduces the asynchronous mode for `VertxHttpClientHTTPConduit` -based clients:

```
import io.smallrye.mutiny.Uni;

@CXFClient("hello")
HelloService hello;

Uni<String> callHelloAsync() {
    return Uni.createFrom()
            // Asynchronous CXF client call returning java.util.concurrent.Future
            .future(hello.helloAsync("Joe"))
            .map(HelloResponse::getReturn);
}
```

Copy to Clipboard

Toggle word wrap

This works much like with the existing Apache HttpClient 5 Async HTTP Transport. The main difference is that you do not need to add (now deprecated) `io.quarkiverse.cxf:quarkus-cxf-rt-transports-http-hc5` dependency to your application anymore.

You still need to [generate the async methods](https://docs.quarkiverse.io/quarkus-cxf/%7Bquarkus-cxf-doc-version%7D/user-guide/advanced-client-topics/asynchronous-client.html#asynchronous-client-generate-async-methods) using the embedded `wsdl2java` tool.

Check the [Asynchronous client](https://docs.quarkiverse.io/quarkus-cxf/%7Bquarkus-cxf-doc-version%7D/user-guide/advanced-client-topics/asynchronous-client.html) page for more information.

##### [1.17.2.4. #1609 Support HTTP redirects with VertxHttpClientHTTPConduit Copy link](#onethousandsixhundredandnine_support_http_redirects_with_vertxhttpclienthttpconduit)

Before Quarkus CXF 3.17.0, the `VertxHttpClientHTTPConduit` -based CXF clients were not following HTTP redirects (HTTP status codes 301, 302, 303 and 307 with `Location` response header) even if [`quarkus.cxf.client."client-name".auto-redirect`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-auto-redirect) was enabled for the given client.

Quarkus CXF 3.17.0 adds this functionality along with the proper support for [`quarkus.cxf.client."client-name".max-retransmits`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-max-retransmits) .

A new configuration property [`quarkus.cxf.client."client-name".redirect-relative-uri`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-redirect-relative-uri) was introduced. It is equivalent to setting `http.redirect.relative.uri` property on the CXF client request context as already supported by CXF.

##### [1.17.2.5. #1639 Add quarkus.cxf.client."client-name".max-same-uri configuration option Copy link](#onethousandsixhundredandthirty-nine_add_quarkus_cxf_client_client_name_max_same_uri_configuration_option)

Check [`quarkus.cxf.client."client-name".max-same-uri`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-max-same-uri) 's documentation for more information.

Special thanks to [@dcheng1248](https://github.com/dcheng1248) for the [contribution](https://github.com/quarkiverse/quarkus-cxf/pull/1669) .

##### [1.17.2.6. #1628 Support offloading the request data to disk with VertxHttpClientHTTPConduit Copy link](#onethousandsixhundredandtwenty-eight_support_offloading_the_request_data_to_disk_with_vertxhttpclienthttpconduit)

Quarkus CXF 3.17.0, added [support for redirects](https://docs.quarkiverse.io/quarkus-cxf/dev/release-notes/3.17.0.html#_1609_support_http_redirects_with_vertxhttpclienthttpconduit) with `VertxHttpClientHTTPConduit` . It included some basic in-memory caching of the request body for the sake of retransmission. Since Quarkus CXF 3.18.0, the `VertxHttpClientHTTPConduit` is able to offload the data to disk in case the size of the body surpasses some configurable threshold. Check the documentation of the following new configuration options to learn how the new feature works:

- [quarkus.cxf.client."client-name".auto-redirect](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-auto-redirect)
- [quarkus.cxf.retransmit-cache.threshold](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-retransmit-cache-threshold)
- [quarkus.cxf.retransmit-cache.max-size](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-retransmit-cache-max-size)
- [quarkus.cxf.retransmit-cache.directory](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-retransmit-cache-directory)
- [quarkus.cxf.retransmit-cache.gc-delay](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-retransmit-cache-gc-delay)
- [quarkus.cxf.retransmit-cache.gc-on-shut-down](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-retransmit-cache-gc-on-shut-down)

Tip

The above configuration options also works for `URLConnectionHTTPConduit` .

##### [1.17.2.7. #1616 Support authorization retransmits in VertxHttpClientHTTPConduit Copy link](#onethousandsixhundredandsixteen_support_authorization_retransmits_in_vertxhttpclienthttpconduit)

Before Quarkus CXF 3.20.0, when a remote service responded with `401 Unauthorized` or `407 Proxy Authentication Required` , clients backed by `VertxHttpClientHTTPConduit` would simply fail and the only possible workaround was to use some other HTTP conduit, such as `URLConnectionHTTPConduit` .

Since Quarkus CXF 3.20.0, `VertxHttpClientHTTPConduit` handles `401` and `407` status codes properly by sending a new request with an `Authorization` header value derived from one or more of following options:

- [quarkus.cxf.client."client-name".auth.scheme](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-auth-scheme)
- [quarkus.cxf.client."client-name".auth.username](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-auth-username)
- [quarkus.cxf.client."client-name".auth.password](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-auth-password)
- [quarkus.cxf.client."client-name".auth.token](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-auth-token)
- [quarkus.cxf.client."client-name".proxy-username](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-proxy-username)
- [quarkus.cxf.client."client-name".proxy-password](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-proxy-password)

##### [1.17.2.8. #1680 Introduce quarkus.cxf.client.tls-configuration-name to set TLS options for all CXF clients Copy link](#onethousandsixhundredandeighty_introduce_quarkus_cxf_client_tls_configuration_name_to_set_tls_options_for_all_cxf_clients)

Before Quarkus CXF 3.19.0, it was only possible to configure trust stores and key stores per CXF client via [`quarkus.cxf.client."client-name".tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) or (now deprecated) [`quarkus.cxf.client."client-name".key-store*`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-key-store) and [`quarkus.cxf.client."client-name".trust-store*`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-trust-store) options.

In cases with multiple clients, this configuration could get verbose.

Since Quarkus CXF 3.19.0, it is possible to set the trust stores and key stores for all clients using the [`quarkus.cxf.client.tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-tls-configuration-name) option.

#### [1.17.3. Bugfixes Copy link](#bugfixes)

##### [1.17.3.1. #1697 Native build fails when using quarkus-cxf-integration-tracing-opentelemetry and quarkus-jdbc-oracle Copy link](#onethousandsixhundredandninety-seven_native_build_fails_when_using_quarkus_cxf_integration_tracing_opentelemetry_and_quarkus_jdbc_oracle)

Before Quarkus CXF 3.19.0, combining `quarkus-jdbc-oracle` with `quarkus-cxf-integration-tracing-opentelemetry` or `quarkus-cxf-rt-ws-rm` in a single application resulted in an error during the build of native image as follows:

```
org.graalvm.compiler.debug.GraalError: com.oracle.graal.pointsto.constraints.UnsupportedFeatureException:
Detected a MBean server in the image heap. This is currently not supported, but could be changed in the future.
Management beans are registered in many global caches that would need to be cleared and properly re-built at image build time.
Class of disallowed object: com.sun.jmx.mbeanserver.JmxMBeanServer
```

Copy to Clipboard

Toggle word wrap

We have fixed this in Quarkus CXF 3.19.0.

Note

JMX features are still not supported in native mode.

##### [1.17.3.2. #1326 CXF-9003 Name clash between Service methods with the same name in one Java package Copy link](#onethousandthreehundredandtwenty-six_cxf_9003_name_clash_between_service_methods_with_the_same_name_in_one_java_package)

For each service method, several ancillary classes are generated at build time. These may represent a request or a response of an operation. So, for `com.acme.HelloService.hello()` method at least two classes `com.acme.jaxws_asm.Hello` and `com.acme.jaxws_asm.HelloResponse` would be generated. Before Quarkus CXF 3.20.0 and CXF 4.1.1, the name of the service class was not taken into account. Therefore, when there were multiple service interfaces containing methods with the same name in a single Java package, then the names for their ancillary classes would clash. This would mean that only one set of those classes, suiting only one of those services was stored in the application. At runtime, the following error message may appear in the application log:

```
java.lang.IllegalArgumentException: argument type mismatch
     at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
     at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
     at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
     at java.base/java.lang.reflect.Method.invoke(Method.java:568)
     at org.apache.cxf.databinding.AbstractWrapperHelper.createWrapperObject(AbstractWrapperHelper.java:114)
     at org.apache.cxf.jaxws.interceptors.WrapperClassOutInterceptor.handleMessage(WrapperClassOutInterceptor.java:91)
     at org.apache.cxf.phase.PhaseInterceptorChain.doIntercept(PhaseInterceptorChain.java:307)
     at org.apache.cxf.endpoint.ClientImpl.doInvoke(ClientImpl.java:530)
     at org.apache.cxf.endpoint.ClientImpl.invoke(ClientImpl.java:441)
     at org.apache.cxf.endpoint.ClientImpl.invoke(ClientImpl.java:356)
     at org.apache.cxf.endpoint.ClientImpl.invoke(ClientImpl.java:314)
     at org.apache.cxf.frontend.ClientProxy.invokeSync(ClientProxy.java:96)
     at org.apache.cxf.jaxws.JaxWsClientProxy.invoke(JaxWsClientProxy.java:140)
     at jdk.proxy6/jdk.proxy6.$Proxy132.hello(Unknown Source)
```

Copy to Clipboard

Toggle word wrap

The problem was fixed in CXF 4.1.1 and Quarkus CXF 3.20.0. Now, the name of the service class is taken into account. So for the above example, the names of the generated classes would be `com.acme.jaxws_asm.helloservice.Hello` and `com.acme.jaxws_asm.helloservice.HelloResponse` respectively.

#### [1.17.4. Deprecations Copy link](#deprecations)

##### [1.17.4.1. #1633 HttpClientHTTPConduitFactory value of *.http-conduit-factory deprecated Copy link](#onethousandsixhundredandthirty-three_httpclienthttpconduitfactory_value_of_http_conduit_factory_deprecated)

The `HttpClientHTTPConduitFactory` value of [`quarkus.cxf.http-conduit-factory`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-http-conduit-factory) and [`quarkus.cxf.client."client-name".http-conduit-factory`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-http-conduit-factory) existed since their inception in Quarkus CXF 2.3.0.

`HttpClientHTTPConduit` never gained any real traction within Quarkus CXF. When CXF started using it as a default, we were forced to introduce our own default ( `URLConnectionHTTPConduitFactory` ) to avoid bugs like [#992](https://github.com/quarkiverse/quarkus-cxf/issues/992) , [CXF-8885](https://issues.apache.org/jira/browse/CXF-8885) , [CXF-8951](https://issues.apache.org/jira/browse/CXF-8951) , [CXF-8946](https://issues.apache.org/jira/browse/CXF-8946) , [CXF-8903](https://issues.apache.org/jira/browse/CXF-8903) and possibly others. Now that we have `VertxHttpClientHTTPConduit` , which we can support very well on Quarkus, there are no more reasons for us to spend our resources on `HttpClientHTTPConduit` .

`HttpClientHTTPConduitFactory` was marked as deprecated in our documentation and we added some warnings on application startup for folks still using it.

##### [1.17.4.2. #1632 io.quarkiverse.cxf:quarkus-cxf-rt-transports-http-hc5 deprecated Copy link](#onethousandsixhundredandthirty-two_io_quarkiverse_cxfquarkus_cxf_rt_transports_http_hc5_deprecated)

The `io.quarkiverse.cxf:quarkus-cxf-rt-transports-http-hc5` extension is deprecated since Quarkus CXF 3.19.0 and it is scheduled for removal in 3.21.0. Use the [asynchronous mode of](https://docs.quarkiverse.io/quarkus-cxf/%7Bquarkus-cxf-doc-version%7D/user-guide/advanced-client-topics/asynchronous-client.html) [`VertxHttpClientHTTPConduit`](https://docs.quarkiverse.io/quarkus-cxf/%7Bquarkus-cxf-doc-version%7D/user-guide/advanced-client-topics/asynchronous-client.html) instead.

This is a part of our efforts to support only a single HTTP Conduit based on Vert.x HttpClient in the future.

##### [1.17.4.3. Deprecated configuration properties Copy link](#deprecated_configuration_properties)

- [`quarkus.cxf.client."client-name".username`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-username) - use [`quarkus.cxf.client."client-name".auth.username`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-auth-username) instead
- [`quarkus.cxf.client."client-name".password`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-password) - use [`quarkus.cxf.client."client-name".auth.password`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-auth-password) instead
- [`quarkus.cxf.client."client-name".key-store`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-key-store) - use [`quarkus.cxf.client."client-name".auth.tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) instead
- [`quarkus.cxf.client."client-name".key-store-password`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-key-store-password) - use [`quarkus.cxf.client."client-name".auth.tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) instead
- [`quarkus.cxf.client."client-name".key-store-type`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-key-store-type) - use [`quarkus.cxf.client."client-name".auth.tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) instead
- [`quarkus.cxf.client."client-name".key-password`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-key-password) - use [`quarkus.cxf.client."client-name".auth.tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) instead
- [`quarkus.cxf.client."client-name".trust-store`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-trust-store) - use [`quarkus.cxf.client."client-name".auth.tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) instead
- [`quarkus.cxf.client."client-name".trust-store-password`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-trust-store-password) - use [`quarkus.cxf.client."client-name".auth.tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) instead
- [`quarkus.cxf.client."client-name".trust-store-type`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-trust-store-type) - use [`quarkus.cxf.client."client-name".auth.tls-configuration-name`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/quarkus-cxf.html#quarkus-cxf_quarkus-cxf-client-client-name-tls-configuration-name) instead

#### [1.17.5. Full changelog Copy link](#full_changelog)

[https://github.com/quarkiverse/quarkus-cxf/compare/3.15.3...3.20.2](https://github.com/quarkiverse/quarkus-cxf/compare/3.15.3...3.20.2)

### [1.18. Additional resources Copy link](#camel-quarkus-relnotes_cq-release-notes)

- [Supported Configurations](https://access.redhat.com/articles/6507531)
- [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/)
- [Getting Started with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/index)
- [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)

## [Legal Notice Copy link](#idm140117247157200)

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