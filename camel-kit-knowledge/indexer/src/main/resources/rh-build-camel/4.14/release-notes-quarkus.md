## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Release Notes for Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm139736308603920)
3. 1. Release notes for Red Hat build of Apache Camel for Quarkus 3.27 / 3.27.2
4. [Legal Notice](#idm139736300942576)

Format Multi-page Single-page View full doc as PDF

# Release Notes for Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.14

## The latest details on what's new in this release.

[Legal Notice](#idm139736300942576)

**Abstract**

Release Notes for Red Hat build of Apache Camel for Quarkus provides the latest details on what's new in this release.´

## [Preface Copy link](#idm139736308603920)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. To create a ticket, click this link: [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Release notes for Red Hat build of Apache Camel for Quarkus 3.27 / 3.27.2 Copy link](#camel-quarkus-relnotes_cq-release-notes)

### [1.1. Red Hat build of Apache Camel for Quarkus features Copy link](#camel-quarkus-relnotes-features)

Fast startup and low RSS memory Using the optimized build-time and ahead-of-time (AOT) compilation features of Quarkus, your Camel application can be pre-configured at build time resulting in fast startup times. Application generator Use the [code.camel.redhat.com](https://code.camel.redhat.com/) to bootstrap your application and discover its extension ecosystem. Highly configurable

All the important aspects of a Red Hat build of Apache Camel for Quarkus application can be set up programmatically with CDI (Contexts and Dependency Injection) or by using configuration properties. By default, a CamelContext is configured and automatically started for you.

Check out the [Configuring your Quarkus applications by using a properties file](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.27/html/configuring_your_red_hat_build_of_quarkus_applications_by_using_a_properties_file/index) guide for more information on the different ways to bootstrap and configure an application.

Integrates with existing Quarkus extensions Red Hat build of Apache Camel for Quarkus provides extensions for libraries and frameworks that are used by some Camel components which inherit native support and configuration options.

### [1.2. Supported platforms, configurations, databases, and extensions Copy link](#camel-quarkus-relnotes-supported-platforms)

For information about supported platforms, configurations, and databases in Red Hat build of Apache Camel for Quarkus version 3.27, see the [Supported Configuration](https://access.redhat.com/articles/6507531) page on the Customer Portal (login required).

For a list of Red Hat Red Hat build of Apache Camel for Quarkus extensions and the Red Hat support level for each extension, see the [Extensions Overview](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) chapter of the *Red Hat build of Apache Camel for Quarkus Reference* (login required).

### [1.3. BOM files for Red Hat build of Apache Camel for Quarkus Copy link](#camel-quarkus-relnotes-bom)

To configure your Red Hat Red Hat build of Apache Camel for Quarkus version 3.27 projects to use the supported extensions, use the latest Bill Of Materials (BOM) version `3.27.1.redhat-00004` or newer, from the [Redhat Maven Repository](https://maven.repository.redhat.com/ga/org/apache/camel/quarkus/camel-quarkus/) .

For more information about BOM dependency management, see [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)

### [1.4. Technology preview extensions Copy link](#camel-quarkus-relnotes-tech-preview)

Items designated as *Technology Preview* in the [Extensions Overview](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) chapter of the *Red Hat build of Apache Camel for Quarkus Reference* have limited supportability, as defined by the Technology Preview Features Support Scope.

### [1.5. Product errata and security advisories Copy link](#camel-quarkus-relnotes-product-errata-advisories)

#### [1.5.1. Red Hat build of Apache Camel for Quarkus Copy link](#red_hat_build_of_apache_camel_for_quarkus)

For the latest Red Hat build of Apache Camel for Quarkus product errata and security advisories, see the [Red Hat Product Errata](https://access.redhat.com/errata-search/?q=&p=1&sort=portal_publication_date+desc&rows=10&portal_product=Red%5C+Hat%5C+Build%5C+of%5C+Apache%5C+Camel&extIdCarryOver=true&sc_cid=701f2000001Css5AAC&portal_publication_date=2025) page.

#### [1.5.2. Red Hat build of Quarkus Copy link](#red_hat_build_of_quarkus)

For the latest Red Hat build of Quarkus product errata and security advisories, see the [Red Hat Product Errata](https://access.redhat.com/errata-search/?q=&p=1&sort=portal_publication_date+desc&rows=10&portal_product=Red%5C+Hat%5C+build%5C+of%5C+Quarkus&extIdCarryOver=true&intcmp=7013a000003SwrTAAS&sc_cid=701f2000001Css5AAC&portal_product_version=3&portal_publication_date=2025) page.

### [1.6. Known issues Copy link](#camel-quarkus-relnotes-known-issues)

#### [1.6.1. Limitations in Groovy extension Copy link](#rn-groovy-camel-quarkus-limitations)

In this release, some important limitations apply to the [Groovy extension](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extension-groovy) .

##### [1.6.1.1. Native mode limitations Copy link](#native_mode_limitations)

Due to an issue in GraalVM / Mandrel 23.1.x, you **must** build your native application with the [`--report-unsupported-elements-at-runtime`](https://quarkus.io/version/3.27/guides/all-config#quarkus-core_quarkus-native-report-errors-at-runtime) option. You can do this by adding the following configuration to `application.properties` .

```
quarkus.native.report-errors-at-runtime=true
```

Copy to Clipboard

Toggle word wrap

##### [1.6.1.2. Static compilation Copy link](#static_compilation)

Compilation of Groovy expressions is made with static compilation enabled. Which means that the types used in your expressions must be known at compile time. Refer to the [Groovy documentation for more details](https://docs.groovy-lang.org/latest/html/documentation/core-semantics.html#static-type-checking) .

This primarily impacts the customization of the Groovy Shell and the handling of exchange information. In native mode, customizing the Groovy Shell and accessing the following exchange variables will not function as expected.

- attachment
- exchangeProperty
- exchangeProperties
- header
- log
- variable
- variables

If you use property placeholders within your expressions like.

```
from("direct:start")
    .transform().groovy("println '{{greeting.message}}'");
```

Copy to Clipboard

Toggle word wrap

`greeting.message` will be evaluated once at build time and its value will be permanently stored in the native image. It is not possible to override the value of the property at runtime. Attempting to do so will result in an exception being thrown.

#### [1.6.2. Refactored .endChoice() behavior with Java DSL Copy link](#refactored_endchoice_behavior_with_java_dsl)

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

#### [1.6.3. Websocket + Knative does not work with HTTP2 Copy link](#websocket_knative_does_not_work_with_http2)

We support both [`camel-quarkus-grpc`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-grpc) and [`camel-vertx-websocket`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-vertx-websocket) with Knative.

`gRPC` [needs HTTP2](https://quarkus.io/guides/grpc) (you can find instructions on how to enable it here: [HTTP2 on Knative](https://docs.openshift.com/serverless/1.31/knative-serving/external-ingress-routing/using-http2-gRPC.html) ).

Unfortunately, Websockets with Knative does not work with HTTP2 (see [Ingress Operator in OpenShift Container Platform](https://docs.openshift.com/container-platform/4.15/networking/ingress-operator.html) ).

Consequently, if you have an application that is intended to accept WebSocket connections, it must not allow negotiating the HTTP/2 protocol or else clients will fail to upgrade to the WebSocket protocol.

#### [1.6.4. Error when running maven update Copy link](#error_when_running_maven_update)

When running a maven update with a specified version:

```
mvn com.redhat.quarkus.platform:quarkus-maven-plugin:3.27.2.redhat-00002:update -Drewrite
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

#### [1.6.5. Exporting Camel CLI projects to Quarkus requires Red Hat Maven GA repository Copy link](#exporting_camel_cli_projects_to_quarkus_requires_red_hat_maven_ga_repository)

When running a camel CLI project export, the export may fail with errors related to missing artifacts or classes.

As a workaround, before [installing Camel CLI](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/tooling_guide_for_red_hat_build_of_apache_camel/index#add-red-hat-repositories-to-maven-cq) , you can [add Red Hat repositories to your local Maven](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/tooling_guide_for_red_hat_build_of_apache_camel/index#add-red-hat-repositories-to-maven-cq) configuration file.

### [1.7. Important notes Copy link](#camel-quarkus-relnotes-important-notes)

#### [1.7.1. Support for IBM Power and IBM Z Copy link](#support_for_ibm_power_and_ibm_z)

Red Hat build of Apache Camel for Quarkus is supported on IBM Power and IBM Z.

#### [1.7.2. Minimum Java version - JDK 17 Copy link](#minimum_java_version_jdk_17)

Red Hat build of Apache Camel for Quarkus version 3.27 requires JDK 17 or newer.

#### [1.7.3. Support for OpenJDK Copy link](#support_for_openjdk)

Red Hat build of Apache Camel for Quarkus version 3.27 includes support for OpenJDK 21.

#### [1.7.4. Support for AdoptiumJDK Copy link](#support_for_adoptiumjdk)

Red Hat build of Apache Camel for Quarkus version 3.27 includes support for AdoptiumJDK 17 and AdoptiumJDK 21.

### [1.8. Camel upgraded from version 4.10 to version 4.14 Copy link](#camel-quarkus-relnotes-upgrades)

#### [1.8.1. Upgrading Camel from version 4.10 to version 4.14 Copy link](#apache-camel-upgrade)

For important information about upgrading Camel, see the [Apache Camel manual](https://camel.apache.org/manual/) sections:

- [Upgrade guide 4.13 → 4.14](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_14.html)
- [Upgrade guide 4.12 → 4.13](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_13.html)
- [Upgrade guide 4.11 → 4.12](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_12.html)
- [Upgrade guide 4.10 → 4.11](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_11.html)

#### [1.8.2. Camel release notes from version 4.10 to version 4.14 Copy link](#apache-camel-relnotes)

Red Hat build of Apache Camel for Quarkus version 3.27 has been upgraded from Camel version 4.10.4 to Camel version 4.14.2. For additional information about each intervening Camel patch release, refer to the following:

- [Apache Camel 4.14.2 Release Notes](https://camel.apache.org/releases/release-4.14.2)
- [Apache Camel 4.14.1 Release Notes](https://camel.apache.org/releases/release-4.14.1)
- [Apache Camel 4.14.0 Release Notes](https://camel.apache.org/releases/release-4.14.0)
- [Apache Camel 4.13.0 Release Notes](https://camel.apache.org/releases/release-4.13.0)
- [Apache Camel 4.12.0 Release Notes](https://camel.apache.org/releases/release-4.12.0)
- [Apache Camel 4.11.0 Release Notes](https://camel.apache.org/releases/release-4.11.0)
- [Apache Camel 4.10.7 Release Notes](https://camel.apache.org/releases/release-4.10.7)
- [Apache Camel 4.10.6 Release Notes](https://camel.apache.org/releases/release-4.10.6)
- [Apache Camel 4.10.5 Release Notes](https://camel.apache.org/releases/release-4.10.5)

#### [1.8.3. Camel Quarkus upgraded from version 3.20 to version 3.27 Copy link](#apache-camel-quarkus-relnotes)

Red Hat build of Apache Camel for Quarkus version 3.27 has been upgraded from Camel Quarkus version 3.20 to Camel Quarkus version 3.27. For additional information about each intervening Camel Quarkus patch release, refer to the following:

- [Apache Camel 3.27.1 Release Notes](https://camel.apache.org/releases/q-3.27.1)
- [Apache Camel 3.27.0 Release Notes](https://camel.apache.org/releases/q-3.27.0)
- [Apache Camel 3.26.0 Release Notes](https://camel.apache.org/releases/q-3.26.0)
- [Apache Camel 3.25.0 Release Notes](https://camel.apache.org/releases/q-3.25.0)
- [Apache Camel 3.24.0 Release Notes](https://camel.apache.org/releases/q-3.24.0)
- [Apache Camel 3.23.0 Release Notes](https://camel.apache.org/releases/q-3.23.0)
- [Apache Camel 3.22.0 Release Notes](https://camel.apache.org/releases/q-3.22.0)

### [1.9. Resolved issues Copy link](#camel-quarkus-relnotes-resolved)

The following lists shows known issues that were affecting Red Hat build of Apache Camel for Quarkus, which have been fixed in Red Hat build of Apache Camel for Quarkus version 3.27.

Expand

|    | Issue                                                   | Description                                                                                        |
|----|---------------------------------------------------------|----------------------------------------------------------------------------------------------------|
|  1 | [CEQ-12480](https://issues.redhat.com/browse/CEQ-12480) | Backport CAMEL-22784 - Failover in FileLockClusterService is unreliable when running multiple JVMs |
|  2 | [CEQ-12033](https://issues.redhat.com/browse/CEQ-12033) | Add support for configuring proxy host, proxy port, and no-proxy settings                          |

Show more

Expand

|    | Issue                                                   | Description                                                                             |
|----|---------------------------------------------------------|-----------------------------------------------------------------------------------------|
|  1 | [CEQ-12239](https://issues.redhat.com/browse/CEQ-12239) | CVE-2025-66566 lz4-java: Information Disclosure via Insufficient Output Buffer Clearing |

Show more

Expand

|    | Issue                                                   | Description                                               |
|----|---------------------------------------------------------|-----------------------------------------------------------|
|  1 | [CEQ-11915](https://issues.redhat.com/browse/CEQ-11915) | Using JPA component in Split block lead to various errors |

Show more

Expand

|    | Issue                                                   | Description                                                                                  |
|----|---------------------------------------------------------|----------------------------------------------------------------------------------------------|
|  1 | [CEQ-11887](https://issues.redhat.com/browse/CEQ-11887) | CVE-2025-59952 io.minio/minio: minio-java Client XML Tag is Vulnerable to Value Substitution |
|  2 | [CEQ-11874](https://issues.redhat.com/browse/CEQ-11874) | CVE-2025-59952 minio: minio-java Client XML Tag is Vulnerable to Value Substitution          |

Show more

Expand

|    | Issue                                                   | Description                                                                                                     |
|----|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
|  1 | [CEQ-11714](https://issues.redhat.com/browse/CEQ-11714) | ``` ComponentsBuilderFactory ```  should provide builders for unsupported components when explicitly required   |
|  2 | [CEQ-11704](https://issues.redhat.com/browse/CEQ-11704) | Productize  ``` camel-debug ```                                                                                 |
|  3 | [CEQ-11684](https://issues.redhat.com/browse/CEQ-11684) | Support extension:  ``` camel-quarkus-crypto-pgp ```                                                            |
|  4 | [CEQ-11395](https://issues.redhat.com/browse/CEQ-11395) | Support extension  ``` camel-quarkus-azure-storage-datalake ```                                                 |
|  5 | [CEQ-11304](https://issues.redhat.com/browse/CEQ-11304) | [CAMEL-22059] Backport Request:  ``` camel-ssh ```  - Calling 2nd time does not keep correct exit value header. |
|  6 | [CEQ-11203](https://issues.redhat.com/browse/CEQ-11203) | camel-rest: Contract first implementation doesn't honor the contextPath set in restConfiguration.               |
|  7 | [CEQ-11185](https://issues.redhat.com/browse/CEQ-11185) | Lack of Contents About "REST DSL With Contract-First OpenAPI".                                                  |
|  8 | [CEQ-11544](https://issues.redhat.com/browse/CEQ-11544) | [Doc] Missing backport CEQ-11181 in the release notes for 3.20                                                  |

Show more

### [1.10. Deprecated features in Red Hat build of Apache Camel for Quarkus version 3.27 Copy link](#camel-quarkus-relnotes-deprecated)

The following capabilities are deprecated in this release.

#### [1.10.1. Jolokia /q/jolokia endpoint Copy link](#camel-quarkus-relnotes-deprecated-jolokia)

The `camel-quarkus-jolokia register-management-endpoint` default has been changed to `false` .

When using `camel-quarkus-jolokia` , the `/q/jolokia` Quarkus management endpoint is no longer registered by default. It has been deprecated for removal in a future release.

If you need to restore the previous behavior and expose `/q/jolokia` , you can add the following to `application.properties` .

```
quarkus.camel.jolokia.register-management-endpoint=true
```

Copy to Clipboard

Toggle word wrap

However, as mentioned above, this option will eventually be removed.

#### [1.10.2. Olingo4 Copy link](#camel-quarkus-relnotes-deprecated-olingo)

The [Apache Olingo project](https://olingo.apache.org/) has been retired.

The Olingo4 component `camel-olingo4` is deprecated in this release.

There is currently no viable replacement component, although `camel-sap´ might work for limited cases.

### [1.11. Extensions added in Red Hat build of Apache Camel for Quarkus version 3.27 Copy link](#camel-quarkus-relnotes-extensions-added)

The following table lists the extensions added in the Red Hat build of Apache Camel for Quarkus version 3.27 release .

Expand

|    | Extension              | Artifact                                                                                                                                                                                                                | Description                                                   |
|----|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
|  1 | Crypto PGP             | [`camel-quarkus-crypto-pgp`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extension-crypto-pgp)                         | Encrypt and decrypt messages using Bouncy Castle OpenPGP API. |
|  2 | Opensearch             | [`camel-quarkus-opensearch`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extension-opensearch)                         | Send requests to OpenSearch via Java Client API.              |
|  3 | Mail Microsoft Oauth   | [`camel-quarkus-mail-microsoft-oauth`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extension-mail-microsoft-oauth)     | Camel Mail OAuth2 Authenticator for Microsoft Exchange Online |
|  4 | Azure Storage Datalake | [`camel-quarkus-azure-storage-datalake`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extension-azure-storage-datalake) | Camel Azure Datalake Gen2 Component.                          |

Show more

### [1.12. Extensions removed in Red Hat build of Apache Camel for Quarkus version 3.27 Copy link](#camel-quarkus-relnotes-extensions-removed)

The following table lists the extensions added in the Red Hat build of Apache Camel for Quarkus version 3.27 release .

Expand

| Extension                         | Artifact                                   | Description                                                            | Note                                                                                                                                                                                                                                                                                                                                    |
|-----------------------------------|--------------------------------------------|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Quarkus CXF Transports HTTP Async | ``` quarkus-cxf-rt-transports-http-hc5 ``` | Implement async SOAP Clients using Apache HttpComponents HttpClient 5. | The extension has been deprecated since Quarkus CXF 3.19.0 and was removed in 3.22.0.  Use the asynchronous mode of  ``` VertxHttpClientHTTPConduit ```  instead. All asynchronous client functionality is now supported by the  ``` io.quarkiverse.cxf:quarkus-cxf extension ```  . You do not need any additional extension for that. |

Show more

### [1.13. Extensions with changed support in Red Hat build of Apache Camel for Quarkus version 3.27 Copy link](#camel-quarkus-relnotes-extensions-changed-support)

No extensions have changed support levels in the Red Hat build of Apache Camel for Quarkus version 3.27 release.

The following table lists the data formats that have been added in the Red Hat build of Apache Camel for Quarkus version 3.27 release.

Expand

| Extension   | Artifact                   | Description                          |
|-------------|----------------------------|--------------------------------------|
| JAXP        | ``` camel-quarkus-jaxp ``` | XML JAXP type converters and parsers |

Show more

#### [1.13.1. Aggregated Quarkus CXF release notes 3.20.2 LTS → 3.27.1 LTS Copy link](#quarkus-cxf-aggregated-rn-3_27_1)

This document may help when upgrading from the 3.20 LTS stream to 3.27 LTS stream.

##### [1.13.1.1. Important dependency upgrades Copy link](#important_dependency_upgrades)

- Quarkus 3.20.x → 3.27.x
- CXF 4.1.1 → 4.1.3 - [release notes](https://cxf.apache.org/download.html) , [changelog](https://github.com/apache/cxf/compare/cxf-4.1.1...cxf-4.1.3)
- JAXB Plugins 4.0.9 → 4.0.11 - [release notes 4.0.10](https://github.com/highsource/jaxb-tools/releases/tag/4.0.10) , [release notes 4.0.11](https://github.com/highsource/jaxb-tools/releases/tag/4.0.11)
- Ehcache 3.10.8 → 3.11.1 [changelog](https://github.com/ehcache/ehcache3/compare/v3.10.8...v3.11.1)
- Woodstox 7.1.0 → 7.1.1 - [changelog](https://github.com/FasterXML/woodstox/compare/woodstox-core-7.1.0...woodstox-core-7.1.1)

##### [1.13.1.2. New and noteworthy in Quarkus CXF Copy link](#new_and_noteworthy_in_quarkus_cxf)

##### [1.13.1.2.1. #1778 Introduce quarkus.cxf.client.worker-dispatch-timeout Copy link](#onethousandsevenhundredandseventy-eight_introduce_quarkus_cxf_client_worker_dispatch_timeout)

Before Quarkus CXF 3.22.0, when a SOAP client application using asynchronous clients was under high load, it might have taken every long time till there was a worker thread available for executing the client call.

In such situations, it was hard to figure out, what was the root cause of those hanging clients.

In Quarkus CXF 3.22.0, we introduced the [`quarkus.cxf.client.worker-dispatch-timeout`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-client-worker-dispatch-timeout) configuration parameter, that limits the time SOAP clients can wait for a free executor thread. In case the timeout is surpassed, an exception is thrown informing about the problem:

```
Unable to dispatch SOAP client call within 30000 ms on a worker thread due to worker thread pool exhaustion.
You may want to adjust one or more of the following configuration options:
quarkus.thread-pool.core-threads, quarkus.thread-pool.max-threads, quarkus.cxf.client.worker-dispatch-timeout
```

Copy to Clipboard

Toggle word wrap

##### [1.13.1.2.2. #1553 Support XJC plugins from org.jvnet.jaxb:jaxb-plugins Copy link](#onethousandfivehundredandfifty-three_support_xjc_plugins_from_org_jvnet_jaxbjaxb_plugins)

[XJC Plugins](https://docs.quarkiverse.io/quarkus-cxf/3.27/user-guide/contract-first-code-first/generate-java-from-wsdl.html#generate-java-from-wsdl-xjc-plugins) are one of the ways how the Java files produced by `wsdl2java` can be customized.

Before Quarkus CXF 3.23.0, only XJC Plugins from `org.apache.cxf.xjcplugins:cxf-xjc-*` were supported via [`io.quarkiverse.cxf:quarkus-cxf-xjc-plugins`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/#quarkus-cxf-xjc-plugins) .

Since Quarkus CXF 3.23.0, also CXF Plugins `org.jvnet.jaxb:jaxb-plugins` can be used via [`io.quarkiverse.cxf:quarkus-cxf-jaxb-plugins`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/#quarkus-cxf-jaxb-plugins) extension.

Check the [`wsdl2java`](https://docs.quarkiverse.io/quarkus-cxf/3.27/user-guide/contract-first-code-first/generate-java-from-wsdl.html#generate-java-from-wsdl-customize-the-java-model-classes) [guide](https://docs.quarkiverse.io/quarkus-cxf/3.27/user-guide/contract-first-code-first/generate-java-from-wsdl.html#generate-java-from-wsdl-customize-the-java-model-classes) for more details.

##### [1.13.1.2.3. #1853 Expose Vert.x HttpClient connection pool configuration options Copy link](#onethousandeighthundredandfifty-three_expose_vert_x_httpclient_connection_pool_configuration_options)

See the ducumentation of the new options:

- [quarkus.cxf.client."client-name".vertx.connection-pool.http1-max-size](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-client-client-name-vertx-connection-pool-http1-max-size)
- [quarkus.cxf.client."client-name".vertx.connection-pool.http2-max-size](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-client-client-name-vertx-connection-pool-http2-max-size)
- [quarkus.cxf.client."client-name".vertx.connection-pool.cleaner-period](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-client-client-name-vertx-connection-pool-cleaner-period)
- [quarkus.cxf.client."client-name".vertx.connection-pool.event-loop-size](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-client-client-name-vertx-connection-pool-event-loop-size)
- [quarkus.cxf.client."client-name".vertx.connection-pool.max-wait-queue-size](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-client-client-name-vertx-connection-pool-max-wait-queue-size)

##### [1.13.1.3. Fixed issues Copy link](#fixed_issues)

##### [1.13.1.3.1. #1891 Implementations of javax.wsdl.extensions.ExtensibilityElement need to get registered for reflection Copy link](#onethousandeighthundredandninety-one_implementations_of_javax_wsdl_extensions_extensibilityelement_need_to_get_registered_for_reflection)

Before Quarkus CXF 3.26.1, if the WSDL of a client contained elements from the [`http://schemas.xmlsoap.org/wsdl/http/`](http://schemas.xmlsoap.org/wsdl/http/) namespace, like in the following example

```
<?xml version='1.0' encoding='UTF-8'?>
<wsdl:definitions
    xmlns:xsd="http://www.w3.org/2001/XMLSchema"
    xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
    xmlns:tns="http://test.deployment.cxf.quarkiverse.io/"
    xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
    xmlns:ns1="http://schemas.xmlsoap.org/soap/http"
    name="ExtensorsService"
    targetNamespace="http://test.deployment.cxf.quarkiverse.io/"
    xmlns:http="http://schemas.xmlsoap.org/wsdl/http/" >
...
  <wsdl:portType name="ExtensorsServiceGet" />
  <wsdl:binding name="ExtensorsServiceGet" type="tns:ExtensorsServiceGet">
    <http:binding verb="GET" />
  </wsdl:binding>
  <wsdl:service name="ExtensorsService">
    <wsdl:port binding="tns:ExtensorsServiceGet" name="ExtensorsServiceGet" >
      <http:address location="http://localhost:8081/soap/ExtensorsServiceGet" />
    </wsdl:port>
  </wsdl:service>
</wsdl:definitions>
```

Copy to Clipboard

Toggle word wrap

then the application would fail during boot in native mode with an error message similar to the following:

```
ERROR [io.qua.run.Application] (main) Failed to start application: java.lang.RuntimeException: Failed to start quarkus
        at io.quarkus.runner.ApplicationImpl.doStart(Unknown Source)
        ...
Caused by: org.apache.cxf.service.factory.ServiceConstructionException: SERVICE_CREATION_MSG
        at org.apache.cxf.wsdl11.WSDLServiceFactory.<init>(WSDLServiceFactory.java:87)
        ...
Caused by: javax.wsdl.WSDLException: WSDLException (at /wsdl:definitions/wsdl:binding[2]/http:binding): faultCode=CONFIGURATION_ERROR: Problem instantiating Java extensionType 'com.ibm.wsdl.extensions.http.HTTPBindingImpl'.: java.lang.InstantiationException: com.ibm.wsdl.extensions.http.HTTPBindingImpl
        at javax.wsdl.extensions.ExtensionRegistry.createExtension(ExtensionRegistry.java:383)
        ...
Caused by: java.lang.InstantiationException: com.ibm.wsdl.extensions.http.HTTPBindingImpl
        at java.base@21.0.8/java.lang.Class.newInstance(DynamicHub.java:719)
        ...
Caused by: java.lang.NoSuchMethodException: com.ibm.wsdl.extensions.http.HTTPBindingImpl.<init>()
        at java.base@21.0.8/java.lang.Class.checkMethod(DynamicHub.java:1078)
        at java.base@21.0.8/java.lang.Class.getConstructor0(DynamicHub.java:1241)
        at java.base@21.0.8/java.lang.Class.newInstance(DynamicHub.java:706)
        ... 28 more
```

Copy to Clipboard

Toggle word wrap

Since Quarkus CXF 3.26.1, no classes related to the [`http://schemas.xmlsoap.org/wsdl/http/`](http://schemas.xmlsoap.org/wsdl/http/) namespace need to be registered for reflection by end users.

Special thanks to [Lazaro Miguel Coronado Torres](https://github.com/quarkiverse/quarkus-cxf/discussions/1882) for reporting this issue.

##### [1.13.1.4. Deprecations and removals Copy link](#deprecations_and_removals)

##### [1.13.1.4.1. Remove HttpClientHTTPConduitFactory value of *http-conduit-factory Copy link](#remove_httpclienthttpconduitfactory_value_of_http_conduit_factory)

The `HttpClientHTTPConduitFactory` value of [`quarkus.cxf.http-conduit-factory`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-http-conduit-factory) and [`quarkus.cxf.client."client-name".http-conduit-factory`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-client-client-name-http-conduit-factory) was deprecated since Quarkus CXF [3.18.0](https://docs.quarkiverse.io/quarkus-cxf/dev/release-notes/3.27.1.html3.18.0.html#issue_1633_httpclienthttpconduitfactory_value_deprecated) , because it never gained any real traction within Quarkus CXF.

`HttpClientHTTPConduitFactory` was removed from Quarkus CXF 3.22.0.

Use the default `VertxHttpClientHTTPConduit` instead.

Both the removal of `HttpClientHTTPConduitFactory` and `quarkus-cxf-rt-transports-http-hc5` (see the next section) are a part of our efforts to support only a single HTTP Conduit based on Vert.x HttpClient in the future. For now, the `URLConnectionHTTPConduitFactory` stays fully supported, although it is not used by default since Quarkus CXF 3.16.0.

Check [`quarkus.cxf.http-conduit-factory`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-http-conduit-factory) and [`quarkus.cxf.client."client-name".http-conduit-factory`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#quarkus-cxf_quarkus-cxf-client-client-name-http-conduit-factory) for more information.

##### [1.13.1.4.2. Remove quarkus-cxf-rt-transports-http-hc5 extension Copy link](#remove_quarkus_cxf_rt_transports_http_hc5_extension)

`io.quarkiverse.cxf:quarkus-cxf-rt-transports-http-hc5` has been deprecated since Quarkus CXF 3.19.0 and was removed in 3.22.0.

Use the [asynchronous mode of](https://camel.apache.org/camel-quarkus/3.27.x/user-guide/advanced-client-topics/asynchronous-client.html) [`VertxHttpClientHTTPConduit`](https://camel.apache.org/camel-quarkus/3.27.x/user-guide/advanced-client-topics/asynchronous-client.html) instead. All asynchronous client functionality is now supported by the `io.quarkiverse.cxf:quarkus-cxf` extension. You do not need any additional extension for that.

##### [1.13.1.5. Documentation improvements Copy link](#documentation_improvements)

- Show only recent releases in the navigation. The complete list of releases is still available on the [Release notes](https://docs.quarkiverse.io/quarkus-cxf/dev/release-notes/3.27.1.htmlindex.html) page.
- Accessing SOAP [client metrics](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-rt-features-metrics.html#extensions-quarkus-cxf-rt-features-metrics-usage-client-metrics) and [Vert.x HttpClient metrics](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html/quarkus_cxf_for_red_hat_build_of_apache_camel/quarkus-cxf-reference#extensions-quarkus-cxf-rt-features-metrics-usage-vert-x-httpclient-metrics) .

##### [1.13.1.6. Full changelog Copy link](#full_changelog)

[https://github.com/quarkiverse/quarkus-cxf/compare/3.27.1...3.20.2](https://github.com/quarkiverse/quarkus-cxf/compare/3.27.1...3.20.2)

#### [1.13.2. Additional resources Copy link](#camel-quarkus-relnotes-extensions-changed-support)

- [Supported Configurations](https://access.redhat.com/articles/6507531)
- [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/)
- [Getting Started with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/index)
- [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)

## [Legal Notice Copy link](#idm139736300942576)

Copyright © Red Hat. Except as otherwise noted below, the text of and illustrations in this documentation are licensed by Red Hat under the Creative Commons Attribution-Share Alike 3.0 Unported license . If you distribute this document or an adaptation of it, you must provide the URL for the original version. Red Hat, as the licensor of this document, waives the right to enforce, and agrees not to assert, Section 4d of CC-BY-SA to the fullest extent permitted by applicable law. Red Hat, the Red Hat logo, JBoss, Hibernate, and RHCE are trademarks or registered trademarks of Red Hat, LLC. or its subsidiaries in the United States and other countries.

Linux ® is the registered trademark of Linus Torvalds in the United States and other countries. XFS is a trademark or registered trademark of Hewlett Packard Enterprise Development LP or its subsidiaries in the United States and other countries. The OpenStack ® Word Mark and OpenStack logo are trademarks or registered trademarks of the Linux Foundation, used under license. All other trademarks are the property of their respective owners.

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