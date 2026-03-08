## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Release Notes for Red Hat build of Apache Camel for Spring Boot
2. 1. Red Hat build of Apache Camel for Spring Boot 4.14 release notes
3. [2. Additional resources](#additional_resources)
4. [Legal Notice](#idm139963946378368)

Format Multi-page Single-page View full doc as PDF

# Release Notes for Red Hat build of Apache Camel for Spring Boot

Red Hat build of Apache Camel 4.14

## What's new in Red Hat build of Apache Camel

Red Hat build of Apache Camel Documentation Team

[Legal Notice](#idm139963946378368)

**Abstract**

Describes the Red Hat build of Apache Camel product and provides the latest details on what's new in this release.

## [Chapter 1. Red Hat build of Apache Camel for Spring Boot 4.14 release notes Copy link](#camel-spring-boot-relnotes_csb)

### [1.1. Features in Red Hat build of Apache Camel for Spring Boot Copy link](#camel-spring-boot-relnotes-features_csb)

Red Hat build of Apache Camel for Spring Boot introduces Camel support for Spring Boot which provides auto-configuration of Camel, and starters for many Camel components. The opinionated auto-configuration of the Camel context auto-detects Camel routes available in the Spring context and registers key Camel utilities (like producer template, consumer template and the type converter) as beans.

### [1.2. Supported platforms, configurations, databases, and extensions for Red Hat build of Apache Camel for Spring Boot Copy link](#camel-spring-boot-relnotes-supported_csb)

- For information about supported platforms, configurations, and databases in Red Hat build of Apache Camel for Spring Boot, see the [Supported Configuration](https://access.redhat.com/articles/6970899) page on the Customer Portal (login required).
- For a list of Red Hat Red Hat build of Apache Camel for Spring Boot extensions, see the [*Red Hat build of Apache Camel for Spring Boot Reference*](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index) (login required).

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

#### [1.4.1. camel-olingo4 component is deprecated Copy link](#camel-spring-boot-relnotes-migration_csb)

The `camel-olingo4` component is now deprecated. This is due to the project Apache Olingo is no more maintained.

#### [1.4.2. Upgrade to Spring Boot 3.5.x Copy link](#upgrade_to_spring_boot_3_5_x)

Red Hat build of Apache Camel for Spring Boot is upgraded to Spring Boot 3.5.x. For more information about the changes from Spring Boot 3.4 to Spring Boot 3.5, refer [Spring Boot 3.5 Release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes) .

#### [1.4.3. Support for IBM Power and IBM Z Copy link](#support_for_ibm_power_and_ibm_z)

Red Hat build of Camel Spring Boot is now supported on IBM Power and IBM Z.

#### [1.4.4. Changes to the snowdrop groupId Copy link](#changes_to_the_snowdrop_groupid)

The snowdrop groupId is changed from `me.snowdrop` to `dev.snowdrop` . You must update the `pom.xml` file accordingly.

#### [1.4.5. Using the automatic Camel context reloading on Secret Refresh feature of AWS Secret Manager component starter Copy link](#using_the_automatic_camel_context_reloading_on_secret_refresh_feature_of_aws_secret_manager_component_starter)

To use the [Automatic Camel context reloading on Secret Refresh](https://camel.apache.org/components/next/aws-secrets-manager-component.html#_automatic_camel_context_reloading_on_secret_refresh) feature, the secret update has to be done either via UI or via API call with opereation `PutSecretValue` . The camel context reload will not be triggered with executing updateSecret via Camel.

### [1.5. BOM details for Red Hat build of Apache Camel for Spring Boot Copy link](#camel-spring-boot-relnotes-BOM_csb)

Following table lists the Bill of Material versions for Red Hat build of Apache Camel for Spring Boot.

Expand

| Release version   | BOM version         | Spring Boot version   |
|-------------------|---------------------|-----------------------|
| 4.14.4            | 4.14.4.redhat-00010 | 3.5.11                |
| 4.14.2            | 4.14.2.redhat-00015 | 3.5.8                 |
| 4.14.1            | 4.14.1.redhat-00011 | 3.5.7                 |

Show more

### [1.6. Fixed issues for Red Hat build of Apache Camel for Spring Boot Copy link](#csb-resolved-issues_csb)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot.

- [Section 1.6.1, "Red Hat build of Apache Camel for Spring Boot version 4.14.4 fixed issues"](#csb-4_14_4_resolved-issues)
- [Section 1.6.2, "Red Hat build of Apache Camel for Spring Boot version 4.14.2 Patch 1 fixed issues"](#csb-4_14_2_patch_1_resolved-issues)
- [Section 1.6.3, "Red Hat build of Apache Camel for Spring Boot version 4.14.2 fixed issues"](#csb-4_14_2_resolved-issues)
- [Section 1.6.4, "Red Hat build of Apache Camel for Spring Boot version 4.14 fixed issues"](#csb-4_14_resolved-issues)

#### [1.6.1. Red Hat build of Apache Camel for Spring Boot version 4.14.4 fixed issues Copy link](#csb-4_14_4_resolved-issues)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot 4.14.4.

Expand

| Issue                                                 | Description                                                                                                                             |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| [CSB-8095](https://issues.redhat.com/browse/CSB-8095) | CVE-2025-12543 undertow-core: Undertow HTTP Server Fails to Reject Malformed Host Headers Leading to Potential Cache Poisoning and SSRF |
| [CSB-8155](https://issues.redhat.com/browse/CSB-8155) | RemoteFileProduces ignores the result of Noop                                                                                           |
| [CSB-8516](https://issues.redhat.com/browse/CSB-8516) | CVE-2026-1002 vertx-core: static handler component cache can be manipulated to deny the access to static files                          |
| [CSB-8621](https://issues.redhat.com/browse/CSB-8621) | CVE-2026-27727 mchange-commons-java: mchange-commons-java: Arbitrary code execution via JNDI dereferencing of crafted objects           |
| [CSB-8631](https://issues.redhat.com/browse/CSB-8631) | CVE-2026-27830 com.mchange/c3p0: c3p0: Arbitrary Code Execution via deserialization of crafted objects                                  |

Show more

In addition to above issues, Red Hat build of Apache Camel for Spring Boot version 4.14.4 also included several upstream fixes. For more information about these fixes, refer [Resolved issues](https://camel.apache.org/releases/release-4.14.4/#resolved?) .

#### [1.6.2. Red Hat build of Apache Camel for Spring Boot version 4.14.2 Patch 1 fixed issues Copy link](#csb-4_14_2_patch_1_resolved-issues)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot 4.14.2 Patch 1.

Expand

| Issue                                                 | Description                                                                                       |
|-------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| [CSB-8336](https://issues.redhat.com/browse/CSB-8336) | CVE-2025-66566 lz4-java: lz4-java: Information Disclosure via Insufficient Output Buffer Clearing |
| [CSB-8351](https://issues.redhat.com/browse/CSB-8351) | camel-http: useSystemProperties property defined at component level is ignored                    |
| [CSB-8358](https://issues.redhat.com/browse/CSB-8358) | Camel throws NullPointerException when returning a Response with null entity form jax-rs          |
| [CSB-8411](https://issues.redhat.com/browse/CSB-8411) | [CAMEL-22832] camel-azure-storage-blob: upload big files using uploadBlockBlobFromFile            |

Show more

#### [1.6.3. Red Hat build of Apache Camel for Spring Boot version 4.14.2 fixed issues Copy link](#csb-4_14_2_resolved-issues)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot 4.14.2.

Expand

| Issue                                                 | Description                                                                                                                                                     |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [CSB-7692](https://issues.redhat.com/browse/CSB-7692) | CVE-2025-9784 undertow-core: Undertow MadeYouReset HTTP/2 DDoS Vulnerability.                                                                                   |
| [CSB-8164](https://issues.redhat.com/browse/CSB-8164) | CVE-2025-48924 Uncontrolled Recursion vulnerability in Apache Commons Lang.                                                                                     |
| [CSB-8247](https://issues.redhat.com/browse/CSB-8247) | CVE-2025-66516 tika-core: Apache Tika core, Apache Tika parsers, Apache Tika PDF parser module: Update to CVE-2025-54988 to expand scope of artifacts affected. |

Show more

In addition to above issues, Red Hat build of Apache Camel for Spring Boot version 4.14 also included several upstream fixes. For more information about these fixes, refer [Resolved issues](https://camel.apache.org/releases/release-4.14.2/) .

#### [1.6.4. Red Hat build of Apache Camel for Spring Boot version 4.14 fixed issues Copy link](#csb-4_14_resolved-issues)

The following sections list the issues that have been resolved in Red Hat build of Apache Camel for Spring Boot 4.14.

Expand

| Issue                                                 | Description                                                                    |
|-------------------------------------------------------|--------------------------------------------------------------------------------|
| [CSB-6437](https://issues.redhat.com/browse/CSB-6437) | CXF opentelemetry - using same trace id from different http requests on RHEL9  |
| [CSB-6914](https://issues.redhat.com/browse/CSB-6914) | Protobuf jar is not added to the classpath when exporting routes with Kamelets |
| [CSB-7423](https://issues.redhat.com/browse/CSB-7423) | Upgrade to Spring Boot 3.5.x                                                   |
| [CSB-7688](https://issues.redhat.com/browse/CSB-7688) | Support component camel-azure-storage-datalake-starter                         |
| [CSB-7692](https://issues.redhat.com/browse/CSB-7692) | CVE-2025-9784 undertow-core: Undertow MadeYouReset HTTP/2 DDoS Vulnerability   |
| [CSB-7695](https://issues.redhat.com/browse/CSB-7695) | Expose extra micrometer metrics                                                |
| [CSB-7722](https://issues.redhat.com/browse/CSB-7722) | Agroal datasource documentation                                                |
| [CSB-7769](https://issues.redhat.com/browse/CSB-7769) | [JBang] Cannot execute export/run command                                      |
| [CSB-7790](https://issues.redhat.com/browse/CSB-7790) | Jira component error using basic authentication                                |
| [CSB-7793](https://issues.redhat.com/browse/CSB-7793) | Optional AutoConfigure Actuator dependency missing in web applications         |
| [CSB-7796](https://issues.redhat.com/browse/CSB-7796) | Support camel-kafka "breakOnFirstError=true" with "batching=true"              |
| [CSB-8054](https://issues.redhat.com/browse/CSB-8054) | Spring Batch doesn't register custom job bean                                  |

Show more

In addition to above issues, Red Hat build of Apache Camel for Spring Boot version 4.14 also included several upstream fixes. For more information about these fixes, refer [Resolved issues](https://camel.apache.org/releases/release-4.14.1/) .

### [1.7. API changes for Red Hat build of Apache Camel for Spring Boot Copy link](#csb-api-changes_csb)

The following sections list known issues for Red Hat build of Apache Camel for Spring Boot.

#### [1.7.1. API changes in AgroalDataoSourceAutoConfiguration Copy link](#api_changes_in_agroaldataosourceautoconfiguration)

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

#### [1.7.2. Red Hat build of Apache Camel for Spring Boot version 4.10.3 changes API Copy link](#csb-4_10_3_api_changes)

[CSB-6748](https://issues.redhat.com/browse/CSB-6748) Method `AttachmentMessage.getAttachments()` return value changed When you use the method `AttachmentMessage.getAttachments()` it returns an empty list instead of the null value when there are no attachments.

### [1.8. Known issues for Red Hat build of Apache Camel for Spring Boot Copy link](#csb-known-issues_csb)

The following sections list known issues for Red Hat build of Apache Camel for Spring Boot.

#### [1.8.1. Red Hat build of Apache Camel for Spring Boot version 4.10.3 known issues Copy link](#csb-4_10_3_known-issues)

[CSB-6748](https://issues.redhat.com/browse/CSB-6748) Method `AttachmentMessage.getAttachments()` return value changed When you use the method `AttachmentMessage.getAttachments()` it returns an empty list instead of the null value when there are attachments.

#### [1.8.2. Red Hat build of Apache Camel for Spring Boot version 4.8.3 known issues Copy link](#csb-4_8_3_known-issues)

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

#### [1.8.3. Red Hat build of Apache Camel for Spring Boot version 4.8.0 known issues Copy link](#csb-4_8_0_known-issues)

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
- [Getting Started with Red Hat build of Apache Camel for Spring Boot](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/getting_started_with_red_hat_build_of_apache_camel_for_spring_boot/index)
- [Migrating to Red Hat build of Apache Camel for Spring Boot](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/migrating_to_red_hat_build_of_apache_camel_for_spring_boot/index)
- [Red Hat build of Apache Camel for Spring Boot Reference](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14/html-single/red_hat_build_of_apache_camel_for_spring_boot_reference/index)

## [Legal Notice Copy link](#idm139963946378368)

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