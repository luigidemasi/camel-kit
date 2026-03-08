## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Migrating Fuse 7 Applications to Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm140675212737856)
3. 1. Overview of migrating Fuse 7 applications to Red Hat build of Apache Camel for Quarkus
4. 2. Migrating Camel Routes from Fuse 7 to Camel
5. [3. Additional resources](#additional_resources)
6. [Legal Notice](#idm140675217200400)

Format Multi-page Single-page View full doc as PDF

# Migrating Fuse 7 Applications to Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.10

## Migrating Fuse 7 Applications to Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm140675217200400)

**Abstract**

Migrating Fuse 7 Applications to Red Hat build of Apache Camel for Quarkus provides information on migrating from Red Hat Fuse 7 to Red Hat build of Apache Camel for Quarkus.

## [Preface Copy link](#idm140675212737856)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Overview of migrating Fuse 7 applications to Red Hat build of Apache Camel for Quarkus Copy link](#fuse-to-camel-migration-overview)

### [1.1. Fuse, Red Hat build of Apache Camel for Quarkus and Camel on EAP Copy link](#migration-overview)

#### [1.1.1. Fuse Copy link](#fuse)

Red Hat Fuse is an agile integration solution based on open source communities like Apache Camel and Apache Karaf. Red Hat Fuse is a lightweight, flexible integration platform that enables rapid on-premise cloud integration.

You can run Red Hat Fuse using three different runtimes:

- Karaf which supports OSGi applications
- Spring Boot
- JBoss EAP (Enterprise Application Platform)

#### [1.1.2. Red Hat build of Apache Camel for Quarkus Copy link](#red_hat_build_of_apache_camel_for_quarkus)

Red Hat build of Apache Camel for Quarkus brings the integration capabilities of Apache Camel and its vast component library to the Quarkus runtime. Red Hat build of Camel Quarkus provides Quarkus extensions for many of the Camel components.

Camel Quarkus takes advantage of the many performance improvements made in Camel 3, which results in a lower memory footprint, less reliance on reflection, and faster startup times.

In a Red Hat build of Apache Camel for Quarkus application, you define Camel routes using Java DSL, so you can migrate the Camel routes that you use in your Fuse application to CEQ.

#### [1.1.3. Camel on EAP Copy link](#camel_on_eap)

Karaf, which follows the OSGI dependency management concept, and EAP, which follows the JEE specification, are application servers impacted by the adoption of containerized applications.

Containers have emerged as the predominant method for packaging applications. Consequently, the responsibility for managing applications, which encompasses deployment, scaling, clustering, and load balancing, has shifted from the application server to the container orchestration using Kubernetes.

Although EAP continues to be supported on Red Hat Openshift, Camel 3 is no longer supported on an EAP server. So if you have a Fuse 7 application running on an EAP server, you should consider migrating your application to the Red Hat Build of Apache Camel for Spring Boot or the Red Hat build of Apache Camel for Quarkus and take the benefit of the migration process to consider a redesign, or partial redesign of your application, from a monolith to a microservices architecture.

If you do not use Openshift, RHEL virtual machines remain a valid approach when you deploy your application for Spring Boot and Quarkus, and Quarkus also benefits from its native compilation capabilities. It is important to evaluate the tooling to support the management of a microservices architecture on such a platform.

Red Hat provides this capability through Ansible, using the [Red Hat Ansible for Middleware collections](https://access.redhat.com/documentation/en-us/red_hat_ansible_automation_platform/2.3) .

### [1.2. Standard migration paths Copy link](#migration-paths)

#### [1.2.1. XML path Copy link](#xml_path)

Fuse applications written in Spring XML or Blueprint XML should be migrated towards an XML-based flavor, and can target either the Spring Boot or the Quarkus runtime with no difference in the migration steps.

#### [1.2.2. Java path Copy link](#java_path)

Fuse applications written in Java DSL should be migrated towards a Java-based flavor, and can target either the Spring Boot or the Quarkus runtime with no difference in the migration steps.

### [1.3. Architectural changes Copy link](#architectural-changes)

Openshift has replaced Fabric8 as the runtime platform for Fuse 6 users and is the recommended target for your Fuse application migration.

You should consider the following architectural changes when you are migrating your application:

- If your Fuse 6 application relied on the Fabric8 service discovery, you should use Kubernetes Service Discovery when running Camel 3 on OpenShift.
- If your Fuse 6 application relies on OSGi bundle configuration, you should use Kubernetes ConfigMaps and Secrets when running Camel 3 on OpenShift.
- If your application uses a file-based route definition, consider using AWS S3 technology when running Camel 3 on OpenShift.
- If your application uses a standard filesystem, the resulting Spring Boot or Quarkus applications should be deployed on standard RHEL virtual machines rather than the Openshift platform.
- Delegation of Inbound HTTPS connections to the Openshift Router which handles SSL requirements.
- Delegation of Hystrix features to [Service Mesh](https://www.redhat.com/en/technologies/cloud-computing/openshift/what-is-openshift-service-mesh) .

### [1.4. The javax to jakarta package namespace change Copy link](#javax-jakarta)

The Java EE move to the Eclipse Foundation and the establishment of Jakarta EE, since Jakarta EE 9, packages used for all EE APIs have changed to `jakarta.*`

Code snippets in documentation have been updated to use the `jakarta.*` namespace, but you of course need to take care and review your own applications.

Note

This change does not affect javax packages that are part of Java SE.

When migrating applications to EE 10, you need to:

- Update any import statements or other source code uses of EE API classes from the `javax` package to `jakarta` .
- Change any EE-specified system properties or other configuration properties whose names begin with `javax.` to begin with `jakarta.` .
- Use the `META-INF/services/jakarta.[rest_of_name]` name format to identify implementation classes in your applications that use the implement EE interfaces or abstract classes bootstrapped with the `java.util.ServiceLoader` mechanism.

#### [1.4.1. Migration tools Copy link](#migration_tools)

- Source code migration: [How to use Red Hat Migration Toolkit for Auto-Migration of an Application to the Jakarta EE 10 Namespace](https://access.redhat.com/articles/6987195)
- Bytecode transforms: For cases where source code migration is not an option, the open source [Eclipse Transformer](https://github.com/eclipse/transformer)

**Additional resources**

- Background: [Update on Jakarta EE Rights to Java Trademarks](https://blogs.eclipse.org/post/mike-milinkovich/update-jakarta-ee-rights-java-trademarks)
- Red Hat Customer Portal: [Red Hat JBoss EAP Application Migration from Jakarta EE 8 to EE 10](https://access.redhat.com/login?redirectTo=https%3A%2F%2Faccess.redhat.com%2Farticles%2F6980265%23javax_jakarta)
- Jakarta EE: [Javax to Jakarta Namespace Ecosystem Progress](https://jakarta.ee/blogs/javax-jakartaee-namespace-ecosystem-progress/)

## [Chapter 2. Migrating Camel Routes from Fuse 7 to Camel Copy link](#fuse-to-camel-migration-routes)

Note

You can define Camel routes in Red Hat build of Apache Camel for Quarkus applications using Java DSL, XML IO DSL, or YAML.

### [2.1. Java DSL route migration example Copy link](#java-dsl-route-migration-example)

To migrate a Java DSL route definition from your Fuse application to CEQ, you can copy your existing route definition directly to your Red Hat build of Apache Camel for Quarkus application and add the necessary dependencies to your Red Hat build of Apache Camel for Quarkus pom.xml file.

In this example, we will migrate a content-based route definition from a Fuse 7 application to a new CEQ application by copying the Java DSL route to a file named `Routes.java` in your CEQ application.

**Procedure**

1. Using the `code.quarkus.redhat.com` website, select the extensions required for this example:
2. Navigate to the directory where you extracted the generated project files from the previous step: `$ cd <directory_name>` Copy to Clipboard Toggle word wrap
3. Create a file named `Routes.java` in the `src/main/java/org/acme/` subfolder.
4. Add the route definition from your Fuse application to the `Routes.java` , similar to the following example: `package org.acme; import org.apache.camel.builder.RouteBuilder; public class Routes extends RouteBuilder { // Add your Java DSL route definition here public void configure() { from("file:work/cbr/input") .log("Receiving order ${file:name}") .choice() .when().xpath("//order/customer/country[text() = 'UK']") .log("Sending order ${file:name} to the UK") .to("file:work/cbr/output/uk") .when().xpath("//order/customer/country[text() = 'US']") .log("Sending order ${file:name} to the US") .to("file:work/cbr/output/uk") .otherwise() .log("Sending order ${file:name} to another country") .to("file:work/cbr/output/others"); } }` Copy to Clipboard Toggle word wrap
5. Compile your CEQ application. `mvn clean compile quarkus:dev` Copy to Clipboard Toggle word wrap

Note

This command compiles the project, starts your application, and lets the Quarkus tooling watch for changes in your workspace. Any modifications in your project will automatically take effect in the running application.

### [2.2. Blueprint XML DSL route migration Copy link](#blueprint-xml-dsl-route-migration)

To migrate a Blueprint XML route definition from your Fuse application to CEQ, use the `camel-quarkus-xml-io-dsl` extension and copy your Fuse application route definition directly to your CEQ application. You will then need to add the necessary dependencies to the CEQ `pom.xml` file and update your CEQ configuration in the `application.properties` file.

Note

CEQ supports Camel 3, whereas Fuse 7 supports Camel 2.

For more information relating to upgrading Camel when you migrate your Red Hat Fuse 7 application to CEQ, see [Migrating Apache Camel](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/%7Bversion%7D/html-single/migrating_apache_camel/index) .

For more information about using beans in Camel Quarkus, see the [CDI and the Camel Bean Component](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/%7Bversion%7D/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index#cdi_and_the_camel_bean_component) section in the *Developing Applications with Red Hat build of Apache Camel for Quarkus* guide.

#### [2.2.1. XML-IO-DSL limitations Copy link](#xml_io_dsl_limitations)

You can use the `camel-quarkus-xml-io-dsl` extension to assist with migrating a Blueprint XML route definition to CEQ.

The `camel-quarkus-xml-io-dsl` extension only supports the following `<camelContext>` sub-elements:

- routeTemplates
- templatedRoutes
- rests
- routes
- routeConfigurations

Note

As Blueprint XML supports other bean definitions that are not supported by the `camel-quarkus-xml-io-dsl` extension, you may need to rewrite other bean definitions that are included in your Blueprint XML route definition.

You must define every element (XML IO DSL) in a separate file. For example, this is a simplified example of a Blueprint XML route definition:

```
<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0">
    <camelContext xmlns="http://camel.apache.org/schema/blueprint">
        <restConfiguration contextPath="/camel" />
        <rest path="/books">
            <get uri="/">
                <to ..../>
            </get>
        </rest>
        <route>
            <from ..../>
        </route>
    </camelContext>
</blueprint>
```

Copy to Clipboard

Toggle word wrap

You can migrate this Blueprint XML route definition to CEQ using XML IO DSL as defined in the following files:

**src/main/resources/routes/camel-rests.xml**

```
<rests xmlns="http://camel.apache.org/schema/spring">
    <rest path="/books">
    <get path="/">
        <to ..../>
    </get>
    </rest>
</rests>
```

Copy to Clipboard

Toggle word wrap

**src/main/resources/routes/camel-routes.xml**

```
<routes xmlns="http://camel.apache.org/schema/spring">
    <route>
        <from ..../>
    </route>
</routes>
```

Copy to Clipboard

Toggle word wrap

You must use Java DSL to define other elements which are not supported, such as `<restConfiguration>` . For example, using a route builder defined in a `camel-rests.xml` file as follows:

**src/main/resources/routes/camel-rests.xml**

```
import org.apache.camel.builder.RouteBuilder;
public class Routes extends RouteBuilder {
    public void configure() {
       restConfiguration()
            .contextPath("/camel");
    }
}
```

Copy to Clipboard

Toggle word wrap

#### [2.2.2. Blueprint XML DSL route migration example Copy link](#blueprint_xml_dsl_route_migration_example)

Note

For more information about using the XML IO DSL extension, see the [XML IO DSL](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/%7Bversion%7D/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#extensions-xml-io-dsl) documentation in the Red Hat build of Apache Camel for Quarkus Extensions.

In this example, you are migrating a content-based route definition from a Fuse application to a new CEQ application by copying the Blueprint XML route definition to a file named `camel-routes.xml` in your CEQ application.

**Procedure**

1. Using the `code.quarkus.redhat.com` website, select the following extensions for this example:
2. Select *Generate your application* to confirm your choices and display the overlay screen with the download link for the archive that contains your generated project.
3. Select Download the ZIP to save the archive with the generated project files to your machine.
4. Extract the contents of the archive.
5. Navigate to the directory where you extracted the generated project files from the previous step: `$ cd <directory_name>` Copy to Clipboard Toggle word wrap
6. Create a file named `camel-routes.xml` in the `src/main/resources/routes/` directory.
7. Copy the `<route>` element and sub-elements from the following `blueprint-example.xml` example to the `camel-routes.xml` file: **blueprint-example.xml** `<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0"> <camelContext id="cbr-example-context" xmlns="http://camel.apache.org/schema/blueprint"> <route id="cbr-route"> <from id="_from1" uri="file:work/cbr/input"/> <log id="_log1" message="Receiving order ${file:name}"/> <choice id="_choice1"> <when id="_when1"> <xpath id="_xpath1">/order/customer/country = 'UK'</xpath> <log id="_log2" message="Sending order ${file:name} to the UK"/> <to id="_to1" uri="file:work/cbr/output/uk"/> </when> <when id="_when2"> <xpath id="_xpath2">/order/customer/country = 'US'</xpath> <log id="_log3" message="Sending order ${file:name} to the US"/> <to id="_to2" uri="file:work/cbr/output/us"/> </when> <otherwise id="_otherwise1"> <log id="_log4" message="Sending order ${file:name} to another country"/> <to id="_to3" uri="file:work/cbr/output/others"/> </otherwise> </choice> <log id="_log5" message="Done processing ${file:name}"/> </route> </camelContext> </blueprint>` Copy to Clipboard Toggle word wrap **camel-routes.xml** `<route id="cbr-route"> <from id="_from1" uri="file:work/cbr/input"/> <log id="_log1" message="Receiving order ${file:name}"/> <choice id="_choice1"> <when id="_when1"> <xpath id="_xpath1">/order/customer/country = 'UK'</xpath> <log id="_log2" message="Sending order ${file:name} to the UK"/> <to id="_to1" uri="file:work/cbr/output/uk"/> </when> <when id="_when2"> <xpath id="_xpath2">/order/customer/country = 'US'</xpath> <log id="_log3" message="Sending order ${file:name} to the US"/> <to id="_to2" uri="file:work/cbr/output/us"/> </when> <otherwise id="_otherwise1"> <log id="_log4" message="Sending order ${file:name} to another country"/> <to id="_to3" uri="file:work/cbr/output/others"/> </otherwise> </choice> <log id="_log5" message="Done processing ${file:name}"/> </route>` Copy to Clipboard Toggle word wrap
8. Modify `application.properties # Camel # camel.context.name = camel-quarkus-xml-io-dsl-example camel.main.routes-include-pattern = file:src/main/resources/routes/camel-routes.xml` Copy to Clipboard Toggle word wrap
9. Compile your CEQ application. `mvn clean compile quarkus:dev` Copy to Clipboard Toggle word wrap Note This command compiles the project, starts your application, and lets the Quarkus tooling watch for changes in your workspace. Any modifications in your project will automatically take effect in the running application.

## Chapter 3. Additional resources

For more information about Red Hat build of Apache Camel for Quarkus, see the following documentation:

- [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/%7Bversion%7D/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/)
- [Getting Started with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/%7Bversion%7D/html-single/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/index)
- [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/%7Bversion%7D/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)
- [Migrating applications to Red Hat build of Quarkus version 3.20](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.20/html/migrating_applications_to_red_hat_build_of_quarkus_3.20/assembly_migrating-to-quarkus-3_quarkus-migration)

## [Legal Notice Copy link](#idm140675217200400)

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