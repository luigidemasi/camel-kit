## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Migrating Camel Quarkus projects
2. [Preface](#idm140617633320400)
3. 1. Migrating Camel Quarkus projects
4. [2. Additional resources](#additional_resources)
5. [Legal Notice](#idm140617633193888)

Format Multi-page Single-page View full doc as PDF

# Migrating Camel Quarkus projects

Red Hat build of Apache Camel 4.10

## Migrating Camel Quarkus projects

[Legal Notice](#idm140617633193888)

**Abstract**

Migrating Camel Quarkus projects provides information on migrating from Red Hat Fuse 7 to Red Hat build of Apache Camel for Quarkus.

## [Preface Copy link](#idm140617633320400)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Migrating Camel Quarkus projects Copy link](#fuse-to-camel-migration-projects)

### [1.1. Updating projects to the latest Quarkus version Copy link](#proc_updating-quarkus_cq-migration)

We recommend that you use Maven to update and upgrade your projects to the latest Quarkus version.

Important

For projects that use Hibernate ORM or Hibernate Reactive, review the [Hibernate ORM 5 to 6 migration](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.0:-Hibernate-ORM-5-to-6-migration) quick reference. The following update command covers only a subset of this guide.

#### [1.1.1. Prerequisites Copy link](#prerequisites)

- Roughly 30 minutes
- JDK installed with `JAVA_HOME` configured appropriately
- Apache Maven 3.8.6
- Optionally, the Quarkus CLI if you want to use it
- A project based on Camel Quarkus version 2.13 or later.

### [1.2. Updating with Maven Copy link](#proc_updating-quarkus-maven_cq-migration)

1. Configure your extension registry client as described in the [Configuring Quarkus extension registry client](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.20/html/getting_started_with_red_hat_build_of_quarkus/assembly_quarkus-getting-started_quarkus-getting-started#proc_configuring-quarkus-extension-registry-client_quarkus-getting-started) section of the Quarkus Getting Started guide.
2. Update with Maven: Go to the project directory and update the project to the latest stream:
3. Analyze the update command output for potential instructions and perform the suggested tasks if needed.
4. Use a diff tool to inspect all changes.
5. Review the migration guide for items that were not updated by the update command. If your project has such items, implement the additional steps advised in these topics.
6. Ensure the project builds without errors, all tests pass, and the application functions as required before deploying to production.
7. Before deploying your updated Quarkus application to production, ensure the following:

## Chapter 2. Additional resources

For more information about Red Hat build of Apache Camel for Quarkus, see the following documentation:

- [Red Hat build of Apache Camel for Quarkus Extensions](https://docs.redhat.com/en/documentation/migrating_camel_quarkus_projects/4.10/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/)
- [Getting Started with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/migrating_camel_quarkus_projects/4.10/html-single/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/index)
- [Developing Applications with Red Hat build of Apache Camel for Quarkus](https://docs.redhat.com/en/documentation/migrating_camel_quarkus_projects/4.10/html-single/developing_applications_with_red_hat_build_of_apache_camel_for_quarkus/index)
- [Migrating applications to Red Hat build of Quarkus version 3.20](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.20/html/migrating_applications_to_red_hat_build_of_quarkus_3.20/assembly_migrating-to-quarkus-3_quarkus-migration)

## [Legal Notice Copy link](#idm140617633193888)

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