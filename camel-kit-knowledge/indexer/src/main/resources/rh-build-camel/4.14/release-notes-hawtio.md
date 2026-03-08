## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Release Notes for HawtIO Diagnostic Console
2. [Preface](#preface)
3. 1. HawtIO release notes 4.3.0
4. [Legal Notice](#idm140335536657072)

Format Multi-page Single-page View full doc as PDF

# Release Notes for HawtIO Diagnostic Console

Red Hat build of Apache Camel 4.14

## Release Notes for HawtIO Diagnostic Console Guide

[Legal Notice](#idm140335536657072)

**Abstract**

The latest details on what's new in this release of HawtIO.

## [Preface Copy link](#preface)

HawtIO provides enterprise monitoring tools for viewing and managing Red Hat HawtIO-enabled applications. It is a web-based console accessed from a browser to monitor and manage a running HawtIO-enabled container. HawtIO is based on the open source HawtIO software ( [https://hawt.io/](https://hawt.io/) ). [HawtIO Diagnostic Console Guide](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.14/html/hawtio_diagnostic_console_guide/index) describes how to manage applications with HawtIO.

The audience for this guide are Apache Camel eco-system developers and administrators. This guide assumes familiarity with Apache Camel and the processing requirements for your organization.

### Making open source more inclusive

Red Hat is committed to replacing problematic language in our code, documentation, and web properties. We are beginning with these four terms: master, slave, blacklist, and whitelist. Because of the enormity of this endeavor, these changes will be implemented gradually over several upcoming releases. For more details, see [our CTO Chris Wright's message](https://www.redhat.com/en/blog/making-open-source-more-inclusive-eradicating-problematic-language) .

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. HawtIO release notes 4.3.0 Copy link](#camel-hawtio-release-notes_hawtio)

This Release Note contains important information related to HawtIO Diagnostic Console.

### [1.1. HawtIO features Copy link](#hawtio-relnotes-known-versions)

The HawtIO Diagnostic Console includes the following main features:

- Runtime management of JVM via JMX, especially that of Camel applications with specialised views
- Visualisation and debugging/tracing of Camel routes
- Simple management and monitoring of application metrics

### [1.2. Release features Copy link](#hawtio-relnotes-fixed-features-released)

1. **HawtIO Online**
2. **Diagnostics Plugin - Java Flight Recorder**
3. **Multiple Authentication Methods** HawtIO Product 4.3.0 brings support for multiple authentication methods. When logging into Hawtio, user can select one of available (and configurable) authentication methods. Previously, the HawtIO login screen presented one of these options:
screenshot 1

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
screenshot 1

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
screenshot 2

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
screenshot 2

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
8. **Camel View Enhancements**

### [1.3. Release components Copy link](#hawtio-release-components)

For a complete list of release components included in this release, and for information about the current support status of these components, see the [Red Hat build of Apache Camel Component Details Overview](https://access.redhat.com/articles/7036995) .

### [1.4. HawtIO fixed issues Copy link](#hawtio-relnotes-fixed-issues)

This release incorporates all bug fixes from the upstream release. The following list shows issues that were affecting HawtIO, which have been fixed in Red Hat build of Apache Camel 4.14.

[HAWNG-1155](https://issues.redhat.com/browse/HAWNG-1155) Hawtio-Online installed via the Operator requires pod update permission

A code change is required in the gateway jolokia-agent to ensure the following use-cases:

- RBAC is disabled if the user specifies `disabled` as the value of the *HAWTIO\_ONLINE\_RBAC\_ACL* environment variable;
- RBAC is enabled by default with the default installed RBAC rules;
- RBAC is enabled with a set of custom rules provided by a file path as the value of the *HAWTIO\_ONLINE\_RBAC\_ACL* environment variable.

[HAWNG-1055](https://issues.redhat.com/browse/HAWNG-1055) Unsatisfied version 1.9.6-redhat-00001

1. `Unsatisfied version 1.9.6-redhat-00001 from @hawtio/console-assembly of shared singleton module @hawtio/react (required =file:/tmp/hawtio-4.2.0/react-1.9.6-redhat-00001.tgz)` .
2. `Unsatisfied version 1.9.6-redhat-00001 from @hawtio/console-assembly of shared singleton module @hawtio/react (required ^1.9.5)` .
3. Steps to reproduce:

[HAWNG-1088](https://issues.redhat.com/browse/HAWNG-1088) Artemix JMX Charts cause HawtIO Online crashes When navigating to Charts in Artemis JMX, it crashes. Sometimes, the first access to the chart works fine, but the second attempt leads to the application's crash. [HAWNG-1107](https://issues.redhat.com/browse/HAWNG-1107) [Artemis plugin] Unable to validate user from management. Username: null; SSL certificate subject DN: unavailable

When trying to send a message, the following error is displayed:

artemis plugin

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

artemis plugin

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

[HAWNG-1108](https://issues.redhat.com/browse/HAWNG-1108) [hawtio-mirror-4.2.0.redhat-00033] RESOURCE\_LEAK

[https://cov01.lab.eng.brq2.redhat.com/covscanhub/task/910239/log/hawtio-mirror-4.2.0.redhat-00033/scan-results-imp.html](https://cov01.lab.eng.brq2.redhat.com/covscanhub/task/910239/log/hawtio-mirror-4.2.0.redhat-00033/scan-results-imp.html)

`hawtio-system/src/main/java/io/hawt/util/Files.java:49:9` : leaked\_resource: Variable "is" going out of scope leaks the resource it refers to.

[CWE-404](https://cwe.mitre.org/data/definitions/404.html)

### [1.5. HawtIO known issues Copy link](#hawtio-relnotes-known-issues)

The following issue remains with HawtIO for this release:

[HAWNG-1509](https://issues.redhat.com/browse/HAWNG-1509) NPE in &lt;Chart&gt; component

While checking [Artemis Console](https://github.com/apache/activemq-artemis-console) there's an issue with "Charts" tab in Artemis JMX tab (can't reproduce in normal JMX tab).

`chartData` contains a key not available in `attributesToWatch` . The issue is only with Artemis plugin, because it remembers to stay at "Charts" tab when switching MBeans in the tree.

Jolokia jobs are not unregistered and previous one is using stale closured state for `chartData` .

*Revised on 2025-12-10 10:06:00 UTC*

## [Legal Notice Copy link](#idm140335536657072)

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