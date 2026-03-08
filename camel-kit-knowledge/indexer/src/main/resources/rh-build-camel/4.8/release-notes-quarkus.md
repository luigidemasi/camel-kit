## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Release Notes for Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm139714378622688)
3. 1. Release notes for Red Hat build of Apache Camel for Quarkus 3.15 / 3.15.4
4. [Legal Notice](#idm139714383700704)

Format Multi-page Single-page View full doc as PDF

# Release Notes for Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.8

## Release Notes for Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm139714383700704)

**Abstract**

Red Hat build of Apache Camel for Quarkus provides Quarkus extensions for many of the Camel components. Release Notes for Red Hat build of Apache Camel for Quarkus provides the latest details on what's new in this release.

## [Preface Copy link](#idm139714378622688)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Release notes for Red Hat build of Apache Camel for Quarkus 3.15 / 3.15.4 Copy link](#camel-quarkus-extensions-release-notes)

### [1.1. Red Hat build of Apache Camel for Quarkus features Copy link](#camel-quarkus-relnotes-features)

Fast startup and low RSS memory Using the optimized build-time and ahead-of-time (AOT) compilation features of Quarkus, your Camel application can be pre-configured at build time resulting in fast startup times. Application generator Use the [Quarkus application generator](https://code.quarkus.redhat.com/) to bootstrap your application and discover its extension ecosystem. Highly configurable

All the important aspects of a Red Hat build of Apache Camel for Quarkus application can be set up programmatically with CDI (Contexts and Dependency Injection) or by using configuration properties. By default, a CamelContext is configured and automatically started for you.

Check out the [Configuring your Quarkus applications by using a properties file](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.15html-single/configuring_your_red_hat_build_of_quarkus_applications_by_using_a_properties_file/assembly_quarkus-configuration-guide_quarkus-configuration-guide#assembly_quarkus-configuration-guide_quarkus-configuration-guide) guide for more information on the different ways to bootstrap and configure an application.

Integrates with existing Quarkus extensions Red Hat build of Apache Camel for Quarkus provides extensions for libraries and frameworks that are used by some Camel components which inherit native support and configuration options.

### [1.2. Supported platforms, configurations, databases, and extensions Copy link](#camel-quarkus-relnotes-supported-platforms)

For information about supported platforms, configurations, and databases in Red Hat build of Apache Camel for Quarkus version 3.15, see the [Supported Configuration](https://access.redhat.com/articles/6507531) page on the Customer Portal (login required).

For a list of Red Hat Red Hat build of Apache Camel for Quarkus extensions and the Red Hat support level for each extension, see the [Extensions Overview](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) chapter of the *Red Hat build of Apache Camel for Quarkus Reference* (login required).

### [1.3. BOM files for Red Hat build of Apache Camel for Quarkus Copy link](#camel-quarkus-relnotes-bom)

- To configure your Red Hat Red Hat build of Apache Camel for Quarkus version 3.15 projects to use the supported extensions, use the latest Bill Of Materials (BOM) version `3.15.4.redhat-00001` or newer, from the [Redhat Maven Repository](https://maven.repository.redhat.com/ga/com/redhat/quarkus/platform/quarkus-bom) .

For more information about BOM dependency management, see [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)

### [1.4. Technology preview extensions Copy link](#camel-quarkus-relnotes-tech-preview)

Items designated as *Technology Preview* in the [Extensions Overview](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/) chapter of the *Red Hat build of Apache Camel for Quarkus Reference* have limited supportability, as defined by the Technology Preview Features Support Scope.

### [1.5. Product errata and security advisories Copy link](#camel-quarkus-relnotes-product-errata-advisories)

#### [1.5.1. Red Hat build of Apache Camel for Quarkus Copy link](#red_hat_build_of_apache_camel_for_quarkus)

For the latest Red Hat build of Apache Camel for Quarkus product errata and security advisories, see the [Red Hat Product Errata](https://access.redhat.com/errata-search/?q=&p=1&sort=portal_publication_date+desc&rows=10&portal_product=Red%5C+Hat%5C+Build%5C+of%5C+Apache%5C+Camel&extIdCarryOver=true&sc_cid=701f2000001Css5AAC&portal_publication_date=2025) page.

#### [1.5.2. Red Hat build of Quarkus Copy link](#red_hat_build_of_quarkus)

For the latest Red Hat build of Quarkus product errata and security advisories, see the [Red Hat Product Errata](https://access.redhat.com/errata-search/?q=&p=1&sort=portal_publication_date+desc&rows=10&portal_product=Red%5C+Hat%5C+build%5C+of%5C+Quarkus&extIdCarryOver=true&intcmp=7013a000003SwrTAAS&sc_cid=701f2000001Css5AAC&portal_product_version=3&portal_publication_date=2025) page.

### [1.6. Known issues Copy link](#camel-quarkus-relnotes-known-issues)

#### [1.6.1. Websocket + Knative does not work with HTTP2 Copy link](#websocket_knative_does_not_work_with_http2)

We support both [`camel-quarkus-grpc`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-grpc) and [`camel-vertx-websocket`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-vertx-websocket) with Knative.

`gRPC` [needs HTTP2](https://quarkus.io/guides/grpc) (you can find instructions on how to enable it here: [HTTP2 on Knative](https://docs.openshift.com/serverless/1.31/knative-serving/external-ingress-routing/using-http2-gRPC.html) ).

Unfortunately, Websockets with Knative does not work with HTTP2 (see [Ingress Operator in OpenShift Container Platform](https://docs.openshift.com/container-platform/4.15/networking/ingress-operator.html) ).

Consequently, if you have an application that is intended to accept WebSocket connections, it must not allow negotiating the HTTP/2 protocol or else clients will fail to upgrade to the WebSocket protocol.

### [1.7. Known Quarkus CXF issues Copy link](#camel-quarkus-relnotes-known-issues-cxf)

Note

CXF is fully supported, but the following issues remain with this release of Red Hat build of Apache Camel for Quarkus.

#### [1.7.1. Name clash between Service methods with the same name in one Java package Copy link](#name_clash_between_service_methods_with_the_same_name_in_one_java_package)

If there are two SEIs in one Java package, both having a `@WebMethod` with the same name but different signature, then the default name for the generated request, response and possibly other classes is the same for both methods of both classes.

Since Quarkus CXF 3.10.0 and 3.8.4, the problem is detected at build time and the build fails.

### [1.8. Important notes Copy link](#camel-quarkus-relnotes-important-notes)

#### [1.8.1. Support for IBM Power and IBM Z Copy link](#support_for_ibm_power_and_ibm_z)

Red Hat build of Apache Camel for Quarkus is supported on IBM Power and IBM Z.

#### [1.8.2. Minimum Java version - JDK 17 Copy link](#minimum_java_version_jdk_17)

Red Hat build of Apache Camel for Quarkus version 3.15 requires JDK 17 or newer.

#### [1.8.3. Support for OpenJDK Copy link](#support_for_openjdk)

Red Hat build of Apache Camel for Quarkus version 3.15 includes support for OpenJDK 21.

#### [1.8.4. Support for AdoptiumJDK Copy link](#support_for_adoptiumjdk)

Red Hat build of Apache Camel for Quarkus version 3.15 includes support for AdoptiumJDK 17 and AdoptiumJDK 21.

### [1.9. Upgrades Copy link](#camel-quarkus-relnotes-upgrades)

#### [1.9.1. Camel upgraded from version 4.4 to version 4.8 Copy link](#apache-camel-relnotes)

Red Hat build of Apache Camel for Quarkus version 3.15 has been upgraded from Camel version 4.0 to Camel version 4.4. For additional information about each intervening Camel patch release, refer to the following:

- [Apache Camel 4.8.0 Release Notes](https://camel.apache.org/releases/release-4.8.0/)
- [Apache Camel 4.7.0 Release Notes](https://camel.apache.org/releases/release-4.7.0/)
- [Apache Camel 4.6.0 Release Notes](https://camel.apache.org/releases/release-4.6.0/)
- [Apache Camel 4.5.0 Release Notes](https://camel.apache.org/releases/release-4.5.0/)
- [Apache Camel 4.4.4 Release Notes](https://camel.apache.org/releases/release-4.4.4/)
- [Apache Camel 4.4.3 Release Notes](https://camel.apache.org/releases/release-4.4.3/)
- [Apache Camel 4.4.2 Release Notes](https://camel.apache.org/releases/release-4.4.2/)
- [Apache Camel 4.4.1 Release Notes](https://camel.apache.org/releases/release-4.4.1/)

#### [1.9.2. Camel Quarkus upgraded from version 3.8 to version 3.15 Copy link](#apache-camel-quarkus-relnotes)

Red Hat build of Apache Camel for Quarkus version 3.15 has been upgraded from Camel Quarkus version 3.2 to Camel Quarkus version 3.8. For additional information about each intervening Camel Quarkus patch release, refer to the following:

- [Apache Camel Quarkus 3.15.1 Release Notes](https://camel.apache.org/releases/q-3.15.1/)
- [Apache Camel Quarkus 3.15.0 Release Notes](https://camel.apache.org/releases/q-3.15.0/)
- [Apache Camel Quarkus 3.15.0 Release Notes](https://camel.apache.org/releases/q-3.15.0/)
- [Apache Camel Quarkus 3.14.5 Release Notes](https://camel.apache.org/releases/q-3.14.5/)
- [Apache Camel Quarkus 3.14.4 Release Notes](https://camel.apache.org/releases/q-3.14.4/)
- [Apache Camel Quarkus 3.14.3 Release Notes](https://camel.apache.org/releases/q-3.14.3/)
- [Apache Camel Quarkus 3.14.2 Release Notes](https://camel.apache.org/releases/q-3.14.2/)
- [Apache Camel Quarkus 3.14.1 Release Notes](https://camel.apache.org/releases/q-3.14.1/)
- [Apache Camel Quarkus 3.14.0 Release Notes](https://camel.apache.org/releases/q-3.14.0/)
- [Apache Camel Quarkus 3.14.0 Release Notes](https://camel.apache.org/releases/q-3.14.0/)
- [Apache Camel Quarkus 3.13.0 Release Notes](https://camel.apache.org/releases/q-3.13.0/)
- [Apache Camel Quarkus 3.13.0 Release Notes](https://camel.apache.org/releases/q-3.13.0/)
- [Apache Camel Quarkus 3.12.0 Release Notes](https://camel.apache.org/releases/q-3.12.0/)
- [Apache Camel Quarkus 3.12.0 Release Notes](https://camel.apache.org/releases/q-3.12.0/)
- [Apache Camel Quarkus 3.11.7 Release Notes](https://camel.apache.org/releases/q-3.11.7/)
- [Apache Camel Quarkus 3.11.6 Release Notes](https://camel.apache.org/releases/q-3.11.6/)
- [Apache Camel Quarkus 3.11.5 Release Notes](https://camel.apache.org/releases/q-3.11.5/)
- [Apache Camel Quarkus 3.11.4 Release Notes](https://camel.apache.org/releases/q-3.11.4/)
- [Apache Camel Quarkus 3.11.3 Release Notes](https://camel.apache.org/releases/q-3.11.3/)
- [Apache Camel Quarkus 3.11.2 Release Notes](https://camel.apache.org/releases/q-3.11.2/)
- [Apache Camel Quarkus 3.11.1 Release Notes](https://camel.apache.org/releases/q-3.11.1/)
- [Apache Camel Quarkus 3.11.0 Release Notes](https://camel.apache.org/releases/q-3.11.0/)
- [Apache Camel Quarkus 3.11.0 Release Notes](https://camel.apache.org/releases/q-3.11.0/)
- [Apache Camel Quarkus 3.10.0 Release Notes](https://camel.apache.org/releases/q-3.10.0/)
- [Apache Camel Quarkus 3.10.0 Release Notes](https://camel.apache.org/releases/q-3.10.0/)
- [Apache Camel Quarkus 3.9.0 Release Notes](https://camel.apache.org/releases/q-3.9.0/)
- [Apache Camel Quarkus 3.9.0 Release Notes](https://camel.apache.org/releases/q-3.9.0/)
- [Apache Camel Quarkus 3.8.4 Release Notes](https://camel.apache.org/releases/q-3.8.4/)
- [Apache Camel Quarkus 3.8.3 Release Notes](https://camel.apache.org/releases/q-3.8.3/)
- [Apache Camel Quarkus 3.8.2 Release Notes](https://camel.apache.org/releases/q-3.8.2/)
- [Apache Camel Quarkus 3.8.1 Release Notes](https://camel.apache.org/releases/q-3.8.1/)

### [1.10. Resolved issues Copy link](#camel-quarkus-relnotes-resolved)

The following lists shows known issues that were affecting Red Hat build of Apache Camel for Quarkus, which have been fixed in Red Hat build of Apache Camel for Quarkus version 3.15.

Expand

| Issue                                                   | Description                                                                                             |
|---------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| [CEQ-10883](https://issues.redhat.com/browse/CEQ-10883) | CVE-2025-2240 com.redhat.quarkus.platform/quarkus-cxf-bom: SmallRye Fault Tolerance                     |
| [CEQ-10940](https://issues.redhat.com/browse/CEQ-10940) | CVE-2024-57699 quarkus-camel-bom: Potential DoS via stack exhaustion (incomplete fix for CVE-2023-1370) |

Show more

Expand

| Issue                                                   | Description                                                                                              |
|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| [CEQ-10920](https://issues.redhat.com/browse/CEQ-10920) | CVE-2025-27636 org.apache.camel/camel-http: bypass of header filters via specially crafted response      |
| [CEQ-10922](https://issues.redhat.com/browse/CEQ-10922) | CVE-2025-27636 org.apache.camel/camel-http-base: bypass of header filters via specially crafted response |

Show more

Expand

| Issue                                                   | Description                                                                                                                                     |
|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| [CEQ-10793](https://issues.redhat.com/browse/CEQ-10793) | CVE-2025-1247 io.quarkus/quarkus-rest: Quarkus REST Endpoint Request Parameter Leakage Due to Shared Instance                                   |
| [CEQ-10790](https://issues.redhat.com/browse/CEQ-10790) | CVE-2025-24970 io.netty/netty-handler: SslHandler doesn't correctly validate packets which can lead to native crash when using native SSLEngine |
| [CEQ-10850](https://issues.redhat.com/browse/CEQ-10850) | CVE-2025-1634 quarkus-resteasy: Memory Leak in Quarkus RESTEasy Classic When Client Requests Timeout                                            |

Show more

Expand

| Issue                                                   | Description                                                                                                                                                                                                                                                                             |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [CEQ-10644](https://issues.redhat.com/browse/CEQ-10644) | A flaw in Quarkus-HTTP, incorrectly parses cookies with certain value-delimiting characters in incoming requests.  This resolves  [CVE-2024-12397](https://access.redhat.com/security/cve/cve-2024-12397)  com.redhat.quarkus.platform/quarkus-camel-bom: Quarkus HTTP Cookie Smuggling |

Show more

Expand

| Issue                                                   | Description                                                                                                                     |
|---------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| [CEQ-10425](https://issues.redhat.com/browse/CEQ-10425) | ``` camel-platform-http ```  - Consumer should have option to control if writing response failing should cause Exchange to fail |
| [CEQ-10383](https://issues.redhat.com/browse/CEQ-10383) | ``` camel-smb ```  does not work if path != "/"                                                                                 |

Show more

### [1.11. Deprecated features in Red Hat build of Apache Camel for Quarkus version 3.15 Copy link](#camel-quarkus-relnotes-deprecated)

The following capabilities are not available in the next major release of Red Hat build of Apache Camel for Quarkus, and are deprecated in this release.

#### [1.11.1. Openapi-java support for Openapi v2 Copy link](#openapi_java_support_for_openapi_v2)

**Deprecated features**

OpenApi V2 is deprecated in 3.15, due to dropped support in Openapi-java with Camel 4.5.x.

### [1.12. Extensions added in Red Hat build of Apache Camel for Quarkus version 3.15 Copy link](#camel-quarkus-relnotes-extensions-added)

The following table lists the extensions added in the Red Hat build of Apache Camel for Quarkus version 3.15 release .

Expand

|    | Extension                           | Artifact                                                                                                                                                                                                                      | Description                                                                                                                                             |
|----|-------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
|  1 | AWS Secrets Manager                 | [camel-quarkus-aws-secrets-manager](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-aws-secrets-manager)               | Manage AWS Secrets Manager services using AWS SDK version 2.x.                                                                                          |
|  2 | Azure Event Hubs                    | [camel-quarkus-azure-eventhubs](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-azure-eventhubs)                       | Integrates Azure Event Hubs using AMQP protocol.                                                                                                        |
|  3 | Azure Key Vault                     | [camel-quarkus-azure-key-vault](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-azure-key-vault)                       | Manage secrets and keys in Azure Key Vault Service                                                                                                      |
|  4 | Azure Storage Blob                  | [camel-quarkus-azure-storage-blob](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-azure-storage-blob)                 | Store and retrieve blobs from Azure Storage Blob Service using SDK v12.                                                                                 |
|  5 | Azure Storage Queue                 | [camel-quarkus-azure-storage-queue](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-azure-storage-queue)               | Storing and retrieving the messages to/from Azure Storage Queue using Azure SDK v12.                                                                    |
|  6 | Beanio                              | [camel-quarkus-beanio](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-beanio)                                         | Marshal and unmarshal Java beans to and from flat files (such as CSV, delimited, or fixed length formats).                                              |
|  7 | Elasticsearch Low level Rest Client | [camel-quarkus-elasticsearch-rest-client](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-elasticsearch-rest-client)   | Perform queries and other operations on Elasticsearch or OpenSearch (uses low-level client).                                                            |
|  8 | File cluster service                | [camel-quarkus-file-cluster-service](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-file-cluster-service)             | Provides a FileLock implementation of the Camel Cluster Service SPI.                                                                                    |
|  9 | Flink                               | [camel-quarkus-flink](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-flink)                                           | Send DataSet jobs to an Apache Flink cluster.                                                                                                           |
| 10 | Google Secret Manager               | [camel-quarkus-google-secret-manager](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-google-secret-manager)           | Manage Google Secret Manager Secrets                                                                                                                    |
| 11 | GraphQL                             | [camel-quarkus-graphql](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-graphql)                                       | Send GraphQL queries and mutations to external systems.                                                                                                 |
| 12 | Hashicorp Vault                     | [camel-quarkus-hashicorp-vault](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-hashicorp-vault)                       | Manage secrets in Hashicorp Vault Service.                                                                                                              |
| 13 | JQ                                  | [camel-quarkus-jq](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-jq)                                                 | Evaluate JQ expression against a JSON message body.                                                                                                     |
| 14 | Kubernetes Cluster Service          | [camel-quarkus-kubernetes-cluster-service](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-kubernetes-cluster-service) | Provides a Kubernetes implementation of the Camel Cluster Service SPI                                                                                   |
| 15 | Qute                                | [camel-quarkus-qute](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-qute)                                             | Transform messages using Quarkus Qute templating engine.                                                                                                |
| 16 | RabbitMQ                            | [camel-quarkus-spring-rabbitmq](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-spring-rabbitmq)                       | Send and receive messages from RabbitMQ using Spring RabbitMQ client.                                                                                   |
| 17 | SMB                                 | [camel-quarkus-smb](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-smb)                                               | SMB component which consumes natively from file shares using the Server Message Block (SMB, also known as Common Internet File System - CIFS) protocol. |
| 18 | YAML IO                             | [camel-quarkus-yaml-io](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-yaml-io)                                       | Camel XML DSL with camel-xml-io.                                                                                                                        |

Show more

### [1.13. Extensions removed in Red Hat build of Apache Camel for Quarkus version 3.15 Copy link](#camel-quarkus-relnotes-extensions-removed)

No extensions are removed in the Red Hat build of Apache Camel for Quarkus version 3.15 release.

### [1.14. Extensions with changed support in Red Hat build of Apache Camel for Quarkus version 3.15 Copy link](#camel-quarkus-relnotes-extensions-changed-support)

The following table lists the extensions that have changed support levels in the Red Hat build of Apache Camel for Quarkus version 3.15 release.

Expand

| Extension           | Artifact                                  | Description                                                                          |
|---------------------|-------------------------------------------|--------------------------------------------------------------------------------------|
| Azure Storage Blob  | ``` camel-quarkus-azure-storage-blob ```  | Store and retrieve blobs from Azure Storage Blob Service using SDK v12.              |
| Azure Storage Queue | ``` camel-quarkus-azure-storage-queue ``` | Storing and retrieving the messages to/from Azure Storage Queue using Azure SDK v12. |

Show more

Note

For information about support levels, see [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference//camel-quarkus-extensions-overview)

### [1.15. Data formats added in Red Hat build of Apache Camel for Quarkus version 3.15 Copy link](#camel-quarkus-relnotes-added-dataformats)

No data formats have been added in the Red Hat build of Apache Camel for Quarkus version 3.15 release.

### [1.16. Additional resources Copy link](#camel-quarkus-extensions-release-notes)

- [Supported Configurations](https://access.redhat.com/articles/6507531)
- [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/)
- [Getting Started with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/index)
- [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)

## [Legal Notice Copy link](#idm139714383700704)

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