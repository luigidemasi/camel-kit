## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Release Notes for Red Hat build of Apache Camel for Spring Boot
2. 1. Red Hat build of Apache Camel for Spring Boot 4.10 release notes
3. [2. Additional resources](#additional_resources)
4. [Legal Notice](#idm140171094813168)

Format Multi-page Single-page View full doc as PDF

# Release Notes for Red Hat build of Apache Camel for Spring Boot

Red Hat build of Apache Camel 4.10

## What's new in Red Hat build of Apache Camel

Red Hat build of Apache Camel Documentation Team

[Legal Notice](#idm140171094813168)

**Abstract**

Describes the Red Hat build of Apache Camel product and provides the latest details on what's new in this release.

## [Chapter 1. Red Hat build of Apache Camel for Spring Boot 4.10 release notes Copy link](#camel-spring-boot-relnotes_csb)

### [1.1. Features in Red Hat build of Apache Camel for Spring Boot Copy link](#camel-spring-boot-relnotes-features_csb)

Red Hat build of Apache Camel for Spring Boot introduces Camel support for Spring Boot which provides auto-configuration of Camel, and starters for many Camel components. The opinionated auto-configuration of the Camel context auto-detects Camel routes available in the Spring context and registers key Camel utilities (like producer template, consumer template and the type converter) as beans.

### [1.2. Supported platforms, configurations, databases, and extensions for Red Hat build of Apache Camel for Spring Boot Copy link](#camel-spring-boot-relnotes-supported_csb)

- For information about supported platforms, configurations, and databases in Red Hat build of Apache Camel for Spring Boot, see the [Supported Configuration](https://access.redhat.com/articles/6970899) page on the Customer Portal (login required).
- For a list of Red Hat Red Hat build of Apache Camel for Spring Boot extensions, see the [*Red Hat build of Apache Camel for Spring Boot Reference*](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index) (login required).

### [1.3. The javax to jakarta Package Namespace Change Copy link](#camel-spring-boot-relnotes-jakarta-changes_csb)

The Java EE move to the Eclipse Foundation and the establishment of Jakarta EE, since Jakarta EE 9, packages used for all EE APIs have changed to `jakarta.*`

Code snippets in documentation have been updated to use the `jakarta.*` namespace, but you of course need to take care and review your own applications.

Note

This change does not affect javax packages that are part of Java SE.

When migrating applications to EE 10, you need to:

- Update any import statements or other source code uses of EE API classes from the `javax` package to `jakarta` .
- Change any EE-specified system properties or other configuration properties whose names begin with `javax.` to begin with `jakarta.` .
- Use the `META-INF/services/jakarta.[rest_of_name]` name format to identify implementation classes in your applications that use the implement EE interfaces or abstract classes bootstrapped with the `java.util.ServiceLoader` mechanism.

#### [1.3.1. Migration tools Copy link](#migration_tools)

- Source code migration: [How to use Red Hat Migration Toolkit for Auto-Migration of an Application to the Jakarta EE 10 Namespace](https://access.redhat.com/articles/6987195)
- Bytecode transforms: For cases where source code migration is not an option, the open source [Eclipse Transformer](https://github.com/eclipse/transformer)

**Additional resources**

- Background: [Update on Jakarta EE Rights to Java Trademarks](https://blogs.eclipse.org/post/mike-milinkovich/update-jakarta-ee-rights-java-trademarks)
- Red Hat Customer Portal: [Red Hat JBoss EAP Application Migration from Jakarta EE 8 to EE 10](https://access.redhat.com/login?redirectTo=https%3A%2F%2Faccess.redhat.com%2Farticles%2F6980265%23javax_jakarta)
- Jakarta EE: [Javax to Jakarta Namespace Ecosystem Progress](https://jakarta.ee/blogs/javax-jakartaee-namespace-ecosystem-progress/)

### [1.4. Important notes for Red Hat build of Apache Camel for Spring Boot Copy link](#camel-spring-boot-relnotes-important_csb)

#### [1.4.1. Support for IBM Power and IBM Z Copy link](#camel-spring-boot-relnotes-migration_csb)

Red Hat build of Camel Spring Boot is now supported on IBM Power and IBM Z.

#### [1.4.2. Changes to the snowdrop groupId Copy link](#changes_to_the_snowdrop_groupid)

The snowdrop groupId is changed from `me.snowdrop` to `dev.snowdrop` . You must update the `pom.xml` file accordingly.

#### [1.4.3. Using the automatic Camel context reloading on Secret Refresh feature of AWS Secret Manager component starter Copy link](#using_the_automatic_camel_context_reloading_on_secret_refresh_feature_of_aws_secret_manager_component_starter)

To use the [Automatic Camel context reloading on Secret Refresh](https://camel.apache.org/components/next/aws-secrets-manager-component.html#_automatic_camel_context_reloading_on_secret_refresh) feature, the secret update has to be done either via UI or via API call with opereation `PutSecretValue` . The camel context reload will not be triggered with executing updateSecret via Camel.

### [1.5. Fixed issues for Red Hat build of Apache Camel for Spring Boot Copy link](#csb-resolved-issues_csb)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot.

- [Section 1.5.1, "Red Hat build of Apache Camel for Spring Boot version 4.10.7 fixed issues"](#csb-4_10_7_resolved-issues)
- [Section 1.5.2, "Red Hat build of Apache Camel for Spring Boot version 4.10.6 fixed issues"](#csb-4_10_6_resolved-issues)
- [Section 1.5.3, "Red Hat build of Apache Camel for Spring Boot version 4.10 Patch 1 fixed issues"](#csb-4_10_3_patch_1_resolved-issues)
- [Section 1.5.4, "Red Hat build of Apache Camel for Spring Boot version 4.10 fixed issues"](#csb-4_10_resolved-issues)

#### [1.5.1. Red Hat build of Apache Camel for Spring Boot version 4.10.7 fixed issues Copy link](#csb-4_10_7_resolved-issues)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot 4.10.7.

Expand

| Issue                                                 | Description                                                                                                             |
|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| [CSB-7724](https://issues.redhat.com/browse/CSB-7724) | CVE-2025-58056 netty-codec-http: Netty is vulnerable to request smuggling due to incorrect parsing of chunk extensions  |
| [CSB-7727](https://issues.redhat.com/browse/CSB-7727) | CVE-2025-58056 netty-codec-http2: Netty is vulnerable to request smuggling due to incorrect parsing of chunk extensions |
| [CSB-7774](https://issues.redhat.com/browse/CSB-7774) | CVE-2025-41248 spring-security-core: Spring Security authorization bypass                                               |
| [CSB-7777](https://issues.redhat.com/browse/CSB-7777) | CVE-2025-41249 spring-core: Spring Framework Annotation Detection Vulnerability                                         |
| [CSB-7780](https://issues.redhat.com/browse/CSB-7780) | CVE-2025-41249 spring-core-test: Spring Framework Annotation Detection Vulnerability                                    |
| [CSB-7783](https://issues.redhat.com/browse/CSB-7783) | CVE-2025-41249 org.springframework/spring-core: Spring Framework Annotation Detection Vulnerability                     |
| [CSB-7802](https://issues.redhat.com/browse/CSB-7802) | CVE-2025-4949 org.eclipse.jgit: XXE vulnerability in Eclipse JGit                                                       |
| [CSB-7889](https://issues.redhat.com/browse/CSB-7889) | CVE-2025-59952 minio: minio-java Client XML Tag is Vulnerable to Value Substitution                                     |
| [CSB-7892](https://issues.redhat.com/browse/CSB-7892) | CVE-2025-59952 io.minio/minio: minio-java Client XML Tag is Vulnerable to Value Substitution                            |

Show more

In addition to above issues, Red Hat build of Apache Camel for Spring Boot version 4.10.7 also included several upstream fixes. For more information about these fixes, refer [Resolved issues](https://camel.apache.org/releases/release-4.10.7/#resolved) .

#### [1.5.2. Red Hat build of Apache Camel for Spring Boot version 4.10.6 fixed issues Copy link](#csb-4_10_6_resolved-issues)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot 4.10.6.

Expand

| Issue                                                 | Description                                                                                                                                                                       |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [CSB-7265](https://issues.redhat.com/browse/CSB-7265) | camel-resilience4j-starter record/ignore exception should handle wrapped exceptions                                                                                               |
| [CSB-7284](https://issues.redhat.com/browse/CSB-7284) | Support component camel-rest-openapi                                                                                                                                              |
| [CSB-7417](https://issues.redhat.com/browse/CSB-7417) | http component as producer fails if bridgeEndpoint=true                                                                                                                           |
| [CSB-7429](https://issues.redhat.com/browse/CSB-7429) | camel-http-starter uses community version of httpclient5                                                                                                                          |
| [CSB-7472](https://issues.redhat.com/browse/CSB-7472) | CXF producer appends http path to the requested http endpoint                                                                                                                     |
| [CSB-7516](https://issues.redhat.com/browse/CSB-7516) | rest-openapi context is not correct                                                                                                                                               |
| [CSB-7519](https://issues.redhat.com/browse/CSB-7519) | CVE-2025-55163 netty-codec-http2: Netty MadeYouReset HTTP/2 DDoS Vulnerability                                                                                                    |
| [CSB-7522](https://issues.redhat.com/browse/CSB-7522) | CVE-2025-5115 jetty-http2-client: HTTP/2 (including DNS over HTTPS) contains a design flaw and is vulnerable to "MadeYouReset" DoS attack through HTTP/2 control frames           |
| [CSB-7525](https://issues.redhat.com/browse/CSB-7525) | CVE-2025-5115 jetty-http2-client-transport: HTTP/2 (including DNS over HTTPS) contains a design flaw and is vulnerable to "MadeYouReset" DoS attack through HTTP/2 control frames |
| [CSB-7528](https://issues.redhat.com/browse/CSB-7528) | CVE-2025-5115 jetty-http2-common: HTTP/2 (including DNS over HTTPS) contains a design flaw and is vulnerable to "MadeYouReset" DoS attack through HTTP/2 control frames           |
| [CSB-7531](https://issues.redhat.com/browse/CSB-7531) | CVE-2025-5115 jetty-http2-hpack: HTTP/2 (including DNS over HTTPS) contains a design flaw and is vulnerable to "MadeYouReset" DoS attack through HTTP/2 control frames            |
| [CSB-7538](https://issues.redhat.com/browse/CSB-7538) | Camel-smb component does not recover properly after SMB server has network issue / resets connection                                                                              |

Show more

#### [1.5.3. Red Hat build of Apache Camel for Spring Boot version 4.10 Patch 1 fixed issues Copy link](#csb-4_10_3_patch_1_resolved-issues)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot version 4.10 Patch 1.

Expand

| Issue                                                 | Description                                                                                                                                   |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| [CSB-6995](https://issues.redhat.com/browse/CSB-6995) | jbang dependency command does not show/handle the version                                                                                     |
| [CSB-7061](https://issues.redhat.com/browse/CSB-7061) | CVE-2024-13009 jetty-server: Jetty: Gzip Request Body Buffer Corruption                                                                       |
| [CSB-7069](https://issues.redhat.com/browse/CSB-7069) | CVE-2025-41232 spring-security-core: Spring Security authorization bypass for method security annotations on private methods                  |
| [CSB-7079](https://issues.redhat.com/browse/CSB-7079) | CVE-2025-48734 commons-beanutils: Apache Commons BeanUtils: PropertyUtilsBean does not suppresses an enum's declaredClass property by default |
| [CSB-7133](https://issues.redhat.com/browse/CSB-7133) | CVE-2025-49146 postgresql: pgjdbc insecure authentication in channel binding                                                                  |

Show more

#### [1.5.4. Red Hat build of Apache Camel for Spring Boot version 4.10 fixed issues Copy link](#csb-4_10_resolved-issues)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot version 4.10.

Expand

| Issue                                                   | Description                                                                                                              |
|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| [CSB-4171](https://issues.redhat.com/browse/CSB-4171)   | Add support for camel-graphql                                                                                            |
| [CSB-4661](https://issues.redhat.com/browse/CSB-4661)   | Support for plain CXF SOAP scenarios                                                                                     |
| [CSB-4995](https://issues.redhat.com/browse/CSB-4995)   | Add support for Kamelets and Pipes                                                                                       |
| [CSB-5483](https://issues.redhat.com/browse/CSB-5483)   | Odd behaviour of <toD> with header substitution                                                                          |
| [CSB-5648](https://issues.redhat.com/browse/CSB-5648)   | jaxws:client's address placeholder is not getting resolved when configured in XML                                        |
| [CSB-5823](https://issues.redhat.com/browse/CSB-5823)   | Redshift kamelet: Failed to configure a DataSource                                                                       |
| [CSB-5875](https://issues.redhat.com/browse/CSB-5875)   | camel-platform-http-starter does not implement all the features in restConfiguration                                     |
| [CSB-6248](https://issues.redhat.com/browse/CSB-6248)   | camel-jaxb - JaxbDataFormat ignoreJAXBElement is default true                                                            |
| [CSB-6282](https://issues.redhat.com/browse/CSB-6282)   | Create an example of the implementation for Camel's Route Security using Spring Security                                 |
| [CSB-6304](https://issues.redhat.com/browse/CSB-6304)   | [Doc] Lack of instruction to install SAP JCo and SAP IDoc libraries into the "lib/" directory of the Java runtime on OCP |
| [CSB-6358](https://issues.redhat.com/browse/CSB-6358)   | Support component camel-observability-services                                                                           |
| [CSB-6464](https://issues.redhat.com/browse/CSB-6464)   | Additional Information Needed in CamelLivenessStateHealthIndicator Logs                                                  |
| [CSB-6466](https://issues.redhat.com/browse/CSB-6466)   | javax dependencies on camel-kubernetes                                                                                   |
| [CSB-6469](https://issues.redhat.com/browse/CSB-6469)   | Support component camel-ssh                                                                                              |
| [CSB-6527](https://issues.redhat.com/browse/CSB-6527)   | camel-infinispan-starter doesn't work with latest productized JDG                                                        |
| [CSB-6687](https://issues.redhat.com/browse/CSB-6687)   | Support component camel-smooks                                                                                           |
| [CSB-6739](https://issues.redhat.com/browse/CSB-6739)   | Dependency org.apache.camel/camel-console increases the building times                                                   |
| [CSB-6742](https://issues.redhat.com/browse/CSB-6742)   | Red Hat Build of Apache Camel BOM includes UPSTREAM Artemis BOM                                                          |
| [CSB-6761](https://issues.redhat.com/browse/CSB-6761)   | EIP: endChoice() cannot get the parent in nested choices                                                                 |
| [CSB-6766](https://issues.redhat.com/browse/CSB-6766)   | strimzi quickstart / get rid of kubernetes profile                                                                       |
| [CSB-6812](https://issues.redhat.com/browse/CSB-6812)   | [CAMEL-22001]camel-core - Kamelet and EIPs should propagate exchange variables                                           |
| [CSB-6855](https://issues.redhat.com/browse/CSB-6855)   | kamelets: camel export fails due to Bean not found                                                                       |
| [CSB-6865](https://issues.redhat.com/browse/CSB-6865)   | [AWS-Kinesis] Error on kinesisClient bean when using KCL Consumer mode                                                   |
| [CSB-6936](https://issues.redhat.com/browse/CSB-6936)   | jbang export causes java.lang.ClassNotFoundException: org.apache.camel.kamelets.catalog.KameletsCatalog                  |
| [CSB-6943](https://issues.redhat.com/browse/CSB-6943)   | Kamelet ExtractField references wrong Camel ExtractField class                                                           |
| [CSB-6946](https://issues.redhat.com/browse/CSB-6946)   | MongoDB version is misaligned                                                                                            |
| [CSB-6955](https://issues.redhat.com/browse/CSB-6955)   | CVE-2025-1948 jetty-http2-common: Jetty HTTP/2 Header List Size Vulnerability                                            |
| [RHBAC-127](https://issues.redhat.com/browse/RHBAC-127) | Backport CAMEL-21828: Fix DefaultHeaderFilterStrategy when filtering in lower-case mode                                  |
| [RHBAC-142](https://issues.redhat.com/browse/RHBAC-142) | Backport CAMEL-21876 - Undertow Header Filter Strategy: Considering also the in filter                                   |
| [RHBAC-145](https://issues.redhat.com/browse/RHBAC-145) | Camel CLI export command does not treat the product version                                                              |

Show more

### [1.6. API changes for Red Hat build of Apache Camel for Spring Boot Copy link](#csb-api-changes_csb)

The following sections list known issues for Red Hat build of Apache Camel for Spring Boot.

#### [1.6.1. Red Hat build of Apache Camel for Spring Boot version 4.10.3 changes API Copy link](#csb-4_10_3_api_changes)

[CSB-6748](https://issues.redhat.com/browse/CSB-6748) Method `AttachmentMessage.getAttachments()` return value changed When you use the method `AttachmentMessage.getAttachments()` it returns an empty list instead of the null value when there are no attachments.

### [1.7. Known issues for Red Hat build of Apache Camel for Spring Boot Copy link](#csb-known-issues_csb)

The following sections list known issues for Red Hat build of Apache Camel for Spring Boot.

#### [1.7.1. Red Hat build of Apache Camel for Spring Boot version 4.10.3 known issues Copy link](#csb-4_10_3_known-issues)

[CSB-6748](https://issues.redhat.com/browse/CSB-6748) Method `AttachmentMessage.getAttachments()` return value changed When you use the method `AttachmentMessage.getAttachments()` it returns an empty list instead of the null value when there are attachments.

#### [1.7.2. Red Hat build of Apache Camel for Spring Boot version 4.8.3 known issues Copy link](#csb-4_8_3_known-issues)

[CSB-6437](https://issues.redhat.com/browse/CSB-6437) CXF opentelemetry - using same trace id from different http requests on RHEL9 This issue only occurs with CXF and OpenTelemetry with the custom tracing configuration on RHEL 9 platform. ,In case of CXF and opentelemetry with the custom opentelemetry tracer defined, when you call multiple requests, the trace ID seems to be reused in the Camel routes. The result is that new spans are added in the existing traces for each http request, instead of creating one new trace ID for each http request. The workaround is to explicitly define the `ContextPropagators` to `W3CTraceContextPropagator` as shown in the example below:

```
@Bean
    ContextPropagators contextPropagators() {
       // return ContextPropagators.create(TextMapPropagator.composite(W3CBaggagePropagator.getInstance()));
       return ContextPropagators.create(W3CTraceContextPropagator.getInstance());
    }
```

Copy to Clipboard

Toggle word wrap

#### [1.7.3. Red Hat build of Apache Camel for Spring Boot version 4.8.0 known issues Copy link](#csb-4_8_0_known-issues)

[CSB-4318](https://issues.redhat.com/browse/CSB-4318) Fail to deploy on OCP using Openshift Maven Plugin if spring.boot.actuator.autoconfigure is not in the dependencies

Jkube maven plugin uses the following condition to check if the application exposes health endpoint (using `SpringBootHealthCheckEnricher` ). Both classes are in the classpath:

- org.springframework.boot.actuate.health.HealthIndicator
- org.springframework.web.context.support.GenericWebApplicationContext

However, the `/actuator/health` wil be not exposed without the configuration of the actuator. This creates discordance between the readiness/liveness probes configured by JKube (they both uses the above endpoint) and what the application is exposing.

This misconfiguration causes a failing deployment config on OpenShift Container Platform since the generated pod will never be in Ready status since the probe`s call for an endpoint is not configured. So in order to make the application work on OpenShift Container Platform, which is deployed using JKube (openshift-maven-plugin), it is necessary to have both web and actuator autoconfiguration in the dependencies.

Following example shows how to configure web and actuator autoconfiguration.

**Example**

```
<dependency>
 <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

Update the archetype as shown below. The applications built from the following archetype will be deployed correctly using JKube.

```
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
      <exclusion>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-tomcat</artifactId>
      </exclusion>
    </exclusions>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

This issue affects the custom applications with missing one of the above dependencies.

## Chapter 2. Additional resources

- [Supported Configurations](https://access.redhat.com/articles/6970899)
- [Getting Started with Red Hat build of Apache Camel for Spring Boot](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/getting_started_with_red_hat_build_of_apache_camel_for_spring_boot/index)
- [Migrating to Red Hat build of Apache Camel for Spring Boot](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/migrating_to_red_hat_build_of_apache_camel_for_spring_boot/index)
- [Red Hat build of Apache Camel for Spring Boot Reference](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.10/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index)

## [Legal Notice Copy link](#idm140171094813168)

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