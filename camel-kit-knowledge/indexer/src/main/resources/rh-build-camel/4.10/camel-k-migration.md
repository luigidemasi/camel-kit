## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Migrating from Camel K to Red Hat build of Apache Camel for Quarkus
2. [Preface](#idm140353049530528)
3. 1. Migrating from Camel-K
4. [Legal Notice](#idm140353041749648)

Format Multi-page Single-page View full doc as PDF

# Migrating from Camel K to Red Hat build of Apache Camel for Quarkus

Red Hat build of Apache Camel 4.10

## Developing Applications with Red Hat build of Apache Camel for Quarkus

[Legal Notice](#idm140353041749648)

**Abstract**

This guide is for developers writing Camel applications on top of Red Hat build of Apache Camel for Quarkus.

## [Preface Copy link](#idm140353049530528)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. To create a ticket, click this link: [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Migrating from Camel-K Copy link](#migrate_camel_k_overview)

### [1.1. Introduction Copy link](#migrate_camel_k_introduction)

Camel K is set to deprecate in favor of an unified Camel approach to OpenShift. Targeting the Red Hat Build of Camel for Quarkus, we aim to provide existing customers with a migration path to transition their Camel K integrations.

This approach ensures a seamless migration to the Red Hat Build of Apache Camel for Quarkus, requiring minimal effort while considering the supported features of both Camel K and the Red Hat Build of Apache Camel for Quarkus.

Note

This guide requires you to already know how to build, configure, deploy and run applications in the Quarkus.

### [1.2. Assumptions Copy link](#migrate_camel_k_assumptions)

- The required source files to migrate are in Java, XML or Yaml.
- The target system to deploy is an OpenShift Cluster 4.18+.
- Camel K version is 1.10.10.
- The migration is to the Red Hat build of Apache Camel.

Camel K operates using the Kamel CLI to run integrations, while the Camel K Operator manages and deploys them as running pods along with various Kubernetes objects, including Deployment, Service, Route, ConfigMap, Secret, and Knative.

Note

The running java program is a Camel on Quarkus application.

When using the Red Hat build of Apache Camel for Quarkus, the starting point is a Maven project that contains all the artifacts needed to build and run the integration. This project will include a Deployment, Service, ConfigMap, and other resources, although their configurations may differ from those in Camel K. For instance, properties might be stored in an application.properties file, and Knative configurations may require separate files. The main goal is to ensure the integration route is deployed and running in an OpenShift cluster.

#### [1.2.1. Requirements Copy link](#requirements)

To perform the migration, following set of tools and configurations are required.

- Link:{camel-manual-url}camel-jbang.html#\_installation[Camel JBang {jbang-version}]
- JDK {jdk-version-all}
- Maven (mvn cli) 3.9.5
- [oc cli](https://mirror.openshift.com/pub/openshift-v4/clients/ocp/4.18.17/)
- OpenShift cluster 4.18+

Explore the [Supported Configurations](https://access.redhat.com/articles/7037134) and [Component Details](https://access.redhat.com/articles/7036995) about Red Hat build of Apache Camel.

#### [1.2.2. Out of scope Copy link](#out_of_scope)

- Use of Camel Spring Boot (CSB) as a target. The migration path is similar but should be tailored for CSB and JKube. Refer the [documentation](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.4#Red%20Hat%20build%20of%20Apache%20Camel%20for%20Spring%20Boot) for numerous [examples](https://github.com/jboss-fuse/camel-spring-boot-examples) .
- OpenShift management.
- Customization of maven project.

#### [1.2.3. Use cases Copy link](#use_cases)

Camel K integrations can vary, typically consisting of several files that correspond to integration routes and configurations. The integration routes may be defined in Java, XML, or YAML, while configurations can be specified in properties files or as parameters in the `kamel run` command. This migration document addresses use cases involving KameletBinding, Kamelet, Knative, and properties in ConfigMap.

#### [1.2.4. Versions Copy link](#versions)

Note

Camel K 1.10.7 uses different versions of Camel and Quarkus that the Red Hat build of Apache Camel for Quarkus.

Expand

| Artifact          | Camel K                 | Red Hat build of Apache Camel for Quarkus   |
|-------------------|-------------------------|---------------------------------------------|
| JDK               | 11                      | 21 (preferred), 17 (supported)              |
| Camel             | 3.18.6.redhat-00009     | 4.4.0.redhat-00025                          |
| Camel for Quarkus | 2.13.3.redhat-00011     | 3.8.0.redhat-00006                          |
| Quarkus Platform  | 2.13.9.SP2-redhat-00003 | 3.8.5.redhat-00003                          |
| Kamelet Catalog   | 1.10.7                  | 2.3.x                                       |

Show more

Migrating from Camel K to Red Hat build of Apache Camel for Quarkus updates several libraries simultaneously. Therefore, you may encounter some errors when building or running the integration in Red Hat build of Apache Camel for Quarkus, due to differences in the underlying libraries.

#### [1.2.5. Project and Organization Copy link](#project_and_organization)

Camel K integration routes originate from a single file in java, yaml or xml. There is no concept of a project to organize the dependencies and builds. At the end, each `kamel run <my app>` results in a running pod.

Red Hat build of Apache Camel for Quarkus requires a maven project. Use the `camel export <many files>` to generate the maven project. On building the project, the container image contains all the integration routes defined in the project.

If you want one pod for each integration route, you must create a maven project for each integration route. While there are many complex ways to use a single maven project with multiple integration routes and custom builds to generate container images with different run entrypoints to start the pod, this is beyond the scope of this migration guide.

### [1.3. Traits Copy link](#migrate_camel_k_traits)

Traits in Camel K provide an easy way for the operator, to materialize parameters from kamel cli to kubernetes objects and configurations. Only a few traits are supported in Camel K 1.10, that are covered in this migration path. There is no need to cover the configuration in the migration path for the following traits: camel, platform, deployment, dependencies, deployer, openapi.

The following list contains the traits with their parameters and equivalents in Red Hat build of Apache Camel for Quarkus.

Note

The properties for Red Hat build of Apache Camel for Quarkus must be set in `application.properties` . On building the project, kubernetes appearing in `target/kubernetes/openshift.yml` must contain the properties.

For more information about properties, see [Quarkus OpenShift Extension](https://quarkus.io/guides/deploying-to-openshift) .

Expand

| Trait Parameter            | Quarkus Parameter                                     |
|----------------------------|-------------------------------------------------------|
| ``` builder.properties ``` | Add the properties to  ``` application.properties ``` |

Show more

Expand

| Trait Parameter                               | Quarkus Parameter                                                                                                                                                                                                                                                                                                                         |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` container.expose ```                      | The  ``` Service ```  kubernetes object is created automatically.                                                                                                                                                                                                                                                                         |
| ``` container.image ```                       | No replacement in Quarkus, since this property was meant for sourceless Camel K integrations, which are not supported in Red Hat build of Apache Camel for Quarkus.                                                                                                                                                                       |
| ``` container.limit-cpu ```                   | ``` quarkus.openshift.resources.limits.cpu ```                                                                                                                                                                                                                                                                                            |
| ``` container.limit-memory ```                | ``` quarkus.openshift.resources.limits.memory ```                                                                                                                                                                                                                                                                                         |
| ``` container.liveness-failure-threshold ```  | ``` quarkus.openshift.liveness-probe.failure-threshold ```                                                                                                                                                                                                                                                                                |
| ``` container.liveness-initial-delay ```      | ``` quarkus.openshift.liveness-probe.initial-delay ```                                                                                                                                                                                                                                                                                    |
| ``` container.liveness-period ```             | ``` quarkus.openshift.liveness-probe.period ```                                                                                                                                                                                                                                                                                           |
| ``` container.liveness-success-threshold ```  | ``` quarkus.openshift.liveness-probe.success-threshold ```                                                                                                                                                                                                                                                                                |
| ``` container.liveness-timeout ```            | ``` quarkus.openshift.liveness-probe.timeout ```                                                                                                                                                                                                                                                                                          |
| ``` container.name ```                        | ``` quarkus.openshift.container-name ```                                                                                                                                                                                                                                                                                                  |
| ``` container.port ```                        | ``` quarkus.openshift.ports."<port name>".container-port ```                                                                                                                                                                                                                                                                              |
| ``` container.port-name ```                   | Set the port name in the property name. The syntax is:  ``` quarkus.openshift.ports."<port name>".container-port ```  . Example for  ``` https ```  port is  ``` quarkus.openshift.ports.https.container-port ```  .                                                                                                                      |
| ``` container.probes-enabled ```              | Add the quarkus maven dependency to the pom.xml  ``` <dependency>       <groupId>io.quarkus</groupId>       <artifactId>quarkus-smallrye-health</artifactId>   </dependency> ```  Copy to Clipboard  Toggle word wrap  It will also add the startup probe to the container. Note that, delay, timeout and period values may be different. |
| ``` container.readiness-failure-threshold ``` | ``` quarkus.openshift.readiness-probe.failure-threshold ```                                                                                                                                                                                                                                                                               |
| ``` container.readiness-initial-delay ```     | ``` quarkus.openshift.readiness-probe.initial-delay ```                                                                                                                                                                                                                                                                                   |
| ``` container.readiness-period ```            | ``` quarkus.openshift.readiness-probe.period ```                                                                                                                                                                                                                                                                                          |
| ``` container.readiness-success-threshold ``` | ``` quarkus.openshift.readiness-probe.success-threshold ```                                                                                                                                                                                                                                                                               |
| ``` container.readiness-timeout ```           | ``` quarkus.openshift.readiness-probe.timeout ```                                                                                                                                                                                                                                                                                         |
| ``` container.request-cpu ```                 | ``` quarkus.openshift.resources.requests.cpu ```                                                                                                                                                                                                                                                                                          |
| ``` container.request-memory ```              | ``` quarkus.openshift.resources.requests.memory ```                                                                                                                                                                                                                                                                                       |
| ``` container.service-port ```                | ``` quarkus.openshift.ports.<port-name>.host-port ```                                                                                                                                                                                                                                                                                     |
| ``` container.service-port-name ```           | Set the port name in the property name. The syntax is:  ``` quarkus.openshift.ports."<port name>".host-port ```  . Example for  ``` https ```  port is  ``` quarkus.openshift.ports.https.host-port ```  .  Also, ensure to set the route port name to  ``` quarkus.openshift.route.target-port ```  .                                    |

Show more

Expand

| Trait Parameter                | Quarkus Parameter                                                                                                                                                      |
|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` environment.vars ```       | ``` quarkus.openshift.env.vars.<key>=<value> ```                                                                                                                       |
| ``` environment.http-proxy ``` | You must set the proxy host with the values of:  ``` quarkus.kubernetes-client.http-proxy quarkus.kubernetes-client.https-proxy quarkus.kubernetes-client.no-proxy ``` |

Show more

Expand

| Trait Parameter           | Quarkus Parameter                                                                                                         |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------|
| ``` error-handler.ref ``` | You must manually add the  [Error Handler](https://camel.apache.org/manual/error-handler.html)  in the integration route. |

Show more

Expand

| Trait Parameter           | Quarkus Parameter                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` jvm.debug ```         | ``` quarkus.openshift.remote-debug.enabled ```                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ``` jvm.debug-suspend ``` | ``` quarkus.openshift.remote-debug.suspend ```                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ``` jvm.print-command ``` | No replacement.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ``` jvm.debug-address ``` | ``` quarkus.openshift.remote-debug.address-port ```                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| ``` jvm.options ```       | Edit  ``` src/main/docker/Dockerfile.jvm ```  and change the  ``` JAVA_OPTS ```  value to set the desired values.  Example to increase the camel log level to debug:  ``` ENV JAVA_OPTS="$JAVA_OPTS -Dquarkus.log.category.\"org.apache.camel\".level=debug" ```  Copy to Clipboard  Toggle word wrap  Note: The Docker configuration is dependent on the base image,  [configuration for OpenJDK 21](https://jboss-container-images.github.io/openjdk/ubi8-openjdk-containers-1.20/ubi8-openjdk-21.html#_configuration_variables)  . |
| ``` jvm.classpath ```     | You must set the classpath at the maven project, so the complete list of dependencies are collected in the  ``` target/quarkus-app/ ```  and later packaged in the containter image.                                                                                                                                                                                                                                                                                                                                                  |

Show more

Expand

| Trait Parameter                                                                                                                                                                            | Quarkus Parameter                                        |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|
| ``` affinity.pod-affinity affinity.pod-affinity-labels affinity.pod-anti-affinity affinity.pod-anti-affinity-labels affinity.node-affinity-labels ```  Copy to Clipboard  Toggle word wrap | There is no  ``` affinity ```  configuration in Quarkus. |

Show more

Expand

| Trait Parameter       | Quarkus Parameter                                     |
|-----------------------|-------------------------------------------------------|
| ``` owner.enabled ``` | There is no  ``` owner ```  configuration in Quarkus. |

Show more

Expand

| Trait Parameter              | Quarkus Parameter                           |
|------------------------------|---------------------------------------------|
| ``` quarkus.package-type ``` | For native builds, use  ``` -Dnative ```  . |

Show more

Expand

| Trait Parameter                        | Quarkus Parameter                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` knative.enabled ```                | Add the maven dependency  ``` org.apache.camel.quarkus:camel-quarkus-knative ```  to the pom.xml, and set the following properties:  ``` quarkus.kubernetes.deployment-target=knative quarkus.container-image.group=<group-name> quarkus.container-image.registry=image-registry.openshift-image-registry.svc:5000 ```  Copy to Clipboard  Toggle word wrap  The  ``` quarkus.container-image.* ```  properties are required by the quarkus maven plugin to set the image url in the generated knative.yml. |
| ``` knative.configuration ```          | ``` camel.component.knative.environmentPath ```                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| ``` knative.channel-sources ```        | Configurable in the knative.json.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ``` knative.channel-sinks ```          | Configurable in the knative.json.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ``` knative.endpoint-sources ```       | Configurable in the knative.json.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ``` knative.endpoint-sinks ```         | Configurable in the knative.json.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ``` knative.event-sources ```          | Configurable in the knative.json.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ``` knative.event-sinks ```            | Configurable in the knative.json.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ``` knative.filter-source-channels ``` | Configurable in the knative.json.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ``` knative.sink-binding ```           | No replacement, you must create the  ``` SinkBinding ```  object.                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ``` knative.auto ```                   | No replacement.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| ``` knative.namespace-label ```        | You must set the label  ``` bindings.knative.dev/include=true ```  manually to the desired namespace.                                                                                                                                                                                                                                                                                                                                                                                                       |

Show more

Expand

| Trait Parameter                            | Quarkus Parameter                                                                                  |
|--------------------------------------------|----------------------------------------------------------------------------------------------------|
| ``` knative-service.enabled ```            | ``` quarkus.kubernetes.deployment-target=knative ```                                               |
| ``` knative-service.annotations ```        | ``` quarkus.knative.annotations.<annotation-name>=<value> ```                                      |
| ``` knative-service.autoscaling-class ```  | ``` quarkus.knative.revision-auto-scaling.auto-scaler-class ```                                    |
| ``` knative-service.autoscaling-metric ``` | ``` quarkus.knative.revision-auto-scaling.metric ```                                               |
| ``` knative-service.autoscaling-target ``` | ``` quarkus.knative.revision-auto-scaling.target ```                                               |
| ``` knative-service.min-scale ```          | ``` quarkus.knative.min-scale ```                                                                  |
| ``` knative-service.max-scale ```          | ``` quarkus.knative.max-scale ```                                                                  |
| ``` knative-service.rollout-duration ```   | ``` quarkus.knative.annotations."serving.knative.dev/rollout-duration" ```                         |
| ``` knative-service.visibility ```         | ``` quarkus.knative.labels."networking.knative.dev/visibility" ```  It must be in quotation marks. |
| ``` knative-service.auto ```               | This behavior is unnecessary in Red Hat build of Apache Camel for Quarkus.                         |

Show more

Expand

| Trait Parameter                       | Quarkus Parameter                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` prometheus.enabled ```            | Add the following maven dependencies to pom.xml  ``` <dependency>     <groupId>org.apache.camel.quarkus</groupId>     <artifactId>camel-quarkus-micrometer</artifactId> </dependency> <dependency>     <groupId>io.quarkus</groupId>     <artifactId>quarkus-micrometer-registry-prometheus</artifactId> </dependency> ```  Copy to Clipboard  Toggle word wrap  Note: Camel K creates a  ``` PodMonitor ```  object, while Quarkus creates a  ``` ServiceMonitor ```  object, both are correct to configure the monitoring feature. |
| ``` prometheus.pod-monitor ```        | ``` quarkus.openshift.prometheus.generate-service-monitor ```                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ``` prometheus.pod-monitor-labels ``` | No quarkus property is available to set custom labels, but you can configure the labels in  ``` ServiceMonitor ```  object in  ``` target/kubernetes/openshift.yml ```  before deploying.                                                                                                                                                                                                                                                                                                                                            |

Show more

Expand

| Trait Parameter                                                                                | Quarkus Parameter                                                            |
|------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| ``` pdb.enabled pdb.min-available pdb.max-unavailable ```  Copy to Clipboard  Toggle word wrap | There is no Quarkus configuration for  ``` PodDisruptionBudget ```  objects. |

Show more

Expand

| Trait Parameter                 | Quarkus Parameter                            |
|---------------------------------|----------------------------------------------|
| ``` pull-secret.secret-name ``` | ``` quarkus.openshift.image-pull-secrets ``` |

Show more

Expand

| Trait Parameter                                     | Quarkus Parameter                                                               |
|-----------------------------------------------------|---------------------------------------------------------------------------------|
| ``` route.enabled ```                               | ``` quarkus.openshift.route.expose ```                                          |
| ``` route.annotations ```                           | ``` quarkus.openshift.route.annotations.<key>=<value ```                        |
| ``` route.host ```                                  | ``` quarkus.openshift.route.host ```                                            |
| ``` route.tls-termination ```                       | ``` quarkus.openshift.route.tls.termination ```                                 |
| ``` route.tls-certificate ```                       | ``` quarkus.openshift.route.tls.certificate ```                                 |
| ``` route.tls-certificate-secret ```                | There is no quarkus property to read the certificate from a secret.             |
| ``` route.tls-key ```                               | ``` quarkus.openshift.route.tls.key ```                                         |
| ``` route.tls-key-secret ```                        | There is no quarkus property to read the key from a secret.                     |
| ``` route.tls-ca-certificate ```                    | ``` quarkus.openshift.route.tls.ca-certificate ```                              |
| ``` route.tls-ca-certificate-secret ```             | There is no quarkus property to read the CA certificate from a secret.          |
| ``` route.tls-destination-ca-certificate ```        | ``` quarkus.openshift.route.tls.destination-ca-certificate ```                  |
| ``` route.tls-destination-ca-certificate-secret ``` | There is no quarkus property to read the destination certificate from a secret. |
| ``` route.tls-insecure-edge-termination-policy ```  | ``` quarkus.openshift.route.tls.insecure-edge-termination-policy ```            |

Show more

Expand

| Trait Parameter         | Quarkus Parameter                                                                                                                                                                              |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` service.enabled ``` | The  ``` Service ```  kubernetes object is created automatically. To disable it, you must remove the  ``` kind: Service ```  from  ``` target/kubernetes/openshift.yml ```  before deployment. |

Show more

### [1.4. Kamel run configuration Copy link](#migrate_camel_k_kamel_run_configuration)

There are additional configuration parameters in the `kamel run` command listed below, along with their equivalents in the Red Hat build of Apache Camel for Quarkus, which must be added in `src/main/resources/application.properties` or `pom.xml` .

Expand

| kamel run parameter        | Quarkus Parameter                                                                             |
|----------------------------|-----------------------------------------------------------------------------------------------|
| ``` --annotation ```       | ``` quarkus.openshift.annotations.<annotation-name>=<value> ```                               |
| ``` --build-property ```   | Add the property in the  ``` <properties> ```  tag of the  ``` pom.xml ```  .                 |
| ``` --dependency ```       | Add the dependency in  ``` pom.xml ```  .                                                     |
| ``` --env ```              | ``` quarkus.openshift.env.vars.<env-name>=<value> ```                                         |
| ``` --label ```            | ``` quarkus.openshift.labels.<label-name>=<value> ```                                         |
| ``` --maven-repository ``` | Add the repository in  ``` pom.xml ```  or use the  ``` camel export --repos=<my repo> ```  . |
| ``` --logs ```             | ``` oc logs -f `oc get pod -l app.kubernetes.io/name=<artifact name> -oname` ```              |
| ``` --volume ```           | ``` quarkus.openshift.mounts.<my-volume>.path=</where/to/mount ```  &gt;                      |

Show more

### [1.5. Kamelets, KameletBindings and Pipes Copy link](#migrate_camel_k_kamelets_bindings_pipes)

Camel K operator bundles the Kamelets and installs them as kubernetes objects. For Red Hat build of Apache Camel for Quarkus project, you must manage kamelets yaml files in the maven project.

There are two ways to manage the kamelets yaml files.

1. [Kamelets](https://github.com/apache/camel-kamelets) are packaged and released as maven artifact `org.apache.camel.kamelets:camel-kamelets` . You can add this dependency to `pom.xml` , and when the camel route starts, it loads the kamelet yaml files from that jar file in classpath.

There are opensource kamelets and the ones produced by Red Hat, whose artifact suffix is `redhat-000nnn` . For example:`1.10.7.redhat-00015`. These are available from the [Red Hat maven repository](https://maven.repository.redhat.com/ga/org/apache/camel/kamelets/camel-kamelets-catalog/) .

2.Add the kamelet yaml files in `src/main/resources/kamelets` directory, that are later packaged in the final deployable artifact. Do not declare the `org.apache.camel.kamelets:camel-kamelets` in `pom.xml` . This way, the camel route loads the Kamelet yaml file from the packaged project.

`KameletBinding` was renamed to `Pipe` . So consider this to understand the use case 3. While the kubernetes resource name `KameletBinding` is still supported, it is deprecated. We recommend renaming it to `Pipe` as soon as possible.

We recommend to update the Kamelets, as there were many updates since Camel K 1.10.7. For example, you can compare the jms-amqp-10-sink.kamelet.yaml of [1.10](https://github.com/openshift-integration/kamelet-catalog/blob/kamelet-catalog-1.10/jms-amqp-10-sink.kamelet.yaml) and [2.3](https://github.com/openshift-integration/kamelet-catalog/blob/release-2.3.x/jms-amqp-10-sink.kamelet.yaml)

If you have custom Kamelets, you must update them accordingly.

- rename `flow` to `template` in Kamelet files.
- rename `property` to `properties` for the bean properties.

#### [1.5.1. Knative Copy link](#knative)

When running integration routes with Knative endpoints in Camel K, the Camel K Operator creates some Knative objects such as: `SinkBindings` , `Trigger` , `Subscription` . Also, Camel K Operator creates the `knative.json` environment file, required for camel-knative component to interact with the Knative objects deployed in the cluster.

**Example of a knative.json**

```
{
  "services": [
    {
      "type": "channel",
      "name": "messages",
      "url": "{{k.sink}}",
      "metadata": {
        "camel.endpoint.kind": "sink",
        "knative.apiVersion": "messaging.knative.dev/v1",
        "knative.kind": "Channel",
        "knative.reply": "false"
      }
    }
  ]
}
```

Copy to Clipboard

Toggle word wrap

Red Hat build of Apache Camel for Quarkus is a maven project. You must create those Knative files manually and provide additional configuration. See use case 2 for the migration of an integration route with Knative endpoints.

#### [1.5.2. Monitoring Copy link](#monitoring)

We recommend you to add custom labels to identify the kubernetes objects installed in the cluster, to allow an easier way to locate these kubernetes. By default, the quarkus openshift extension adds the label `app.kubernetes.io/name=<app name>` , so you can search the objects created using this label.

For monitoring purposes, you can use the [HawtIO Diagnostic Console](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.4/html/hawtio_diagnostic_console_guide/index) to monitor the Camel applications.

### [1.6. Migration Process Copy link](#migrate_camel_k_migration_process)

The migration process is composed of the following steps.

Expand

| Task                     | Description                                                                                                   |
|--------------------------|---------------------------------------------------------------------------------------------------------------|
| Create the maven project | Use the camel cli from Camel JBang to export the files, it will create a maven project.                       |
| Adjust the configuration | Configure the project by adding and changing files.                                                           |
| Build                    | Building the project will generate the JAR files. Build the container image and push to a container registry. |
| Deploy                   | Deploy the kubernetes objects to the Openshift cluster and run the pod.                                       |

Show more

#### [1.6.1. Migration Steps Copy link](#migration_steps)

##### [1.6.1.1. Use Case 1 - Simple Integration Route with Configuration Copy link](#use_case_1_simple_integration_route_with_configuration)

Given the following integration route, featuring rest and kamelet endpoints.

```
import org.apache.camel.builder.RouteBuilder;

public class Http2Jms extends RouteBuilder {
  @Override
  public void configure() throws Exception {
      rest()
      .post("/message")
      .id("rest")
      .to("direct:jms");

      from("direct:jms")
      .log("Sending message to JMS {{broker}}: ${body}")
      .to("kamelet:jms-amqp-10-sink?remoteURI=amqp://myhost:61616&destinationName=queue");
  }
}
```

Copy to Clipboard

Toggle word wrap

The http2jms.properties file

```
broker=amqp://172.30.177.216:61616
queue=qtest
```

Copy to Clipboard

Toggle word wrap

The kamel run command

```
kamel run Http2Jms.java -p file://$PWD/http2jms.properties --annotation some_annotation=foo --env MY_ENV1=VAL1
```

Copy to Clipboard

Toggle word wrap

It builds and runs the pod with the annotations. Environment variable and the properties file are added as a ConfigMap and mounted in the pod.

##### [1.6.1.1.1. Step 1 - Create the maven project Copy link](#step_1_create_the_maven_project)

Use camel jbang to export the file into a maven project.

```
camel export \
--runtime = quarkus \ --quarkus-group-id = com.redhat.quarkus.platform \ --quarkus-version = 3.8 .5.redhat-00003 \
--repos = https://maven.repository.redhat.com/ga \
--dep = io.quarkus:quarkus-openshift \
--gav = com.mycompany:ceq-app:1.0 \
--dir = ceq-app1 \ Http2Jms.java
```

Copy to Clipboard

Toggle word wrap

Description of the parameters:

Expand

| Parameter                                              | Description                                                                                                                                                     |
|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` --runtime=quarkus ```                              | Use the Quarkus runtime. The generated project contains the quarkus BOM.                                                                                        |
| ``` --quarkus-group-id=com.redhat.quarkus.platform ``` | The Red Hat supported quarkus platform maven artifact group is  ``` com.redhat.quarkus.platform ```  .                                                          |
| ``` --quarkus-version=3.8.5.redhat-00003 ```           | This is the latest supported version at the time. Check the  [Quarkus documentation](https://access.redhat.com/articles/7040491)  for a recent release version. |
| ``` --repos=https://maven.repository.redhat.com/ga ``` | Use the Red Hat Maven repository with the GA releases.                                                                                                          |
| ``` --dep=io.quarkus:quarkus-openshift ```             | Adds the quarkus-openshift dependency to pom.xml ,to build in Openshift.                                                                                        |
| ``` --gav=com.mycompany:ceq-app:1.0 ```                | Set a GAV to the generated pom.xml. You must set a GAV accordingly to your project.                                                                             |
| ``` --dir=ceq-app1 ```                                 | The maven project directory.                                                                                                                                    |

Show more

You can see more parameters with `camel export --help`

If you are using kamelets, it must be part of the maven project. You can download the [Kamelet repository](https://github.com/openshift-integration/kamelet-catalog/blob/release-2.3.x) and unzip it. If you have any custom kamelets, add them to this kamelet directory.

While using `camel export` , you can use the parameter `--local-kamelet-dir=<kamelet directory>` that copies all kamelets to `src/main/resources/kamelets` , which are later packed into the final archive.

If you choose not to use the `--local-kamelet-dir=<kamelet directory>` parameter, then you must manually copy the desired kamelet yaml files to the above mentioned directory.

Track the artifact name in the generated pom, as the artifact name is used in the generated Openshift files (Deployment, Service, Route, etc.).

##### [1.6.1.1.2. Step 2 - Configure the project Copy link](#step_2_configure_the_project)

This is the step to configure the maven project and artifacts to suit your environment.

Get into the maven project

```
cd ceq-app1
```

Copy to Clipboard

Toggle word wrap

Set the `docker` build strategy.

```
echo quarkus.openshift.build-strategy = docker >> src/main/resources/application.properties
```

Copy to Clipboard

Toggle word wrap

Change the base image to OpenJDK 21 in `src/main/docker` (optional)

```
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
```

Copy to Clipboard

Toggle word wrap

Change the compiler version to 21 in pom.xml (optional)

```
<maven.compiler.release>21</maven.compiler.release>
```

Copy to Clipboard

Toggle word wrap

Set the environment variables, labels and annotations in `src/main/resources/application.properties` , if you need them.

```
quarkus.openshift.annotations.sample_annotation=sample_value1
quarkus.openshift.env.vars.SAMPLE_KEY=sample_value2
quarkus.openshift.labels.sample_label=sample_value3
```

Copy to Clipboard

Toggle word wrap

If you want to customize the image and container registry settings with these parameters:

```
quarkus.container-image.registry
quarkus.container-image.group
quarkus.container-image.name
quarkus.container-image.tag
```

Copy to Clipboard

Toggle word wrap

As there is a `http2jms.properties` with configuration used at runtime, kamel cli creates a ConfigMap and mount it in the pod. We must achieve the same with Red Hat build of Apache Camel for Quarkus.

Create a local ConfigMap file named `ceq-app in `src/main/kubernetes/common.yml` which will be a part of the image build process. The following command sets the ConfigMap key as `application.properties`

```
oc create configmap ceq-app --from-file application.properties = http2jms.properties --dry-run = client -oyaml > src/main/kubernetes/common.yml
```

Copy to Clipboard

Toggle word wrap

Add the following property to `application.properties` , for Quarkus to mount the `ConfigMap` .

```
quarkus.openshift.app-config-map=ceq-app
```

Copy to Clipboard

Toggle word wrap

##### [1.6.1.1.3. Step 3 - Build Copy link](#step_3_build)

Build the package for local inspection.

```
./mvnw -ntp package
```

Copy to Clipboard

Toggle word wrap

This step builds the maven artifacts (JAR files) locally and generates the Openshift files in `target/kubernetes` directory.

Track the `target/kubernetes/openshift.yml` to understand the deployment that is deployed to the Openshift cluster.

##### [1.6.1.1.4. Step 4 - Build and Deploy Copy link](#step_4_build_and_deploy)

Build the package and deploy to Openshift

```
./mvnw -ntp package -Dquarkus.openshift.deploy = true
```

Copy to Clipboard

Toggle word wrap

You can follow the image build in the maven output. After the build, you can see the pod running.

##### [1.6.1.1.5. Step 5 - Test Copy link](#step_5_test)

Verify if the integration route is working.

If the project can run locally, you can try the following.

```
mvn -ntp quarkus:run
```

Copy to Clipboard

Toggle word wrap

Follow the pod container log

```
oc logs -f ` oc get pod -l app.kubernetes.io/name = app -oname `
```

Copy to Clipboard

Toggle word wrap

It must show something like the following output:

```
INFO exec -a "java" java -Dquarkus.http.host = 0.0 .0.0 -Djava.util.logging.manager = org.jboss.logmanager.LogManager -cp "." -jar /deployments/quarkus-run.jar
INFO running in /deployments
__  ____  __  _____   ___  __ ____  ______
--/ __ \ / / / / _ | / _ \ / //_/ / / / __/
-/ /_/ / /_/ / __ | / , _/ , < / /_/ / \ \ -- \ ___ \ _ \ ____/_/ | _/_/ | _/_/ | _ | \ ____/___/ [ org.apa.cam.qua.cor.CamelBootstrapRecorder ] ( main ) Bootstrap runtime: org.apache.camel.quarkus.main.CamelMainRuntime [ org.apa.cam.mai.MainSupport ] ( main ) Apache Camel ( Main ) 4.4 .0.redhat-00025 is starting [ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Apache Camel 4.4 .0.redhat-00025 ( camel-1 ) is starting [ org.apa.cam.mai.BaseMainSupport ] ( main ) Property-placeholders summary [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ MicroProfilePropertiesSource ] broker = amqp://172.30.177.216:61616 [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ MicroProfilePropertiesSource ] queue = qtest [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ ms-amqp-10-sink.kamelet.yaml ] destinationName = qtest [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ ms-amqp-10-sink.kamelet.yaml ] connectionFactoryBean = connectionFactoryBean-1 [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ ms-amqp-10-sink.kamelet.yaml ] remoteURI = amqp://172.30.177.216:61616 [ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Routes startup ( started:3 )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started route1 ( direct://jms )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started rest ( rest://post:/message )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started jms-amqp-10-sink-1 ( kamelet://source )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Apache Camel 4.4 .0.redhat-00025 ( camel-1 ) started in 17ms ( build:0ms init:0ms start:17ms )
[ io.quarkus ] ( main ) app 1.0 on JVM ( powered by Quarkus 3.8 .5.redhat-00004 ) started in 1 .115s. Listening on: http://0.0.0.0:8080 [ io.quarkus ] ( main ) Profile prod activated. [ io.quarkus ] ( main ) Installed features: [ camel-amqp, camel-attachments, camel-core, camel-direct, camel-jms, camel-kamelet, camel-microprofile-health, camel-platform-http, camel-rest, camel-rest-openapi, camel-yaml-dsl, cdi, kubernetes, qpid-jms, smallrye-context-propagation, smallrye-health, vertx ]
```

Copy to Clipboard

Toggle word wrap

See the `MicroProfilePropertiesSource` line, it shows the content of the properties file added as a ConfigMap and mounted into the pod.

##### [1.6.1.2. Use Case 2 - Knative Integration Route Copy link](#use_case_2_knative_integration_route)

This use case features two Knative integration routes. The `Feed` route periodically sends a text message to a Knative channel, The second route `Printer` receives the message from the Knative channel and prints it.

For Camel K, there are two pods, each one running a single integration route. So, this migration must create two projects, each one having one integration route.

Later if you want, you can customize it to a single maven project with both integration routes in a single pod.

The `Feed` integration route.

```
import org.apache.camel.builder.RouteBuilder;

public class Feed extends RouteBuilder {
  @Override
  public void configure() throws Exception {
    from("timer:clock?period=15s")
      .setBody().simple("Hello World from Camel - ${date:now}")
      .log("sent message to messages channel: ${body}")
      .to("knative:channel/messages");
  }
}
```

Copy to Clipboard

Toggle word wrap

The `Printer` integration route.

```
import org.apache.camel.builder.RouteBuilder;

public class Printer extends RouteBuilder {
  @Override
  public void configure() throws Exception {
    from("knative:channel/messages")
      .convertBodyTo(String.class)
      .to("log:info");
  }
}
```

Copy to Clipboard

Toggle word wrap

The kamel run command shows you how this runs with Camel K.

```
kamel run Feed.java
kamel run Printer.java
```

Copy to Clipboard

Toggle word wrap

There are going to be two pods running.

##### [1.6.1.2.1. Step 1 - Create the maven project Copy link](#step_1_create_the_maven_project_2)

Use camel jbang to export the file into a full maven project

Export the `feed` integration.

```
camel export \
--runtime = quarkus \ --quarkus-group-id = com.redhat.quarkus.platform \ --quarkus-version = 3.8 .5.redhat-00003 \
--repos = https://maven.repository.redhat.com/ga \
--dep = io.quarkus:quarkus-openshift \
--gav = com.mycompany:ceq-feed:1.0 \
--dir = ceq-feed \ Feed.java
```

Copy to Clipboard

Toggle word wrap

Export the `printer` integration.

```
camel export \
--runtime = quarkus \ --quarkus-group-id = com.redhat.quarkus.platform \ --quarkus-version = 3.8 .5.redhat-00003 \
--repos = https://maven.repository.redhat.com/ga \
--dep = io.quarkus:quarkus-openshift \
--gav = com.mycompany:ceq-printer:1.0 \
--dir = ceq-printer \ Printer.java
```

Copy to Clipboard

Toggle word wrap

A maven project will be created for each integration.

##### [1.6.1.2.2. Step 2 - Configure the project Copy link](#step_2_configure_the_project_2)

This step is to configure the maven project and the artifacts to suit your environment. Use case 1 contains information about labels, annotation and configuration in ConfigMaps.

Get into the maven project

```
cd ceq-feed
```

Copy to Clipboard

Toggle word wrap

Set the `docker` build strategy.

```
echo quarkus.openshift.build-strategy=docker >> src/main/resources/application.properties
```

Copy to Clipboard

Toggle word wrap

Change the base image to OpenJDK 21 in `src/main/docker` (optional)

```
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
```

Copy to Clipboard

Toggle word wrap

Change the compiler version to 21 in pom.xml (optional)

```
<maven.compiler.release>21</maven.compiler.release>
```

Copy to Clipboard

Toggle word wrap

Add `openshift` as a deployment target.

```
quarkus.kubernetes.deployment-target=openshift
```

Copy to Clipboard

Toggle word wrap

You must set these container image properties, to set the image address in the generated openshift.yml and knative.yml file.

```
quarkus.container-image.registry=image-registry.openshift-image-registry.svc:5000
quarkus.container-image.group=<namespace>
```

Copy to Clipboard

Toggle word wrap

Add the following property in `application.properties` to allow the Knative controller to inject the `K_SINK` environment variable to the deployment.

```
quarkus.openshift.labels."bindings.knative.dev/include"=true
```

Copy to Clipboard

Toggle word wrap

Add the `knative.json` in `src/main/resources` . This is a required configuration for Camel to connect to the Knative channel.

Note

There is `k.sink` property placeholder. When the pod is running it will look at the environment variable named `K_SINK` and replace in the `url` value.

```
{
  "services": [
    {
      "type": "channel",
      "name": "messages",
      "url": "{{k.sink}}",
      "metadata": {
        "camel.endpoint.kind": "sink",
        "knative.apiVersion": "messaging.knative.dev/v1",
        "knative.kind": "Channel",
        "knative.reply": "false"
      }
    }
  ]
}
```

Copy to Clipboard

Toggle word wrap

Add the following property to allow Camel to load the Knative environment configuration.

```
camel.component.knative.environmentPath=classpath:knative.json
```

Copy to Clipboard

Toggle word wrap

To make the inject work, you must create a Knative `SinkBinding` object.

Add the `SinkBinding` file to `src/main/kubernetes/openshift.yml`

```
cat <<EOF > > src/main/kubernetes/openshift.yml apiVersion : sources.knative.dev/v1 kind : SinkBinding metadata : finalizers : - sinkbindings.sources.knative.dev name : ceq - feed spec : sink : ref : apiVersion : messaging.knative.dev/v1 kind : Channel name : messages subject : apiVersion : apps/v1 kind : Deployment name : ceq - feed
EOF
```

Copy to Clipboard

Toggle word wrap

Now, configure the `ceq-printer` project.

```
cd ceq-printer
```

Copy to Clipboard

Toggle word wrap

Set the `docker` build strategy.

```
echo quarkus.openshift.build-strategy=docker >> src/main/resources/application.properties
```

Copy to Clipboard

Toggle word wrap

Change the base image to OpenJDK 21 in `src/main/docker` (optional)

```
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
```

Copy to Clipboard

Toggle word wrap

Change the compiler version to 21 in pom.xml (optional)

```
<maven.compiler.release>21</maven.compiler.release>
```

Copy to Clipboard

Toggle word wrap

Set `knative` as a deployment target.

```
quarkus.kubernetes.deployment-target=knative
```

Copy to Clipboard

Toggle word wrap

You must set these container image properties, to correctly set the image address in the generated openshift.yml and knative.yml file.

```
quarkus.container-image.registry=image-registry.openshift-image-registry.svc:5000
quarkus.container-image.group=<namespace>
```

Copy to Clipboard

Toggle word wrap

Add the `knative.json` in `src/main/resources` . This is a required configuration for Camel to connect to the Knative channel.

```
{
  "services": [
    {
      "type": "channel",
      "name": "messages",
      "path": "/channels/messages",
      "metadata": {
        "camel.endpoint.kind": "source",
        "knative.apiVersion": "messaging.knative.dev/v1",
        "knative.kind": "Channel",
        "knative.reply": "false"
      }
    }
  ]
}
```

Copy to Clipboard

Toggle word wrap

Add the following property to allow Camel to load the Knative environment configuration.

```
camel.component.knative.environmentPath=classpath:knative.json
```

Copy to Clipboard

Toggle word wrap

A Knative `Subscription` is required for the message delivery from the channel to a sink.

Add the `Subscription` file to `src/main/kubernetes/knative.yml`

```
apiVersion : messaging.knative.dev/v1 kind : Subscription metadata : finalizers : - subscriptions.messaging.knative.dev name : ceq - printer spec : channel : apiVersion : messaging.knative.dev/v1 kind : Channel name : messages subscriber : ref : apiVersion : serving.knative.dev/v1 kind : Service name : ceq - printer uri : /channels/messages
```

Copy to Clipboard

Toggle word wrap

##### [1.6.1.2.3. Step 3 - Build Copy link](#step_3_build_2)

Build the package for local inspection.

```
./mvnw -ntp package
```

Copy to Clipboard

Toggle word wrap

This step builds the maven artifacts (JAR files) locally and generates the Openshift files in `target/kubernetes` directory.

Track the `target/kubernetes/openshift.yml` and `target/kubernetes/knative.yml`to understand the deployment that is deployed to the Openshift cluster.

##### [1.6.1.2.4. Step 4 - Build and Deploy Copy link](#step_4_build_and_deploy_2)

Build the package and deploy to Openshift.

```
./mvnw -ntp package -Dquarkus.openshift.deploy=true
```

Copy to Clipboard

Toggle word wrap

You can follow the image build in the maven output. After build, you can see the pod running.

##### [1.6.1.2.5. Step 5 - Test Copy link](#step_5_test_2)

Verify if the integration route is working.

Follow the pod container log

```
oc logs -f `oc get pod -l app.kubernetes.io/name=ceq-feed -oname`
```

Copy to Clipboard

Toggle word wrap

It must show like the following output:

**ceq-feed pod**

```
INFO exec -a "java" java -Dquarkus.http.host = 0.0 .0.0 -Djava.util.logging.manager = org.jboss.logmanager.LogManager -cp "." -jar /deployments/quarkus-run.jar
INFO running in /deployments
__  ____  __  _____   ___  __ ____  ______
 --/ __ \ / / / / _ | / _ \ / //_/ / / / __/
 -/ /_/ / /_/ / __ | / , _/ , < / /_/ / \ \ -- \ ___ \ _ \ ____/_/ | _/_/ | _/_/ | _ | \ ____/___/ [ org.apa.cam.qua.cor.CamelBootstrapRecorder ] ( main ) Bootstrap runtime: org.apache.camel.quarkus.main.CamelMainRuntime [ org.apa.cam.mai.MainSupport ] ( main ) Apache Camel ( Main ) 4.4 .0.redhat-00025 is starting [ org.apa.cam.mai.BaseMainSupport ] ( main ) Auto-configuration summary [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ MicroProfilePropertiesSource ] camel.component.knative.environmentPath = classpath:knative.json [ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Apache Camel 4.4 .0.redhat-00025 ( camel-1 ) is starting [ org.apa.cam.mai.BaseMainSupport ] ( main ) Property-placeholders summary [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ OS Environment Variable ] k.sink = http://hello-kn-channel.cmiranda-camel.svc.cluster.local [ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Routes startup ( started:1 )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started route1 ( timer://clock )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Apache Camel 4.4 .0.redhat-00025 ( camel-1 ) started in 43ms ( build:0ms init:0ms start:43ms )
[ io.quarkus ] ( main ) ceq-feed 1.0 on JVM ( powered by Quarkus 3.8 .5.redhat-00004 ) started in 1 .386s. Listening on: http://0.0.0.0:8080 [ io.quarkus ] ( main ) Profile prod activated. [ io.quarkus ] ( main ) Installed features: [ camel-attachments, camel-cloudevents, camel-core, camel-knative, camel-platform-http, camel-rest, camel-rest-openapi, camel-timer, cdi, kubernetes, smallrye-context-propagation, vertx ]
[ route1 ] ( Camel ( camel-1 ) thread #1 - timer://clock) sent message to hello channel: Hello World from Camel - Thu Aug 01 13:54:41 UTC 2024
[ route1 ] ( Camel ( camel-1 ) thread #1 - timer://clock) sent message to hello channel: Hello World from Camel - Thu Aug 01 13:54:56 UTC 2024
[ route1 ] ( Camel ( camel-1 ) thread #1 - timer://clock) sent message to hello channel: Hello World from Camel - Thu Aug 01 13:55:11 UTC 2024
```

Copy to Clipboard

Toggle word wrap

See the `Property-placeholders` . It shows the `k.sink` property value.

**ceq-printer pod**

```
INFO exec -a "java" java -Dquarkus.http.host = 0.0 .0.0 -Djava.util.logging.manager = org.jboss.logmanager.LogManager -cp "." -jar /deployments/quarkus-run.jar
INFO running in /deployments
__  ____  __  _____   ___  __ ____  ______
 --/ __ \ / / / / _ | / _ \ / //_/ / / / __/
 -/ /_/ / /_/ / __ | / , _/ , < / /_/ / \ \ -- \ ___ \ _ \ ____/_/ | _/_/ | _/_/ | _ | \ ____/___/ [ org.apa.cam.qua.cor.CamelBootstrapRecorder ] ( main ) Bootstrap runtime: org.apache.camel.quarkus.main.CamelMainRuntime [ org.apa.cam.mai.MainSupport ] ( main ) Apache Camel ( Main ) 4.4 .0.redhat-00025 is starting [ org.apa.cam.mai.BaseMainSupport ] ( main ) Auto-configuration summary [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ MicroProfilePropertiesSource ] camel.component.knative.environmentPath = classpath:knative.json [ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Apache Camel 4.4 .0.redhat-00025 ( camel-1 ) is starting [ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Routes startup ( started:1 )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started route1 ( knative://channel/hello )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Apache Camel 4.4 .0.redhat-00025 ( camel-1 ) started in 10ms ( build:0ms init:0ms start:10ms )
[ io.quarkus ] ( main ) ceq-printer 1.0 on JVM ( powered by Quarkus 3.8 .5.redhat-00004 ) started in 1 .211s. Listening on: http://0.0.0.0:8080 [ io.quarkus ] ( main ) Profile prod activated. [ io.quarkus ] ( main ) Installed features: [ camel-attachments, camel-cloudevents, camel-core, camel-knative, camel-log, camel-platform-http, camel-rest, camel-rest-openapi, cdi, kubernetes, smallrye-context-propagation, vertx ]
[ info ] ( executor-thread-1 ) Exchange [ ExchangePattern: InOnly, BodyType: String, Body: Hello World from Camel - Thu Aug 01 13 :54:41 UTC 2024 ]
[ info ] ( executor-thread-1 ) Exchange [ ExchangePattern: InOnly, BodyType: String, Body: Hello World from Camel - Thu Aug 01 13 :54:56 UTC 2024 ]
[ info ] ( executor-thread-1 ) Exchange [ ExchangePattern: InOnly, BodyType: String, Body: Hello World from Camel - Thu Aug 01 13 :55:11 UTC 2024 ]
```

Copy to Clipboard

Toggle word wrap

##### [1.6.1.3. Use Case 3 - Pipe Copy link](#use_case_3_pipe)

Given the following integration route as a KameletBinding.

```
apiVersion : camel.apache.org/v1alpha1 kind : KameletBinding metadata : name : sample spec : source : ref : kind : Kamelet apiVersion : camel.apache.org/v1alpha1 name : timer - source properties : period : 5000 contentType : application/json message : '{"id":"1","field":"hello","message":"Camel Rocks"}' steps : - ref : kind : Kamelet apiVersion : camel.apache.org/v1alpha1 name : extract - field - action properties : field : "message" sink : ref : kind : Kamelet apiVersion : camel.apache.org/v1 name : log - sink properties : showStreams : true
```

Copy to Clipboard

Toggle word wrap

##### [1.6.1.3.1. Step 1 - Create the maven project Copy link](#step_1_create_the_maven_project_3)

Use camel jbang to export the file into a maven project.

```
camel export \
--runtime = quarkus \ --quarkus-group-id = com.redhat.quarkus.platform \ --quarkus-version = 3.8 .5.redhat-00003 \
--repos = https://maven.repository.redhat.com/ga \
--dep = io.quarkus:quarkus-openshift \
--gav = com.mycompany:ceq-timer2log-kbind:1.0 \
--dir = ceq-timer2log-kbind \ timer-2-log-kbind.yaml
```

Copy to Clipboard

Toggle word wrap

You can see more parameters with `camel export --help`

##### [1.6.1.3.2. Step 2 - Configure the project Copy link](#step_2_configure_the_project_3)

This is the step to configure the maven project and the artifacts to suit your environment.

Note

You can follow use cases 1 and 2 for the common configuration and we will provide the steps required for the KameletBinding configuration.

You can try to run the integration route locally with camel jbang to see how it works, before building and deploying to Openshift.

Get into the maven project

```
cd ceq-timer2log-kbind
```

Copy to Clipboard

Toggle word wrap

See the note at the beginning about how to manage Kamelets. For this migration use case, I use the `org.apache.camel.kamelets:camel-kamelets` dependency in `pom.xml` .

When exporting, it adds the following properties in `application.properties` , but you can remove it.

```
quarkus.native.resources.includes
camel.main.routes-include-pattern
```

Copy to Clipboard

Toggle word wrap

Set the `docker` build strategy.

```
echo quarkus.openshift.build-strategy=docker >> src/main/resources/application.properties
```

Copy to Clipboard

Toggle word wrap

If your `Kamelet` or `KameletBinding` has trait annotations like the following: `trait.camel.apache.org/environment.vars: "my_key=my_val"` , then you must follow the trait configuration section about how to set it using Quarkus properties.

##### [1.6.1.3.3. Step 3 - Build Copy link](#step_3_build_3)

Build the package for local inspection.

```
./mvnw -ntp package
```

Copy to Clipboard

Toggle word wrap

This step builds the maven artifacts (JAR files) locally and generates the Openshift manifest files in `target/kubernetes` directory.

Track the `target/kubernetes/openshift.yml` to understand the deployment that is deployed to the Openshift cluster.

##### [1.6.1.3.4. Step 4 - Build and Deploy Copy link](#step_4_build_and_deploy_3)

Build the package and deploy to Openshift.

```
./mvnw -ntp package -Dquarkus.openshift.deploy = true
```

Copy to Clipboard

Toggle word wrap

You can follow the image build in the maven output. After build, you can see the pod running.

##### [1.6.1.3.5. Step 5 - Test Copy link](#step_5_test_3)

Verify if the integration route is working.

Follow the pod container log

```
oc logs -f ` oc get pod -l app.kubernetes.io/name = ceq-timer2log-kbind -oname `
```

Copy to Clipboard

Toggle word wrap

It must show like the following output:

```
[ org.apa.cam.qua.cor.CamelBootstrapRecorder ] ( main ) Bootstrap runtime: org.apache.camel.quarkus.main.CamelMainRuntime [ org.apa.cam.mai.MainSupport ] ( main ) Apache Camel ( Main ) 4.4 .0.redhat-00025 is starting [ org.apa.cam.cli.con.LocalCliConnector ] ( main ) Management from Camel JBang enabled [ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Apache Camel 4.4 .0.redhat-00025 ( camel-1 ) is starting [ org.apa.cam.mai.BaseMainSupport ] ( main ) Property-placeholders summary [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ timer-source.kamelet.yaml ] period = 5000
[ org.apa.cam.mai.BaseMainSupport ] ( main ) [ timer-source.kamelet.yaml ] message = { "id" : "1" , "field" : "hello" , "message" : "Camel Rocks" }
[ org.apa.cam.mai.BaseMainSupport ] ( main ) [ timer-source.kamelet.yaml ] contentType = application/json [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ log-sink.kamelet.yaml ] showStreams = true [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ ct-field-action.kamelet.yaml ] extractField = extractField-1 [ org.apa.cam.mai.BaseMainSupport ] ( main ) [ ct-field-action.kamelet.yaml ] field = message [ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Routes startup ( started:4 )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started sample ( kamelet://timer-source )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started timer-source-1 ( timer://tick )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started log-sink-2 ( kamelet://source )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Started extract-field-action-3 ( kamelet://source )
[ org.apa.cam.imp.eng.AbstractCamelContext ] ( main ) Apache Camel 4.4 .0.redhat-00025 ( camel-1 ) started in 276ms ( build:0ms init:0ms start:276ms )
[ io.quarkus ] ( main ) ceq-timer2log-kbind 1.0 on JVM ( powered by Quarkus 3.8 .5.redhat-00004 ) started in 1 .867s. Listening on: http://0.0.0.0:8080 [ io.quarkus ] ( main ) Profile prod activated. [ io.quarkus ] ( main ) Installed features: [ camel-attachments, camel-cli-connector, camel-console, camel-core, camel-direct, camel-jackson, camel-kamelet, camel-log, camel-management, camel-microprofile-health, camel-platform-http, camel-rest, camel-rest-openapi, camel-timer, camel-xml-jaxb, camel-yaml-dsl, cdi, kubernetes, smallrye-context-propagation, smallrye-health, vertx ]
[ log-sink ] ( Camel ( camel-1 ) thread #2 - timer://tick) Exchange[ExchangePattern: InOnly, BodyType: org.apache.camel.converter.stream.InputStreamCache, Body: "Camel Rocks"]
```

Copy to Clipboard

Toggle word wrap

#### [1.6.2. Undeploy kubernetes resources Copy link](#undeploy_kubernetes_resources)

To delete all resouces installed by the quarkus-maven-plugin, you must run the following command.

```
oc delete -f target/kubernetes/openshift.yml
```

Copy to Clipboard

Toggle word wrap

#### [1.6.3. Kubernetes CronJob Copy link](#kubernetes_cronjob)

Camel K has a feature when there is a consumer of type cron, quartz or timer.In some circumstances, it creates a kubernetes `CronJob` object instead of a regular `Deployment` . This saves computing resources by not running the Deployment `Pod` all time.

To obtain the same outcome in Red Hat build of Apache Camel for Quarkus, you must set the following properties in `src/main/resources/application.properties` .

```
quarkus.openshift.deployment-kind=CronJob
quarkus.openshift.cron-job.schedule=<your cron schedule>
camel.main.duration-max-idle-seconds=1
```

Copy to Clipboard

Toggle word wrap

And you must set the timer consumer to execute only once, as follows:

```
from("timer:java?delay=0&period=1&repeatCount=1")
```

Copy to Clipboard

Toggle word wrap

The following are the timer parameters.

- `delay=0` : Starts the consumer with no delay.
- `period=1` : Run only once 1s.
- `repeatCount=1` : Don't run after the first run.

### [1.7. Troubleshooting Copy link](#migrate_camel_k_troubleshooting)

#### [1.7.1. Product Support Copy link](#product_support)

If you encounter any problems during the migration process you can open a [support case](https://access.redhat.com/support/cases/#/case/new) and we will help you resolve the issue.

#### [1.7.2. Ignore loading errors when exporting with camel jbang Copy link](#ignore_loading_errors_when_exporting_with_camel_jbang)

When using camel jbang export, it may fail to load the routes. Here, you can use the `--ignore-loading-error` parameter, as follows:

```
camel export --ignore-loading-error <parameters>
```

Copy to Clipboard

Toggle word wrap

#### [1.7.3. Increase logging Copy link](#increase_logging)

You can set a category logging, by using the following property in `application.properties` for `org.apache.camel.component.knative` category to debug level.

```
quarkus.log.category."org.apache.camel.component.knative".level=debug
```

Copy to Clipboard

Toggle word wrap

#### [1.7.4. Disable health checks Copy link](#disable_health_checks)

Your application pod may fail with `CrashLoopBackOff` and the following error appears in the log pod.

```
Get "http://127.0.0.1:8080/q/health/ready": dial tcp 127.0.0.1:8080: connect: connection refused
```

Copy to Clipboard

Toggle word wrap

If you do not want the container health checks, you can disable the container health check by removing this maven dependency from the pom.xml

```
<dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-microprofile-health</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

### [1.8. Known Issues Copy link](#migrate_camel_k_known_issues)

There are a few known issues related to migrating integration routes, along with their workarounds. These workarounds are not limitations of the Red Hat build of Apache Camel for Quarkus, but rather part of the migration process. Once the migration is complete, the resulting Maven project is customizable to meet customer needs.

#### [1.8.1. Camel K features not available in Camel for Quarkus Copy link](#camel_k_features_not_available_in_camel_for_quarkus)

Some Camel K features are not available in Quarkus or Camel as a quarkus property. These features may require additional configuration steps to achieve the same functionality when building and deploying in Red Hat build of Apache Camel for Quarkus.

##### [1.8.1.1. Owner Trait Copy link](#owner_trait)

The [owner trait](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel_k/1.10.5/html/developing_and_managing_integrations_using_camel_k/camel-k-traits-reference#owner_trait) sets the kubernetes owner fields for all created resources, simplifying the process of tracking who created a kubernetes resource.

There is an open [Quarkus issue #13952](https://github.com/quarkusio/quarkus/issues/13952) requesting this feature.

There is no workaround to set the owner fields.

##### [1.8.1.2. Affinity Trait Copy link](#affinity_trait)

The [node affinity trait](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel_k/1.10.5/html/developing_and_managing_integrations_using_camel_k/camel-k-traits-reference#nodeaffinity_trait) enables you to constrain the nodes on which the integration pods are scheduled to run.

There is an open [Quarkus issue #13596](https://github.com/quarkusio/quarkus/issues/13596) requesting this feature.

The workaround would be to implement a post processing task after maven package step, to add the affinity configuration to `target/kubernetes/openshift.yml` .

##### [1.8.1.3. PodDisruptionBudget Trait Copy link](#poddisruptionbudget_trait)

The [PodDisruptionBudget trait](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel_k/1.10.5/html/developing_and_managing_integrations_using_camel_k/camel-k-traits-reference#configuration_4) allows to configure the PodDisruptionBudget resource for the Integration pods.

There is configuration in Quarkus to generate the `PodDisruptionBudget` resource.

The workaround would be to implement a post processing task after maven package step, to add the `PodDisruptionBudget` configuration to `target/kubernetes/openshift.yml` .

#### [1.8.2. Camel Jbang fails to add camel-quarkus-direct dependency Copy link](#camel_jbang_fails_to_add_camel_quarkus_direct_dependency)

If the integration route contains a `rest` and a `direct` endpoint, as shown in the example below, verify that `pom.xml` contains `camel-quarkus-direct` dependency. If it is missing, you must add it.

```
rest()
    .post("/message")
    .id("rest")
    .to("direct:foo");

from("direct:foo")
    .log("hello");
```

Copy to Clipboard

Toggle word wrap

The `camel-quarkus-direct` dependency to add to the pom.xml

```
< dependency > < groupId > org.apache.camel.quarkus < /groupId > < artifactId > camel-quarkus-direct < /artifactId >
< /dependency >
```

Copy to Clipboard

Toggle word wrap

#### [1.8.3. Quarkus build fails with Copy link](#quarkus_build_fails_with)

```
PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

Copy to Clipboard

Toggle word wrap

The server certificate is not trusted by the client. Therefore, you must either add the server public key to the client or trust the server certificate. If you are testing, you can add the following property to the `src/main/resources/application.properties` and rebuild it.

```
quarkus.kubernetes-client.trust-certs=true
```

Copy to Clipboard

Toggle word wrap

#### [1.8.4. Camel Jbang fails to export a route Copy link](#camel_jbang_fails_to_export_a_route)

Camel Jbang fails to export a route when the route contains a kamelet endpoint, which is backed by a bean. If the endpoint contains a kamelet, with property placeholders `{{broker}}` , and in the kamelet there is a `type: "#class:org.apache.qpid.jms.JmsConnectionFactory"` to initialize the camel component, it may fail.

```
from("direct:jms")
.to("kamelet:jms-amqp-10-sink?remoteURI={{broker}}&destinationName={{queue}}");
```

Copy to Clipboard

Toggle word wrap

The error is composed of the following errors.

```
org.apache.camel.RuntimeCamelException: org.apache.camel.VetoCamelContextStartException: Failure creating route from template: jms-amqp-10-sink
Caused by: org.apache.camel.VetoCamelContextStartException: Failure creating route from template: jms-amqp-10-sink
Caused by: org.apache.camel.component.kamelet.FailedToCreateKameletException: Error creating or loading Kamelet with id jms-amqp-10-sink (locations: classpath:kamelets,github:apache:camel-kamelets/kamelets)
Caused by: org.apache.camel.FailedToCreateRouteException: Failed to create route jms-amqp-10-sink-1 at: >>> To[jms:{{destinationType}}:{{destinationName}}?connectionFactory=#bean:{{connectionFactoryBean}}]
Caused by: org.apache.camel.ResolveEndpointFailedException: Failed to resolve endpoint: jms://Queue:$%7Bqueue%7D?connectionFactory=%23bean%3AconnectionFactoryBean-1 due to: Error binding property (connectionFactory=#bean:connectionFactoryBean-1)
Caused by: org.apache.camel.PropertyBindingException: Error binding property (connectionFactory=#bean:connectionFactoryBean-1) with name: connectionFactory on bean:
Caused by: java.lang.IllegalStateException: Cannot create bean: #class:org.apache.qpid.jms.JmsConnectionFactory
Caused by: org.apache.camel.PropertyBindingException: Error binding property (remoteURI=@@[broker]@@) with name: remoteURI on bean: org.apache.qpid.jms.JmsConnectionFactory@a2b54e3 with value: @@[broker]@@
Caused by: java.lang.IllegalArgumentException: Invalid remote URI: @@[broker]@@
Caused by: java.net.URISyntaxException: Illegal character in path at index 2: @@[broker]@@
```

Copy to Clipboard

Toggle word wrap

How to fix:

Replace the property placeholders in the kamelet endpoint `{{broker}}` and `{{queue}}` with any value, for example: `remoteURI=broker&destinationName=queue` . Now export the file, and you can add the property placeholder back in the exported route in src/main/ directory.

### [1.9. Reference documentation Copy link](#migrate_camel_k_documentation)

For more details about Camel products, refer the following links.

- [Red Hat build of Apache Camel for Quarkus releases](https://access.redhat.com/articles/7021827)
- [Red Hat build of Apache Camel for Quarkus Documentation, including migration to Camel Spring Boot](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.4)
- [Camel K documentation](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel_k/1.10.5)
- [Deploying a Camel Spring Boot application to OpenShift](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.4/html-single/getting_started_with_red_hat_build_of_apache_camel_for_spring_boot/index#deploying-camel-spring-boot-application-to-openshift)
- [Deploying Red Hat build of Apache Camel for Quarkus applications](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.4/html-single/getting_started_with_red_hat_build_of_apache_camel_for_quarkus/index#deploying-quarkus-applications)
- [Deploying your Red Hat build of Quarkus applications to OpenShift Container Platform](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.8/html/deploying_your_red_hat_build_of_quarkus_applications_to_openshift_container_platform/assembly_quarkus-openshift_quarkus-openshift#assembly_quarkus-openshift_quarkus-openshift)
- [Developer Resources for Red Hat Build of Quarkus](https://developers.redhat.com/products/quarkus/download)
- [Quarkus Configuration for Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes)
- [Quarkus Configuration for Openshift](https://quarkus.io/guides/deploying-to-openshift)

## [Legal Notice Copy link](#idm140353041749648)

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