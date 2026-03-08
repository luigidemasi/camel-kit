## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Tooling Guide for Red Hat Build of Apache Camel
2. [Preface](#idm139664545242704)
3. [1. Access integrated open source capabilities](#camel-integrations)
4. 2. Tooling Guide
5. 3. Tooling Guide extension pack
6. 4. Language support for Camel
7. 5. Debug support for Camel
8. 6. Using Camel CLI with Red Hat build of Apache Camel for Quarkus
9. 7. Using Camel CLI with Camel Spring Boot
10. [Legal Notice](#idm139664544634320)

Format Multi-page Single-page View full doc as PDF

# Tooling Guide for Red Hat Build of Apache Camel

Red Hat build of Apache Camel 4.8

## Tooling Guide provided by Red Hat

[Legal Notice](#idm139664544634320)

**Abstract**

The Tooling Guide introduces tooling extensions that are used for Red Hat build for Apache Camel.

## [Preface Copy link](#idm139664545242704)

#### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12365398&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Access integrated open source capabilities Copy link](#camel-integrations)

Red Hat build of Apache Camel is certified and supported in a large variety of environments, combining the best of open source integration projects into a powerful, enterprise-ready toolkit, designed to simplify and accelerate cloud-native integration for modern businesses.

These integrations include:

Apache Camel integration framework which implements enterprise integration patterns and offers hundreds of prebuilt components and connectors. Kaoto visual designer for Apache Camel. HawtIO modular web console for troubleshooting and remote management of integrations. Apache CXF for developing and consuming Simple Object Access Protocol (SOAP) web services. Camel CLI for iterative integration prototyping. VS Code development tools for code assistance and debugging. Extra Camel components requiring licensed libraries. Camel golden path templates for Backstage. Monitoring and tracing via Prometheus and OpenTelemetry. Narayana transaction manager. Quarkus and Spring Boot runtimes. Member of Quarkus Platform with simultaneous security updates.

## [Chapter 2. Tooling Guide Copy link](#camel-tooling-guide)

### [2.1. About Tooling Guide Copy link](#introduction_tooling_guide-tooling-guide)

This guide introduces VS Code extensions for Red Hat build of Apache Camel and how to install and use Camel CLI.

Important

The VS Code extensions for Apache Camel are listed as development support. For more information about scope of development support, see [Development Support Scope of Coverage for Red Hat Build of Apache Camel](https://access.redhat.com/articles/7043889) .

## [Chapter 3. Tooling Guide extension pack Copy link](#camel-tooling-guide-extension-pack)

Important

The VS Code extensions for Apache Camel are listed as development support. For more information about scope of development support, see [Development Support Scope of Coverage for Red Hat Build of Apache Camel](https://access.redhat.com/articles/7043889) .

### [3.1. Installing extension pack for Apache Camel by Red Hat Copy link](#installing-camel-extension-pack-extension-pack)

This section explains how to install Extension Pack for Apache Camel by Red Hat.

**Procedure**

1. Open the VS Code editor.
2. In the **VS Code** editor, select **View &gt; Extensions** .
3. In the search bar, type **Camel** . Select the **Extension Pack for Apache Camel by Red Hat** option from the search results and then click Install.

This installs the extension pack which includes extensions for Apache Camel in the VS Code editor.

## [Chapter 4. Language support for Camel Copy link](#camel-tooling-guide-language)

Important

The VS Code extensions for Apache Camel are listed as development support. For more information about scope of development support, see [Development Support Scope of Coverage for Red Hat Build of Apache Camel](https://access.redhat.com/articles/7043889) .

### [4.1. About language support for Apache Camel extension Copy link](#vscode-language-support-extension-language)

The Visual Studio Code language support extension adds the language support for Apache Camel for XML DSL and Java DSL code.

This extension provides completion, validation and documentation features for Apache Camel URI elements directly in your Visual Studio Code editor. It works as a client using the Microsoft Language Server Protocol which communicates with Camel Language Server to provide all functionalities.

#### [4.1.1. Features of language support for Apache Camel extension Copy link](#features_of_language_support_for_apache_camel_extension)

The important features of the language support extension are listed below:

- Language service support for Apache Camel URIs.
- Quick reference documentation when you hover the cursor over a Camel component.
- Diagnostics for Camel URIs.
- Navigation for Java and XML langauges.
- Creating a Camel Route specified with Yaml DSL using Camel CLI.
- Create a Camel Quarkus project
- Create a Camel on SpringBoot project
- Specific Camel Catalog Version
- Specific Runtime provider for the Camel Catalog

#### [4.1.2. Requirements Copy link](#requirements)

The following points must be considered when using the Apache Camel Language Server:

- Java 17 is currently required to launch the Apache Camel Language Server. The `java.home` VS Code option is used to use a different version of JDK than the default one installed on the machine.
- For some features, JBang must be available on a system command line.
- For an XML DSL files:
- For a Java DSL files:

#### [4.1.3. Installing Language support for Apache Camel extension Copy link](#installing_language_support_for_apache_camel_extension)

You can download the Language support for Apache Camel extension from the VS Code Extension Marketplace and the Open VSX Registry. You can also install the Language Support for Apache Camel extension directly in the Microsoft VS Code.

**Procedure**

1. Open the VS Code editor.
2. In the **VS Code** editor, select **View &gt; Extensions** .
3. In the search bar, type **Camel** . Select the **Language Support for Apache Camel** option from the search results and then click Install.

This installs the language support extension in your editor.

#### [4.1.4. Using specific Camel catalog version Copy link](#using_specific_camel_catalog_version)

You can use the specific Camel catalog version. Click **File** &gt; **Preferences** &gt; **Settings** &gt; **Apache Camel Tooling** &gt; **Camel catalog version** . For Red Hat productized version that contains redhat in its version identifier, the Maven Red Hat repository is automatically added.

Note

For the first time a version is used, it takes several seconds/minutes to have it available depending on the time to download the dependencies in the background.

#### [4.1.5. Limitations Copy link](#limitations)

- The Kamelet catalog used is community supported version only. For the list of supported Kamelets, see link: [Supported Kamelets](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel_k/1.10.1/html-single/release_notes_for_red_hat_build_of_apache_camel_k_1.10.1/index#supported_kamelets)
- Modeline configuration is based on community only. Not all traits and modeline parameters are supported.

**Additional resources**

- [Language Support for Apache Camel by Red Hat](https://camel-tooling.github.io/camel-lsp-client-vscode/)

## [Chapter 5. Debug support for Camel Copy link](#camel-tooling-guide-debug)

Important

The VS Code extensions for Apache Camel are listed as development support. For more information about scope of development support, see [Development Support Scope of Coverage for Red Hat Build of Apache Camel](https://access.redhat.com/articles/7043889) .

### [5.1. About Debug Adapter for Apache Camel routes Copy link](#vscode-debug-adapter-extension-debug)

The VS Code Debug Adapter is a Visual Studio Code extension that you can use to debug running Camel routes written in Java, Yaml or XML DSL.

#### [5.1.1. Features of Debug Adapter Copy link](#features_of_debug_adapter)

The VS Code Debug Adapter for Apache Camel extension supports the following features:

- Camel Main mode for XML only.
- The use of Camel debugger by attaching it to a running Camel route written in Java, Yaml or XML using the JMX url.
- The local use of Camel debugger by attaching it to a running Camel route written in Java, Yaml or XML using the PID.
- You can use it for a single Camel context.
- Add or remove the breakpoints.
- The conditional breakpoints with simple language.
- Inspecting the variable values on suspended breakpoints.
- Resume a single route instance and resume all route instances.
- Stepping when the route definition is in the same file.
- Allow to update variables in scope Debugger, in the message body, in a message header of type String, and an exchange property of type String
- Supports the command `Run Camel Application with JBang and Debug` .
- Supports the command `Run Camel application with JBang` .
- Configuration snippets for Camel debugger launch configuration
- Configuration snippets to launch a Camel application ready to accept a Camel debugger connection using JBang, or Maven with Camel maven plugin

#### [5.1.2. Requirements Copy link](#requirements_2)

The following points must be considered when using the VS Code Debug Adapter for Apache Camel extension:

**Prerequsites**

- Java Runtime Environment:
- The Camel instance:

Note

For some features, The JBang must be available on a system commandline.

#### [5.1.3. Installing VS Code Debug Adapter for Apache Camel Copy link](#installing_vs_code_debug_adapter_for_apache_camel)

You can download the VS Code Debug Adapter for Apache Camel extension from the VS Code Extension Marketplace and the Open VSX Registry. You can also install the Debug Adapter for Apache Camel extension directly in the Microsoft VS Code.

**Procedure**

1. Open the VS Code editor.
2. In the **VS Code** editor, select **View &gt; Extensions** .
3. In the search bar, type **Camel Debug** . Select the **Debug Adapter for Apache Camel** option from the search results and then click Install.

This installs the Debug Adapter for Apache Camel in the VS Code editor.

#### [5.1.4. Using Debug Adapter Copy link](#using_debug_adapter)

You can debug your camel application with the debug adapter.

**Procedure**

1. Ensure that the `jbang` binary is available on the system commandline.
2. Open a Camel route which can be started with Camel CLI.
3. Call the **command Palette** using the keys `Ctrl + Shift + P` , and select the **Run Camel Application with JBang and Debug** command or click on the codelens **Camel Debug with JBang** that appears on top of the file.
4. Wait until the route is started and debugger is connected.
5. Put a breakpoint on the Camel route.
6. Debug.

**Additional resources**

- [Debug Adapter for Apache Camel by Red Hat](https://camel-tooling.github.io/camel-dap-client-vscode/)

## [Chapter 6. Using Camel CLI with Red Hat build of Apache Camel for Quarkus Copy link](#camel-cli-cq)

### [6.1. Installing Camel CLI Copy link](#installing-camel-jbang-cq)

**Prerequisites**

1. JBang must be installed on your machine. See [instructions](https://www.jbang.dev/download/) on how to download and install the JBang.

After the JBang is installed, you can verify JBang is working by executing the following command from a command shell:

```
jbang version
```

Copy to Clipboard

Toggle word wrap

This outputs the version of installed JBang.

**Procedure**

1. Optional: uninstall any previous versions of Camel CLI: `jbang app uninstall camel` Copy to Clipboard Toggle word wrap
2. Run the following command to install the Camel CLI application: `jbang app install -Dcamel.jbang.version = 4.8 .2 camel@apache/camel` Copy to Clipboard Toggle word wrap

Use a `camel.jbang.version` that matches the product camel version

This installs the Apache Camel as the `camel` command within JBang. This means that you can run Camel from the command line by just executing `camel` command.

### [6.2. Using Camel CLI Copy link](#using-camel-jbang-cq)

The Camel CLI supports multiple commands. The `camel help` command can display all the available commands.

```
camel --help
```

Copy to Clipboard

Toggle word wrap

Note

The first time you run this command, it may cause dependencies to be cached, therefore taking a few extra seconds to run. If you are already using JBang and you get errors such as `Exception in thread "main" java.lang.NoClassDefFoundError: "org/apache/camel/dsl/jbang/core/commands/CamelJBangMain"` , try clearing the JBang cache and re-install again.

All the commands support the `--help` and will display the appropriate help if that flag is provided.

#### [6.2.1. User configuration for Camel CLI Copy link](#user_configuration_for_camel_cli)

Camel CLI config command is used to store and use the user configuration. This eliminates the need to specify CLI options each time. For example, to run a different Camel version, use:

```
camel run * --camel-version=4.8.3.redhat-00004
```

Copy to Clipboard

Toggle word wrap

the `camel-version` can be added to the user configuration such as:

```
camel config set camel-version=4.8.3.redhat-00004
```

Copy to Clipboard

Toggle word wrap

This configures the Camel version that is used when you use `camel run` command. The `run` command below uses the user configuration:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

The user configuration file is stored in `~/.camel-jbang-user.properties` .

#### [6.2.2. Enable shell completion Copy link](#enable_shell_completion)

Camel CLI provides shell completion for bash and zsh out of the box. To enable shell completion for Camel CLI, run:

```
source < ( camel completion )
```

Copy to Clipboard

Toggle word wrap

To make it permanent, run:

```
echo 'source <(camel completion)' >> ~/.bashrc
```

Copy to Clipboard

Toggle word wrap

### [6.3. Creating and running Camel routes Copy link](#camel-jbang-cq-running-camel-routes-cq)

You can create a new basic routes with the `init` command. For example to create an XML route, run the following command:

```
camel init cheese.xml
```

Copy to Clipboard

Toggle word wrap

This creates the file `cheese.xml` (in the current directory) with a sample route.

To run the file, run:

```
camel run cheese.xml
```

Copy to Clipboard

Toggle word wrap

Note

You can create and run any of the supported [DSLs](https://camel.apache.org/manual/dsl.html) in Camel such as YAML, XML, Java, Groovy.

To create a new `.java` route, run:

```
camel init foo.java
```

Copy to Clipboard

Toggle word wrap

When you use the init command, Camel by default creates the file in the current directory. However, you can use the `--directory` option to create the file in the specified directory. For example to create in a folder named `foobar` , run:

```
camel init foo.java --directory = foobar
```

Copy to Clipboard

Toggle word wrap

Note

When you use the `--directory` option, Camel automatically cleans this directory if already exists.

#### [6.3.1. Running routes from multiple files Copy link](#running_routes_from_multiple_files)

You can run routes from more than one file, for example to run two YAML files:

```
camel run one.yaml two.yaml
```

Copy to Clipboard

Toggle word wrap

You can run routes from two different files such as yaml and Java:

```
camel run one.yaml hello.java
```

Copy to Clipboard

Toggle word wrap

You can use wildcards (i.e. `*` ) to match multiple files, such as running all the yaml files:

```
camel run *.yaml
```

Copy to Clipboard

Toggle word wrap

You can run all files starting with foo*:

```
camel run foo*
```

Copy to Clipboard

Toggle word wrap

To run all the files in the directory, use:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

Note

The `run` goal can also detect files that are `properties` , such as `application.properties` .

#### [6.3.2. Running routes from input parameter Copy link](#running_routes_from_input_parameter)

For very small Java routes, it is possible to provide the route as CLI argument, as shown below:

```
camel run --code = 'from("kamelet:beer-source").to("log:beer")'
```

Copy to Clipboard

Toggle word wrap

This is very limited as the CLI argument is a bit cumbersome to use than files. When you run the routes from input parameter, remember that:

- Only Java DSL code is supported.
- Code is wrapped in single quote, so you can use double quote in Java DSL.
- Code is limited to what literal values possible to provide from the terminal and JBang.
- All route(s) must be defined in a single `--code` parameter.

Note

Using `--code` is only usable for very quick and small prototypes.

#### [6.3.3. Dev mode with live reload Copy link](#dev_mode_with_live_reload)

You can enable the dev mode that comes with live reload of the route(s) when the source file is updated (saved), using the `--dev` options as shown:

```
camel run foo.yaml --dev
```

Copy to Clipboard

Toggle word wrap

Then while the Camel integration is running, you can update the YAML route and update when saving. This option works for all DLS including `java` , for example:

```
camel run hello.java --dev
```

Copy to Clipboard

Toggle word wrap

Note

The live reload option is meant for development purposes only, and if you encounter problems with reloading such as JVM class loading issues, then you may need to restart the integration.

#### [6.3.4. Developer Console Copy link](#developer_console)

You can enable the developer console, which presents a variety of information to the developer. To enable the developer console, run:

```
camel run hello.java --console
```

Copy to Clipboard

Toggle word wrap

The console is then accessible from a web browser at [http://localhost:8080/q/dev](http://localhost:8080/q/dev) (by default). The link is also displayed in the log when the Camel is starting up.

The console can give you insights into your running Camel integration, such as reporting the top routes that takes the longest time to process messages. You can then identify the slowest individual EIPs in these routes.

The developer console can also output the data in `JSON` format, that can be used by 3rd-party tooling to capture the information. For example, to output the top routes via curl, run:

```
curl -s -H "Accept: application/json" http://0.0.0.0:8080/q/dev/top/
```

Copy to Clipboard

Toggle word wrap

If you have `jq` installed, that can format and output the JSON data in colour, run:

```
curl -s -H "Accept: application/json" http://0.0.0.0:8080/q/dev/top/ | jq
```

Copy to Clipboard

Toggle word wrap

#### [6.3.5. Using profiles Copy link](#using_profiles)

A `profile` in Camel CLI is a name (id) that refers to the configuration that is loaded automatically with Camel CLI. The default profile is named as the `application` which is a (smart default) to let Camel CLI automatic load `application.properties` (if present). This means that you can create profiles that match to a specific properties file with the same name.

For example, running with a profile named `local` means that Camel CLI will load `local.properties` instead of `application.properties` . To use a profile, specify the command line option `--profile` as shown:

```
camel run hello.java --profile = local
```

Copy to Clipboard

Toggle word wrap

You can only specify one profile name at a time, for example, `--profile=local,two` is not valid.

In the `properties` files you can configure all the configurations from [Camel Main](https://camel.apache.org/components/3.20.x/others/main.html) . To turn off and enable log masking run the following command:

```
camel.main.streamCaching = false camel.main.logMask = true
```

Copy to Clipboard

Toggle word wrap

You can also configure Camel components such as `camel-kafka` to declare the URL to the brokers:

```
camel.component.kafka.brokers = broker1:9092,broker2:9092,broker3:9092
```

Copy to Clipboard

Toggle word wrap

Note

Keys starting with `camel.jbang` are reserved keys that are used by Camel CLI internally, and allow for pre-configuring arguments for Camel CLI commands.

#### [6.3.6. Downloading JARs over the internet Copy link](#downloading_jars_over_the_internet)

By default, Camel CLI automatically resolves the dependencies needed to run Camel, this is done by JBang and Camel respectively. Camel itself detects at runtime if a component has a need for the JARs that are not currently available on the classpath, and can then automatically download the JARs.

Camel downloads these JARs in the following order:

1. from the local disk in `~/.m2/repository`
2. from the internet in Maven Central
3. from internet in the custom 3rd-party Maven repositories
4. from all the repositories found in active profiles of `~/.m2/settings.xml` or a settings file specified using `--maven-settings` option.

If you do not want the Camel CLI to download over the internet, you can turn this off with the `--download` option, as shown:

```
camel run foo.java --download = false
```

Copy to Clipboard

Toggle word wrap

#### [6.3.7. Adding custom JARs Copy link](#adding_custom_jars)

Camel CLI automatically detects the dependencies for the Camel components, languages, and data formats from its own release. This means that it is not necessary to specify which JARs to use. However, if you need to add 3rd-party custom JARs then you can specify these with the `--dep` as CLI argument in Maven GAV syntax ( `groupId:artifactId:version` ), such as:

```
camel run foo.java --dep = com.foo:acme:1.0
```

Copy to Clipboard

Toggle word wrap

```
To add a Camel dependency explicitly you can use a shorthand syntax (starting with `camel:` or `camel-`):
```

Copy to Clipboard

Toggle word wrap

```
camel run foo.java --dep = camel-saxon
```

Copy to Clipboard

Toggle word wrap

You can specify multiple dependencies separated by comma:

```
camel run foo.java --dep = camel-saxon,com.foo:acme:1.0
```

Copy to Clipboard

Toggle word wrap

#### [6.3.8. Using 3rd-party Maven repositories Copy link](#using_3rd_party_maven_repositories)

Camel CLI downloads from the local repository first, and then from the online Maven Central repository. To download from the 3rd-party Maven repositories, you must specify this as CLI argument, or in the `application.properties` file.

```
camel run foo.java --repos = https://packages.atlassian.com/maven-external
```

Copy to Clipboard

Toggle word wrap

Note

You can specify multiple repositories separated by comma.

The configuration for the 3rd-party Maven repositories is configured in the `application.properties` file with the key `camel.jbang.repos` as shown:

```
camel.jbang.repos = https://packages.atlassian.com/maven-external
```

Copy to Clipboard

Toggle word wrap

When you run Camel route, the `application.properties` is automatically loaded:

```
camel run foo.java
```

Copy to Clipboard

Toggle word wrap

You can also explicitly specify the properties file to use:

```
camel run foo.java application.properties
```

Copy to Clipboard

Toggle word wrap

Or you can specify this as a profile:

```
camel run foo.java --profile = application
```

Copy to Clipboard

Toggle word wrap

Where the profile id is the name of the properties file.

#### [6.3.9. Configuration of Maven usage Copy link](#configuration_of_maven_usage)

By default, the existing `~/.m2/settings.xml` file is loaded, so it is possible to alter the behavior of the Maven resolution process. Maven settings file provides the information about the Maven mirrors, credential configuration (potentially encrypted) or active profiles and additional repositories.

Maven repositories can use authentication and the Maven-way to configure credentials is through `<server>` elements:

```
<server>
    <id>external-repository</id>
    <username>camel</username>
    <password>{SSVqy/PexxQHvubrWhdguYuG7HnTvHlaNr6g3dJn7nk=}</password>
</server>
```

Copy to Clipboard

Toggle word wrap

While the password may be specified using plain text, we recommend you configure the maven master password first and then use it to configure repository password:

```
$ mvn -emp Master password: camel { hqXUuec2RowH8dA8vdqkF6jn4NU9ybOsDjuTmWvYj4U = }
```

Copy to Clipboard

Toggle word wrap

The above password must be added to `~/.m2/settings-security.xml` file as shown:

```
<settingsSecurity>
  <master>{hqXUuec2RowH8dA8vdqkF6jn4NU9ybOsDjuTmWvYj4U=}</master>
</settingsSecurity>
```

Copy to Clipboard

Toggle word wrap

Then you can configure a normal password:

```
$ mvn -ep Password: camel { SSVqy/PexxQHvubrWhdguYuG7HnTvHlaNr6g3dJn7nk = }
```

Copy to Clipboard

Toggle word wrap

Then you can use this password in the `<server>/<password>` configuration.

By default, Maven reads the master password from `~/.m2/settings-security.xml` file, but you can override it. Location of the `settings.xml` file itself can be specified as shown:

```
camel run foo.java --maven-settings = /path/to/settings.xml --maven-settings-security = /path/to/settings-security.xml
```

Copy to Clipboard

Toggle word wrap

If you want to run Camel application without assuming any location (even `~/.m2/settings.xml` ), use this option:

```
camel run foo.java --maven-settings = false
```

Copy to Clipboard

Toggle word wrap

#### [6.3.10. Running routes hosted on GitHub Copy link](#running_routes_hosted_on_github)

You can run a route that is hosted on the GitHub using the Camels resource loader. For example, to run one of the Camel K examples, use:

```
camel run github:apache:camel-kamelets-examples:jbang/hello-java/Hey.java
```

Copy to Clipboard

Toggle word wrap

You can also use the `https` URL for the GitHub. For example, you can browse the examples from a web-browser and then copy the URL from the browser window and run the example with Camel CLI:

```
camel run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/hello-java
```

Copy to Clipboard

Toggle word wrap

You can also use wildcards (i.e. `\*` ) to match multiple files, such as running all the groovy files:

```
camel run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/languages/*.groovy
```

Copy to Clipboard

Toggle word wrap

Or you can run all files starting with rou*:

```
camel run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/languages/rou*
```

Copy to Clipboard

Toggle word wrap

##### [6.3.10.1. Running routes from the GitHub gists Copy link](#running_routes_from_the_github_gists)

Using the gists from the GitHub is a quick way to share the small Camel routes that you can easily run. For example to run a gist, use:

```
camel run https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92
```

Copy to Clipboard

Toggle word wrap

A gist can contain one or more files, and Camel CLI will gather all relevant files, so a gist can contain multiple routes, properties files, and Java beans.

#### [6.3.11. Downloading routes hosted on the GitHub Copy link](#downloading_routes_hosted_on_the_github)

You can use Camel CLI to download the existing examples from GitHub to local disk, which allows to modify the example and to run locally. For example, you can download the `dependency injection` example by running the following command:

```
camel init https://github.com/apache/camel-kamelets-examples/tree/main/jbang/dependency-injection
```

Copy to Clipboard

Toggle word wrap

Then the files (not sub folders) are downloaded to the current directory. You can then run the example locally with:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

You can also download to the files to a new folder using the `--directory` option, for example to download the files to a folder named `myproject` , run:

```
camel init https://github.com/apache/camel-kamelets-examples/tree/main/jbang/dependency-injection --directory = myproject
```

Copy to Clipboard

Toggle word wrap

Note

When using `--directory` option, Camel will automatically clean this directory if already exists.

You can run the example in dev mode, to hot-deploy on the source code changes.

```
camel run * --dev
```

Copy to Clipboard

Toggle word wrap

You can download a single file, for example, to download one of the Camel K examples, run:

```
camel init https://github.com/apache/camel-k-examples/blob/main/generic-examples/languages/simple.groovy
```

Copy to Clipboard

Toggle word wrap

This is a groovy route, which you can run with (or use `*` ):

```
camel run simple.groovy
```

Copy to Clipboard

Toggle word wrap

##### [6.3.11.1. Downloading routes form GitHub gists Copy link](#downloading_routes_form_github_gists)

You can download the files from the gists as shown:

```
camel init https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92
```

Copy to Clipboard

Toggle word wrap

This downloads the files to local disk, which you can run afterwards:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

You can download to a new folder using the `--directory` option, for example, to download to a folder named `foobar` , run:

```
camel init https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92 --directory = foobar
```

Copy to Clipboard

Toggle word wrap

Note

When using `--directory` option, Camel automatically cleans this directory if already exists.

#### [6.3.12. Running the Camel K integrations or bindings Copy link](#running_the_camel_k_integrations_or_bindings)

Camel supports running the Camel K integrations and binding files, that are in the CRD format (Kubernetes Custom Resource Definitions).For example, to run a kamelet binding file named `joke.yaml` :

```
#!/usr/bin/env jbang camel@apache/camel run apiVersion: camel.apache.org/v1alpha1
kind: KameletBinding
metadata:
  name: joke
spec:
  source:
    ref:
      kind: Kamelet
      apiVersion: camel.apache.org/v1
      name: chuck-norris-source
    properties:
      period: 2000 sink:
    ref:
      kind: Kamelet
      apiVersion: camel.apache.org/v1
      name: log-sink
    properties:
      show-headers: false
```

Copy to Clipboard

Toggle word wrap

```
camel run joke.yaml
```

Copy to Clipboard

Toggle word wrap

#### [6.3.13. Run from the clipboard Copy link](#run_from_the_clipboard)

You can run the Camel routes directly from the OS clipboard. This allows to copy some code, and then quickly run the route.

```
camel run clipboard. < extension >
```

Copy to Clipboard

Toggle word wrap

Where `<extension>` is the type of the content of the clipboard is, such as `java` , `xml` , or `yaml` .

For example, you can copy this to your clipboard and then run the route:

```
<route>
  <from uri="timer:foo"/>
  <log message="Hello World"/>
</route>
```

Copy to Clipboard

Toggle word wrap

```
camel run clipboard.xml
```

Copy to Clipboard

Toggle word wrap

#### [6.3.14. Controlling the local Camel integrations Copy link](#controlling_the_local_camel_integrations)

To list the Camel integrations that are currently running, use the `ps` option:

```
camel ps PID   NAME                          READY  STATUS    AGE 61818 sample.camel.MyCamelApplica... 1 /1   Running  26m38s 62506 test1 1 /1   Running   4m34s
```

Copy to Clipboard

Toggle word wrap

This lists the PID, the name and age of the integration.

You can use the `stop` command to stop any of these running Camel integrations. For example to stop the `test1` , run:

```
camel stop test1
Stopping running Camel integration ( pid: 62506 )
```

Copy to Clipboard

Toggle word wrap

You can use the PID to stop the integration:

```
camel stop 62506 Stopping running Camel integration ( pid: 62506 )
```

Copy to Clipboard

Toggle word wrap

Note

You do not have to type the full name, as the stop command will match the integrations that starts with the input, for example you can type `camel stop t` to stop all integrations starting with `t` .

To stop all integrations, use the `--all` option as follows:

```
camel stop --all Stopping running Camel integration ( pid: 61818 ) Stopping running Camel integration ( pid: 62506 )
```

Copy to Clipboard

Toggle word wrap

#### [6.3.15. Controlling Quarkus integrations Copy link](#controlling_quarkus_integrations)

The Camel CLI by default only controls the Camel integrations that are running using the CLI, for example, `camel run foo.java` .

For the CLI to be able to control and manage the Quarkus applications, you need to add a dependency to these projects to integrate with the Camel CLI.

**Quarkus**

In the Quarkus application, add the following dependency:

```
<dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-cli-connector</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

#### [6.3.16. Getting the status of Camel integrations Copy link](#getting_the_status_of_camel_integrations)

The `get` command in the Camel CLI is used for getting the Camel specific status for one or all of the running Camel integrations. To display the status of the running Camel integrations, run:

```
camel get
  PID   NAME      CAMEL            PLATFORM            READY  STATUS    AGE    TOTAL  FAILED  INFLIGHT  SINCE-LAST 61818 MyCamel 3.20 .1-SNAPSHOT  Quarkus v3.2 1 /1   Running  28m34s 854 0 0 0s/0s/- 63051 test1 3.20 .1-SNAPSHOT  JBang 1 /1   Running     18s 14 0 0 0s/0s/- 63068 mygroovy 3.20 .1-SNAPSHOT  JBang 1 /1   Running      5s 2 0 0 0s/0s/-
```

Copy to Clipboard

Toggle word wrap

The `camel get` command displays the default integrations, which is equivalent to typing the `camel get integrations` or the `camel get int` commands.

This displays the overall information for the every Camel integration, where you can see the total number of messages processed. The column `Since Last` shows how long time ago the last processed message for three stages (started/completed/failed).

The value of `0s/0s/-` means that the last started and completed message just happened (0 seconds ago), and that there has not been any failed message yet. In this example, `9s/9s/1h3m` means that last started and completed message is 9 seconds ago, and last failed is 1 hour and 3 minutes ago.

You can also see the status of every routes, from all the local Camel integrations with `camel get route` :

```
camel get route
  PID   NAME      ID      FROM                        STATUS    AGE   TOTAL  FAILED  INFLIGHT  MEAN  MIN  MAX  SINCE-LAST 61818 MyCamel   hello   timer://hello?period = 2000 Running  29m2s 870 0 0 0 0 14 0s/0s/- 63051 test1 java timer://java?period = 1000 Running    46s 46 0 0 0 0 9 0s/0s/- 63068 mygroovy  groovy  timer://groovy?period = 1000 Running    34s 34 0 0 0 0 5 0s/0s/-
```

Copy to Clipboard

Toggle word wrap

Note

Use `camel get --help` to display all the available commands.

##### [6.3.16.1. Top status of the Camel integrations Copy link](#literal_top_literal_status_of_the_camel_integrations)

The `camel top` command is used for getting top utilization statistics (highest to lowest heap used memory) of the running Camel integrations.

```
camel top PID   NAME     JAVA     CAMEL            PLATFORM            STATUS    AGE         HEAP        NON-HEAP     GC     THREADS   CLASSES 22104 chuck 11.0 .13 3.20 .1-SNAPSHOT  JBang               Running   2m10s 131 /322/4294 MB 70 /73 MB  17ms ( 6 ) 7 /8 7456 /7456 14242 MyCamel 11.0 .13 3.20 .1-SNAPSHOT  Quarkus 32 .         Running  33m40s 115 /332/4294 MB 62 /66 MB  37ms ( 6 ) 16 /16 8428 /8428 22116 bar 11.0 .13 3.20 .1-SNAPSHOT  JBang               Running    2m7s 33 /268/4294 MB 54 /58 MB  20ms ( 4 ) 7 /8 6104 /6104
```

Copy to Clipboard

Toggle word wrap

The `HEAP` column shows the heap memory (used/committed/max) and the non-heap (used/committed). The `GC` column shows the garbage collection information (time and total runs). The `CLASSES` column shows the number of classes (loaded/total).

You can also see the top performing routes (highest to lowest mean processing time) of every routes, from all the local Camel integrations with `camel top route` :

```
camel top route
  PID   NAME     ID                     FROM                                 STATUS    AGE    TOTAL  FAILED  INFLIGHT  MEAN  MIN  MAX  SINCE-LAST 22104 chuck    chuck-norris-source-1  timer://chuck?period = 10000 Started     10s 1 0 0 163 163 163 9s 22116 bar      route1                 timer://yaml2?period = 1000 Started      7s 7 0 0 1 0 11 0s 22104 chuck    chuck                  kamelet://chuck-norris-source        Started     10s 1 0 0 0 0 0 9s 22104 chuck    log-sink-2             kamelet://source?routeId = log-sink-2  Started     10s 1 0 0 0 0 0 9s 14242 MyCamel  hello                  timer://hello?period = 2000 Started  31m41s 948 0 0 0 0 4 0s
```

Copy to Clipboard

Toggle word wrap

Note

Use `camel top --help` to display all the available commands.

##### [6.3.16.2. Starting and Stopping the routes Copy link](#starting_and_stopping_the_routes)

The `camel cmd` is used for executing the miscellaneous commands in the running Camel integrations, for example, the commands to start and stop the routes.

To stop all the routes in the `chuck` integration, run:

```
camel cmd stop-route chuck
```

Copy to Clipboard

Toggle word wrap

The status will be then changed to `Stopped` for the `chuck` integration:

```
camel get route
  PID   NAME     ID                     FROM                                 STATUS    AGE   TOTAL  FAILED  INFLIGHT  MEAN  MIN  MAX  SINCE-LAST 81663 chuck    chuck                  kamelet://chuck-norris-source        Stopped 600 0 0 0 0 1 4s 81663 chuck    chuck-norris-source-1  timer://chuck?period = 10000 Stopped 600 0 0 65 52 290 4s 81663 chuck    log-sink-2             kamelet://source?routeId = log-sink-2  Stopped 600 0 0 0 0 1 4s 83415 bar      route1                 timer://yaml2?period = 1000 Started  5m30s 329 0 0 0 0 10 0s 83695 MyCamel  hello                  timer://hello?period = 2000 Started  3m52s 116 0 0 0 0 9 1s
```

Copy to Clipboard

Toggle word wrap

To start the route, run:

```
camel cmd start-route chuck
```

Copy to Clipboard

Toggle word wrap

To stop `all` the routes in every the Camel integration, use the `--all` flag as follows:

```
camel cmd stop-route --all
```

Copy to Clipboard

Toggle word wrap

To start `all` the routes, use:

```
camel cmd start-route --all
```

Copy to Clipboard

Toggle word wrap

Note

You can stop one or more route by their ids by separating them using comma, for example, `camel cmd start-route --id=route1,hello` . Use the `camel cmd start-route --help` command for more details.

##### [6.3.16.3. Configuring the logging levels Copy link](#configuring_the_logging_levels)

You can see the current logging levels of the running Camel integrations by:

```
camel cmd logger
  PID   NAME   AGE   LOGGER  LEVEL 90857 bar   2m48s  root    INFO 91103 foo     20s  root    INFO
```

Copy to Clipboard

Toggle word wrap

The logging level can be changed at a runtime. For example, to change the level for the `foo` to DEBUG, run:

```
camel cmd logger --level = DEBUG foo
```

Copy to Clipboard

Toggle word wrap

Note

You can use `--all` to change logging levels for all running integrations.

##### [6.3.16.4. Listing services Copy link](#listing_services)

Some Camel integrations may host a service which clients can call, such as REST, or SOAP-WS, or socket-level services using TCP protocols. You can list the available services as shown in the example below:

```
camel get service PID   NAME       COMPONENT      PROTOCOL  SERVICE 1912 netty      netty          tcp       tcp:localhost:4444 2023 greetings  platform-http  rest      http://0.0.0.0:7777/camel/greetings/ { name } ( GET ) 2023 greetings  platform-http  http      http://0.0.0.0:7777/q/dev
```

Copy to Clipboard

Toggle word wrap

Here, you can see the two Camel integrations. The netty integration hosts a TCP service that is available on port 4444. The other Camel integration hosts a REST service that can be called via GET only. The third integration comes with embedded web console (started with the `--console` option).

Note

For a service to be listed the Camel components must be able to advertise the services using [Camel Console](https://camel.apache.org/manual/camel-console.html) .

##### [6.3.16.4.1. Listing state of Circuit Breakers Copy link](#listing_state_of_circuit_breakers)

If your Camel integration uses the link:https://camel.apache.org/components/3.20.x/eips/circuitBreaker-eip.html [Circuit Breaker], then you can output the status of the breakers with Camel CLI as follows:

```
camel get circuit-breaker
  PID   NAME  COMPONENT     ROUTE   ID               STATE      PENDING  SUCCESS  FAIL  REJECT 56033 mycb  resilience4j  route1  circuitBreaker1  HALF_OPEN 5 2 3 0
```

Copy to Clipboard

Toggle word wrap

Here we can see the circuit breaker is in `half open` state, that is a state where the breaker is attempting to transition back to closed, if the failures start to drop.

Note

You can run the command with `watch` option to show the latest state, for example:

`watch camel get circuit-breaker` .

#### [6.3.17. Scripting from the terminal using pipes Copy link](#scripting_from_the_terminal_using_pipes)

You can execute a Camel CLI file as a script that is used for terminal scripting with pipes and filters.

Note

Every time the script is executed a JVM is started with Camel. This is not very fast or low on memory usage, so use the Camel CLI terminal scripting, for example, to use the many Camel components or Kamelets to more easily send or receive data from disparate IT systems.

This requires to add the following line in top of the file, for example, as in the `upper.yaml` file below:

```
///usr/bin/env jbang --quiet camel@apache/camel pipe " $0 " " $@ " ; exit $?
# Will upper-case the input - from:
    uri: "stream:in" steps:
      - setBody:
          simple: " ${body.toUpperCase()} " - to: "stream:out"
```

Copy to Clipboard

Toggle word wrap

To execute this as a script, you need to set the execute file permission:

```
chmod +x upper.yaml
```

Copy to Clipboard

Toggle word wrap

Then you can then execute this as a script:

```
echo "Hello \n World" | ./upper.yaml
```

Copy to Clipboard

Toggle word wrap

This outputs:

```
HELLO
WORLD
```

Copy to Clipboard

Toggle word wrap

You can turn on the logging using `--logging=true` which then logs to `.camel-jbang/camel-pipe.log` file. The name of the logging file cannot be configured.

```
echo "Hello \n World" | ./upper.yaml --logging = true
```

Copy to Clipboard

Toggle word wrap

##### [6.3.17.1. Using stream:in with line vs raw mode Copy link](#using_literal_stream_in_literal_with_line_vs_raw_mode)

When using `stream:in` to read data from `System in` then the [Stream Component](https://camel.apache.org/components/3.20.x/stream-component.html) works in two modes:

- line mode (default) - reads input as single lines (separated by line breaks). Message body is a `String` .
- raw mode - reads the entire stream until *end of stream* . Message body is a `byte[]` .

Note

The default mode is due to historically how the stream component was created. Therefore, you may want to set `stream:in?readLine=false` to use raw mode.

#### [6.3.18. Running local Kamelets Copy link](#running_local_kamelets)

You can use Camel CLI to try local Kamelets, without the need to publish them on GitHub or package them in a jar.

```
camel run --local-kamelet-dir = /path/to/local/kamelets earthquake.yaml
```

Copy to Clipboard

Toggle word wrap

Note

When the kamelets are from local file system, then they can be live reloaded, if they are updated, when you run Camel CLI in `--dev` mode.

You can also point to a folder in a GitHub repository. For example:

```
camel run --local-kamelet-dir = https://github.com/apache/camel-kamelets-examples/tree/main/custom-kamelets user.java
```

Copy to Clipboard

Toggle word wrap

Note

If a kamelet is loaded from GitHub, then they cannot be live reloaded.

#### [6.3.19. Using the platform-http component Copy link](#using_the_literal_platform_http_literal_component)

When a route is started from `platform-http` then the Camel CLI automatically includes a VertX HTTP server running on port 8080. following example shows the route in a file named `server.yaml` :

```
- from : uri : "platform-http:/hello" steps : - set-body : constant : "Hello World"
```

Copy to Clipboard

Toggle word wrap

You can run this example with:

```
camel run server.yaml
```

Copy to Clipboard

Toggle word wrap

And then call the HTTP service with:

```
$ curl http://localhost:8080/hello
Hello World%
```

Copy to Clipboard

Toggle word wrap

#### [6.3.20. Using Java beans and processors Copy link](#using_java_beans_and_processors)

There is basic support for including regular Java source files together with Camel routes, and let the Camel CLI runtime compile the Java source. This means you can include smaller utility classes, POJOs, Camel Processors that the application needs.

Note

The Java source files cannot use package names.

#### [6.3.21. Debugging Copy link](#debugging)

There are two kinds of debugging available:

- `Java debugging` - Java code debugging (Standard Java)
- `Camel route debugging` - Debugging Camel routes (requires Camel tooling plugins)

##### [6.3.21.1. Java debugging Copy link](#java_debugging)

You can debug your integration scripts by using the `--debug` flag provided by JBang. However, to enable the Java debugging when starting the JVM, use the `jbang` command, instead of `camel` as shown:

```
jbang --debug camel@apache/camel run hello.yaml
Listening for transport dt_socket at address: 4004
```

Copy to Clipboard

Toggle word wrap

As you can see the default listening port is 4004 but can be configured as described in [JBang debugging](https://www.jbang.dev/documentation/guide/latest/debugging.html) .

This is a standard Java debug socket. You can then use the IDE of your choice. You can add a `Processor` to put breakpoints hit during route execution (as opposed to route definition creation).

##### [6.3.21.2. Camel route debugging Copy link](#camel_route_debugging)

The Camel route debugger is available by default (the `camel-debug` component is automatically added to the classpath). By default, it can be reached through JMX at the URL `service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi/camel` . You can then use the Integrated Development Environment (IDE) of your choice.

#### [6.3.22. Health Checks Copy link](#health_checks)

The status of health checks is accessed using the Camel CLI from the CLI as follows:

```
camel get health
  PID   NAME    AGE  ID             RL  STATE  RATE    SINCE   MESSAGE 61005 mybind   8s  camel/context   R   UP 2 /2/-  1s/3s/-
```

Copy to Clipboard

Toggle word wrap

Here you can see the Camel is `UP` . The application has been running for 8 seconds, and there are two health checks invoked.

The output shows the `default` level of checks as:

- `CamelContext` health check
- Component specific health checks (such as from `camel-kafka` or `camel-aws` )
- Custom health checks
- Any check which are not `UP`

The `RATE` column shows three numbers separated by `/` . So `2/2/-` means 2 checks in total, 2 successful and no failures. The two last columns will reset when a health check changes state as this number is the number of consecutive checks that was successful or failure. So if the health check starts to fail then the numbers could be:

```
camel get health
  PID   NAME     AGE   ID             RL  STATE   RATE    SINCE    MESSAGE 61005 mybind   3m2s  camel/context   R   UP 77 /-/3  1s/-/17s  some kind of error
```

Copy to Clipboard

Toggle word wrap

Here you can see the numbers is changed to `77/-/3` . This means the total number of checks is 77. There is no success, but the check has been failing 3 times in a row. The `SINCE` column corresponds to the `RATE` . So in this case you can see the last check was 1 second ago, and that the check has been failing for 17 second in a row.

You can use `--level=full` to output every health checks that will include consumer and route level checks as well.

A health check may often be failed due to an exception was thrown which can be shown using `--trace` flag:

```
camel get health --trace PID   NAME      AGE   ID                                      RL  STATE    RATE       SINCE     MESSAGE 61038 mykafka  6m19s  camel/context                            R   UP 187 /187/-  1s/6m16s/- 61038 mykafka  6m19s  camel/kafka-consumer-kafka-not-secure...   R  DOWN 187 /-/187  1s/-/6m16s  KafkaConsumer is not ready - Error: Invalid url in bootstrap.servers: value


------------------------------------------------------------------------------------------------------------------------
                                                       STACK-TRACE
------------------------------------------------------------------------------------------------------------------------
    PID: 61038 NAME: mykafka
    AGE: 6m19s
    CHECK-ID: camel/kafka-consumer-kafka-not-secured-source-1
    STATE: DOWN
    RATE: 187 SINCE: 6m16s
    METADATA:
        bootstrap.servers = value
        group.id = 7d8117be-41b4-4c81-b4df-cf26b928d38a
        route.id = kafka-not-secured-source-1
        topic = value
    MESSAGE: KafkaConsumer is not ready - Error: Invalid url in bootstrap.servers: value
    org.apache.kafka.common.KafkaException: Failed to construct kafka consumer
        at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:823 ) at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:664 ) at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:645 ) at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:625 ) at org.apache.camel.component.kafka.DefaultKafkaClientFactory.getConsumer ( DefaultKafkaClientFactory.java:34 ) at org.apache.camel.component.kafka.KafkaFetchRecords.createConsumer ( KafkaFetchRecords.java:241 ) at org.apache.camel.component.kafka.KafkaFetchRecords.createConsumerTask ( KafkaFetchRecords.java:201 ) at org.apache.camel.support.task.ForegroundTask.run ( ForegroundTask.java:123 ) at org.apache.camel.component.kafka.KafkaFetchRecords.run ( KafkaFetchRecords.java:125 ) at java.base/java.util.concurrent.Executors $RunnableAdapter .call ( Executors.java:515 ) at java.base/java.util.concurrent.FutureTask.run ( FutureTask.java:264 ) at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker ( ThreadPoolExecutor.java:1128 ) at java.base/java.util.concurrent.ThreadPoolExecutor $Worker .run ( ThreadPoolExecutor.java:628 ) at java.base/java.lang.Thread.run ( Thread.java:829 ) Caused by: org.apache.kafka.common.config.ConfigException: Invalid url in bootstrap.servers: value
        at org.apache.kafka.clients.ClientUtils.parseAndValidateAddresses ( ClientUtils.java:59 ) at org.apache.kafka.clients.ClientUtils.parseAndValidateAddresses ( ClientUtils.java:48 ) at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:730 ) .. . 13 more
```

Copy to Clipboard

Toggle word wrap

Here you can see that the health check fails because of the `org.apache.kafka.common.config.ConfigException` which is due to invalid configuration: `Invalid url in bootstrap.servers: value` .

Note

Use `camel get health --help` to see all the various options.

### [6.4. Listing what Camel components is available Copy link](#camel-jbang-cq-listing-camel-components-cq)

Camel comes with a lot of artifacts out of the box which are:

- components
- data formats
- expression languages
- miscellaneous components
- kamelets

You can use the Camel CLI to list what Camel provides using the `camel catalog` command. For example, to list all the components:

```
camel catalog components
```

Copy to Clipboard

Toggle word wrap

To see which Kamelets are available:

```
camel catalog kamelets
```

Copy to Clipboard

Toggle word wrap

Note

Use `camel catalog --help` to see all possible commands.

#### [6.4.1. Displaying component documentation Copy link](#displaying_component_documentation)

The `doc` goal can show quick documentation for every component, dataformat, and kamelets. For example, to see the kafka component run:

```
camel doc kafka
```

Copy to Clipboard

Toggle word wrap

Note

The documentation is not the full documentation as shown on the website, as the Camel CLI does not have direct access to this information and can only show a basic description of the component, but include tables for every configuration option.

To see the documentation for jackson dataformat:

```
camel doc jackson
```

Copy to Clipboard

Toggle word wrap

In some rare cases then there may be a component and dataformat with the same name, and the `doc` goal prioritizes components. In such a situation you can prefix the name with dataformat, for example:

```
camel doc dataformat:thrift
```

Copy to Clipboard

Toggle word wrap

You can also see the kamelet documentation such as shown:

```
camel doc aws-kinesis-sink
```

Copy to Clipboard

Toggle word wrap

Note

See [Supported Kamelets](https://access.redhat.com/documentation/en-us/red_hat_integration/2023.q1/html/kamelets_reference/index) for the list of supported kamelets.

##### [6.4.1.1. Browsing online documentation from the Camel website Copy link](#browsing_online_documentation_from_the_camel_website)

You can use the `doc` command to quickly open the url in the web browser for the online documentation. For example to browse the kafka component, you use `--open-url` :

```
camel doc kafka --open-url
```

Copy to Clipboard

Toggle word wrap

This also works for data formats, languages, kamelets.

```
camel doc aws-kinesis-sink --open-url
```

Copy to Clipboard

Toggle word wrap

Note

To just get the link to the online documentation, then use `camel doc kafka --url` .

##### [6.4.1.2. Filtering options listed in the tables Copy link](#filtering_options_listed_in_the_tables)

Some components may have many options, and in such cases you can use the `--filter` option to only list the options that match the filter either in the name, description, or the group (producer, security, advanced).

For example, to list only security related options:

```
camel doc kafka --filter = security
```

Copy to Clipboard

Toggle word wrap

To list only something about `timeout` :

```
camel doc kafka --filter = timeout
```

Copy to Clipboard

Toggle word wrap

### [6.5. Gathering list of dependencies Copy link](#camel-jbang-cq-list-of-dependencies-cq)

The dependencies are automatically resolved when you work with Camel CLI. This means that you do not have to use a build system like Maven or Gradle to add every Camel components as a dependency.

However, you may want to know what dependencies are required to run the Camel integration. You can use the `dependencies` command to see the dependencies required. The command output does not output a detailed tree, such as `mvn dependencies:tree` , as the output is intended to list which Camel components, and other JARs needed (when using Kamelets).

The dependency output by default is `vanilla` Apache Camel with the `camel-main` as runtime, as shown:

```
camel dependency
org.apache.camel:camel-dsl-modeline:4.8.3
org.apache.camel:camel-health:4.8.3
org.apache.camel:camel-kamelet:4.8.3
org.apache.camel:camel-log:4.8.3
org.apache.camel:camel-rest:4.8.3
org.apache.camel:camel-stream:4.8.3
org.apache.camel:camel-timer:4.8.3
org.apache.camel:camel-yaml-dsl:4.8.3
org.apache.camel.kamelets:camel-kamelets-utils:0.9.3
org.apache.camel.kamelets:camel-kamelets:0.9.3
```

Copy to Clipboard

Toggle word wrap

The output is by default a line per maven dependency in GAV format (groupId:artifactId:version).

You can specify the `Maven` format for the the output as shown:

```
camel dependency --output=maven
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-main</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-dsl-modeline</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-health</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-kamelet</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-log</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-rest</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-stream</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-timer</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-yaml-dsl</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>0.9.3</version>
</dependency>
<dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets</artifactId>
    <version>0.9.3</version>
</dependency>
```

Copy to Clipboard

Toggle word wrap

You can also choose the target runtime as either `quarkus` as shown:

```
camel dependency --runtime = quarkus
org.apache.camel.quarkus:camel-quarkus-core:3.15
org.apache.camel.quarkus:camel-quarkus-debug:3.15
org.apache.camel.quarkus:camel-quarkus-microprofile-health:3.15
org.apache.camel.quarkus:camel-quarkus-platform-http:3.15
org.apache.camel.quarkus:camel-timer:3.15
org.apache.camel:camel-cli-connector: { camel-sb-version } org.apache.camel:camel-management: { camel-sb-version } org.apache.camel:camel-rest: { camel-sb-version } org.apache.camel:camel-timer: { camel-sb-version } org.apache.camel:camel-xml-io-dsl: { camel-sb-version } org.apache.camel:camel-yaml-dsl: { camel-sb-version }
```

Copy to Clipboard

Toggle word wrap

### [6.6. Open API Copy link](#camel-jbang-open-api-cq)

Camel CLI allows to quickly expose an Open API service using `contract first` approach, where you have an existing OpenAPI specification file. Camel CLI bridges each API endpoints from the OpenAPI specification to a Camel route with the naming convention `direct:<operationId>` . This make it quicker to implement a Camel route for a given operation.

See the [OpenAPI example](https://github.com/apache/camel-kamelets-examples/tree/main/jbang/open-api) for more details.

### [6.7. Troubleshooting Copy link](#camel-jbang-troubleshooting-cq)

When you use JBang, it stores the state in `~/.jbang` directory. This is also the location where JBang stores downloaded JARs. Camel CLI also downloads the needed dependencies while running.

However, these dependencies are downloaded to your local Maven repository `~/.m2` . So when you troubleshoot the problems such as an outdated JAR while running the Camel CLI, try to delete these directories, or parts of it.

### [6.8. Exporting to Red Hat build of Apache Camel for Quarkus Copy link](#camel-jbang-cq-exporting-to-camel-quarkus-cq)

You can `export` your Camel CLI integration to a traditional Java based project. You may want to do this after you have built a prototype using Camel CLI, and are in the need of a traditional Java based project with more need for Java coding, or to use the powerful runtimes of Quarkus or vanilla Camel Main.

#### [6.8.1. Exporting to Red Hat build of Apache Camel for Quarkus Copy link](#exporting_to_red_hat_build_of_apache_camel_for_quarkus)

The command `export --runtime=quarkus` exports your current Camel CLI file(s) to a Maven based project with files organized in `src/main/` folder structure.

For example, to export using the `quarkus` runtime, the maven groupID `com.foo` , the artifactId `acme` , and the version `1.0-SNAPSHOT` into the `camel-quarkus-jbang` directory, run:

**Example**

```
camel export --runtime = quarkus --gav = com.foo:acme:1.0-SNAPSHOT --quarkus-group-id = com.redhat.quarkus.platform --quarkus-version = 3.15 .4.redhat-00001 --dep = org.apache.camel.quarkus:camel-quarkus-timer,org.apache.camel.quarkus:camel-quarkus-management,org.apache.camel.quarkus:camel-quarkus-cli-connector --repos = https://maven.repository.redhat.com/ga,https://packages.atlassian.com/maven-external --directory = camel-quarkus-jbang
```

Copy to Clipboard

Toggle word wrap

Note

This will export to the `current` directory, this means that files are moved into the needed folder structure.

To export to another directory, run:

```
camel export --runtime = quarkus --gav = com.foo:acme:1.0-SNAPSHOT --directory = .. /myproject
```

Copy to Clipboard

Toggle word wrap

When exporting, the Camel version defined in the `pom.xml` or `build.gradle` is the same version as Camel CLI uses. However, you can specify the different Camel version as shown:

```
camel export --runtime = quarkus --gav = com.foo:acme:1.0-SNAPSHOT --directory = .. /myproject --quarkus-version = 3.15 .4.redhat-00001
```

Copy to Clipboard

Toggle word wrap

Note

See the possible options by running the `camel export --help` command for more details.

#### [6.8.2. Exporting with Camel CLI included Copy link](#exporting_with_camel_cli_included)

When exporting to Quarkus or Camel Main, the Camel JBang CLI is not included out of the box. To continue to use the Camel CLI (that is `camel` ), you need to add `camel:cli-connector` in the `--dep` option, as shown:

```
camel export --runtime = quarkus --gav = com.foo:acme:1.0-SNAPSHOT --dep = camel:cli-connector --directory = .. /myproject
```

Copy to Clipboard

Toggle word wrap

#### [6.8.3. Configuring the export Copy link](#configuring_the_export)

The export command by default loads the configuration from `application.properties` file which is used for exporting specific parameters such as selecting the runtime and java version.

The following options related to `exporting` , can be configured in the `application.properties` file:

Expand

| Option                                    | Description                                                                                                                                                                                      |
|-------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` camel.jbang.runtime ```               | Runtime (  ``` quarkus ```  , or  ``` camel-main ```  )                                                                                                                                          |
| ``` camel.jbang.gav ```                   | The Maven group:artifact:version                                                                                                                                                                 |
| ``` camel.jbang.dependencies ```          | Additional dependencies (Use commas to separate multiple dependencies). See more details at  [Adding custom JARs](https://camel.apache.org/manual/camel-jbang.html#_adding_custom_jars)  .       |
| ``` camel.jbang.classpathFiles ```        | Additional files to add to classpath (Use commas to separate multiple files). See more details at  [Adding custom JARs](https://camel.apache.org/manual/camel-jbang.html#_adding_custom_jars)  . |
| ``` camel.jbang.javaVersion ```           | Java version (11 or 17)                                                                                                                                                                          |
| ``` camel.jbang.kameletsVersion ```       | Apache Camel Kamelets version                                                                                                                                                                    |
| ``` camel.jbang.localKameletDir ```       | Local directory for loading Kamelets                                                                                                                                                             |
| ``` camel.jbang.quarkusGroupId ```        | Quarkus Platform Maven groupId                                                                                                                                                                   |
| ``` camel.jbang.quarkusArtifactId ```     | Quarkus Platform Maven artifactId                                                                                                                                                                |
| ``` camel.jbang.quarkusVersion ```        | Quarkus Platform version                                                                                                                                                                         |
| ``` camel.jbang.mavenWrapper ```          | Include Maven Wrapper files in exported project                                                                                                                                                  |
| ``` camel.jbang.gradleWrapper ```         | Include Gradle Wrapper files in exported project                                                                                                                                                 |
| ``` camel.jbang.buildTool ```             | Build tool to use (maven or gradle)                                                                                                                                                              |
| ``` camel.jbang.repos ```                 | Additional maven repositories for download on-demand (Use commas to separate multiple repositories)                                                                                              |
| ``` camel.jbang.mavenSettings ```         | Optional location of maven setting.xml file to configure servers, repositories, mirrors and proxies. If set to false, not even the default ~/.m2/settings.xml will be used.                      |
| ``` camel.jbang.mavenSettingsSecurity ``` | Optional location of maven settings-security.xml file to decrypt settings.xml                                                                                                                    |
| ``` camel.jbang.exportDir ```             | Directory where the project will be exported.                                                                                                                                                    |
| ``` camel.jbang.platform-http.port ```    | HTTP server port to use when running standalone Camel, such as when --console is enabled (port 8080 by default).                                                                                 |
| ``` camel.jbang.console ```               | Developer console at /q/dev on local HTTP server (port 8080 by default) when running standalone Camel.                                                                                           |
| ``` camel.jbang.health ```                | Health check at /q/health on local HTTP server (port 8080 by default) when running standalone Camel.                                                                                             |

Show more

Note

These are the options from the export command. To view more details and default values, run: `camel export --help` .

#### [6.8.4. Configuration Copy link](#configuration)

The Camel CLI `config` command is used to store and use the user configuration. This eliminates the need to specify CLI options each time. For example, to run a different Camel version, use:

**Example**

```
camel run * --camel-version=4.8
```

Copy to Clipboard

Toggle word wrap

the `camel-version` can be added to the user configuration such as:

```
camel config set camel-version = 4.8
```

Copy to Clipboard

Toggle word wrap

The `run` command uses the user configuration:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

The user configuration file is stored in `~/.camel-jbang-user.properties` .

##### [6.8.4.1. Set and unset configuration Copy link](#set_and_unset_configuration)

Every Camel CLI option is added to the user configuration. For example:

**Example**

```
camel config set gav = com.foo:acme:1.0-SNAPSHOT
camel config set runtime = quarkus
camel config set deps = org.apache.camel.quarkus:camel-timer,camel:management,camel:cli-connector
camel config set camel-version = 4.8 camel config set camel-quarkus-version = 3.15
```

Copy to Clipboard

Toggle word wrap

To export the configuration:

```
camel export
```

Copy to Clipboard

Toggle word wrap

To initialize the camel app:

```
camel init foo.yaml
```

Copy to Clipboard

Toggle word wrap

To run the camel app:

```
camel run foo.yaml --https://maven.repository.redhat.com/ga,https://packages.atlassian.com/maven-external
```

Copy to Clipboard

Toggle word wrap

To unset user configuration keys:

```
camel config unset camel-quarkus-version
```

Copy to Clipboard

Toggle word wrap

##### [6.8.4.2. List and get configurations Copy link](#list_and_get_configurations)

User configuration keys are listed using the following:

```
camel config list
```

Copy to Clipboard

Toggle word wrap

The configuration above gives the following output:

```
runtime = quarkus
deps = org.apache.camel.springboot:camel-timer-starter
gav = com.foo:acme:1.0-SNAPSHOT
```

Copy to Clipboard

Toggle word wrap

To obtain a value for the given key, use the `get` command.

```
camel config get gav

com.foo:acme:1.0-SNAPSHOT
```

Copy to Clipboard

Toggle word wrap

##### [6.8.4.3. Placeholders substitutes Copy link](#placeholders_substitutes)

User configuration values can be used as placeholder substitutes with command line properties, for example:

**Example**

```
camel config set repos=https://maven.repository.redhat.com/ga

camel run 'Test.java' --logging-level=info --repos=#repos,https://packages.atlassian.com/maven-external
```

Copy to Clipboard

Toggle word wrap

In this example, since repos is set in the user configuration (config set) and the camel run command declares the placeholder #repos, camel run will replace the placeholder so that both repositories will be used during the execution. Notice, that to refer to the configuration value the syntax is #optionName eg #repos.

Note

The placeholder substitution only works for every option that a given Camel command has. You can see all the options a command has using `camel run --help` .

## [Chapter 7. Using Camel CLI with Camel Spring Boot Copy link](#camel-cli-sb)

### [7.1. Installing Camel CLI Copy link](#installing-camel-jbang-sb)

**Prerequisites**

1. JBang must be installed on your machine. See [instructions](https://www.jbang.dev/download/) on how to download and install the JBang.

After the JBang is installed, you can verify JBang is working by executing the following command from a command shell:

```
jbang version
```

Copy to Clipboard

Toggle word wrap

This outputs the version of installed JBang.

**Procedure**

1. Optional: uninstall any previous versions of Camel CLI: `jbang app uninstall camel` Copy to Clipboard Toggle word wrap
2. Run the following command to install the Camel CLI application: `jbang app install -Dcamel.jbang.version = 4.8 .2 camel@apache/camel` Copy to Clipboard Toggle word wrap

Use a `camel.jbang.version` that matches the product camel version

This installs the Apache Camel as the `camel` command within JBang. This means that you can run Camel from the command line by just executing `camel` command.

### [7.2. Using Camel CLI Copy link](#using-camel-jbang-sb)

The Camel CLI supports multiple commands. The `camel help` command can display all the available commands.

```
camel --help
```

Copy to Clipboard

Toggle word wrap

Note

The first time you run this command, it may cause dependencies to be cached, therefore taking a few extra seconds to run. If you are already using JBang and you get errors such as `Exception in thread "main" java.lang.NoClassDefFoundError: "org/apache/camel/dsl/jbang/core/commands/CamelJBangMain"` , try clearing the JBang cache and re-install again.

All the commands support the `--help` and will display the appropriate help if that flag is provided.

#### [7.2.1. User configuration for Camel CLI Copy link](#user_configuration_for_camel_cli_2)

Camel CLI config command is used to store and use the user configuration. This eliminates the need to specify CLI options each time. For example, to run a different Camel version, use:

```
camel run * --camel-version=4.8.3.redhat-00004
```

Copy to Clipboard

Toggle word wrap

the `camel-version` can be added to the user configuration such as:

```
camel config set camel-version=4.8.3.redhat-00004
```

Copy to Clipboard

Toggle word wrap

This configures the Camel version that is used when you use `camel run` command. The `run` command below uses the user configuration:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

The user configuration file is stored in `~/.camel-jbang-user.properties` .

#### [7.2.2. Enable shell completion Copy link](#enable_shell_completion_2)

Camel CLI provides shell completion for bash and zsh out of the box. To enable shell completion for Camel CLI, run:

```
source < ( camel completion )
```

Copy to Clipboard

Toggle word wrap

To make it permanent, run:

```
echo 'source <(camel completion)' >> ~/.bashrc
```

Copy to Clipboard

Toggle word wrap

### [7.3. Creating and running Camel routes Copy link](#camel-jbang-running-camel-routes-sb)

You can create a new basic routes with the `init` command. For example to create an XML route, run the following command:

```
camel init cheese.xml
```

Copy to Clipboard

Toggle word wrap

This creates the file `cheese.xml` (in the current directory) with a sample route.

To run the file, run:

```
camel run --camel-version = 4.8 .3.redhat-00004 cheese.xml
```

Copy to Clipboard

Toggle word wrap

Note

You can create and run any of the supported [DSLs](https://camel.apache.org/manual/dsl.html) in Camel such as YAML, XML, Java, Groovy.

To create a new `.java` route, run:

```
camel init foo.java
```

Copy to Clipboard

Toggle word wrap

When you use the init command, Camel by default creates the file in the current directory. However, you can use the `--directory` option to create the file in the specified directory. For example to create in a folder named `foobar` , run:

```
camel init foo.java --directory = foobar
```

Copy to Clipboard

Toggle word wrap

Note

When you use the `--directory` option, Camel automatically cleans this directory if already exists.

#### [7.3.1. Running routes from multiple files Copy link](#running_routes_from_multiple_files_2)

You can run routes from more than one file, for example to run two YAML files:

```
camel run --camel-version = 4.8 .3.redhat-00004 one.yaml two.yaml
```

Copy to Clipboard

Toggle word wrap

You can run routes from two different files such as yaml and Java:

```
camel run --camel-version = 4.8 .3.redhat-00004 one.yaml hello.java
```

Copy to Clipboard

Toggle word wrap

You can use wildcards (i.e. `*` ) to match multiple files, such as running all the yaml files:

```
camel run --camel-version = 4.8 .3.redhat-00004 *.yaml
```

Copy to Clipboard

Toggle word wrap

You can run all files starting with foo*:

```
camel run --camel-version = 4.8 .3.redhat-00004 foo*
```

Copy to Clipboard

Toggle word wrap

To run all the files in the directory, use:

```
camel run --camel-version = 4.8 .3.redhat-00004 *
```

Copy to Clipboard

Toggle word wrap

Note

The `run` goal can also detect files that are `properties` , such as `application.properties` .

#### [7.3.2. Running routes from input parameter Copy link](#running_routes_from_input_parameter_2)

For very small Java routes, it is possible to provide the route as CLI argument, as shown below:

```
camel run --code = 'from("kamelet:beer-source").to("log:beer")'
```

Copy to Clipboard

Toggle word wrap

This is very limited as the CLI argument is a bit cumbersome to use than files. When you run the routes from input parameter, remember that:

- Only Java DSL code is supported.
- Code is wrapped in single quote, so you can use double quote in Java DSL.
- Code is limited to what literal values possible to provide from the terminal and JBang.
- All route(s) must be defined in a single `--code` parameter.

Note

Using `--code` is only usable for very quick and small prototypes.

#### [7.3.3. Dev mode with live reload Copy link](#dev_mode_with_live_reload_2)

You can enable the dev mode that comes with live reload of the route(s) when the source file is updated (saved), using the `--dev` options as shown:

```
camel run foo.yaml --dev
```

Copy to Clipboard

Toggle word wrap

Then while the Camel integration is running, you can update the YAML route and update when saving. This option works for all DLS including `java` , for example:

```
camel run hello.java --dev
```

Copy to Clipboard

Toggle word wrap

Note

The live reload option is meant for development purposes only, and if you encounter problems with reloading such as JVM class loading issues, then you may need to restart the integration.

#### [7.3.4. Developer Console Copy link](#developer_console_2)

You can enable the developer console, which presents a variety of information to the developer. To enable the developer console, run:

```
camel run hello.java --console
```

Copy to Clipboard

Toggle word wrap

The console is then accessible from a web browser at [http://localhost:8080/q/dev](http://localhost:8080/q/dev) (by default). The link is also displayed in the log when the Camel is starting up.

The console can give you insights into your running Camel integration, such as reporting the top routes that takes the longest time to process messages. You can then identify the slowest individual EIPs in these routes.

The developer console can also output the data in `JSON` format, that can be used by 3rd-party tooling to capture the information. For example, to output the top routes via curl, run:

```
curl -s -H "Accept: application/json" http://0.0.0.0:8080/q/dev/top/
```

Copy to Clipboard

Toggle word wrap

If you have `jq` installed, that can format and output the JSON data in colour, run:

```
curl -s -H "Accept: application/json" http://0.0.0.0:8080/q/dev/top/ | jq
```

Copy to Clipboard

Toggle word wrap

#### [7.3.5. Using profiles Copy link](#using_profiles_2)

A `profile` in Camel CLI is a name (id) that refers to the configuration that is loaded automatically with Camel CLI. The default profile is named as the `application` which is a (smart default) to let Camel CLI automatic load `application.properties` (if present). This means that you can create profiles that match to a specific properties file with the same name.

For example, running with a profile named `local` means that Camel CLI will load `local.properties` instead of `application.properties` . To use a profile, specify the command line option `--profile` as shown:

```
camel run hello.java --profile = local
```

Copy to Clipboard

Toggle word wrap

You can only specify one profile name at a time, for example, `--profile=local,two` is not valid.

In the `properties` files you can configure all the configurations from [Camel Main](https://camel.apache.org/components/4.8.0/others/main.html) . To turn off and enable log masking run the following command:

```
camel.main.streamCaching = false camel.main.logMask = true
```

Copy to Clipboard

Toggle word wrap

You can also configure Camel components such as `camel-kafka` to declare the URL to the brokers:

```
camel.component.kafka.brokers = broker1:9092,broker2:9092,broker3:9092
```

Copy to Clipboard

Toggle word wrap

Note

Keys starting with `camel.jbang` are reserved keys that are used by Camel CLI internally, and allow for pre-configuring arguments for Camel CLI commands.

#### [7.3.6. Downloading JARs over the internet Copy link](#downloading_jars_over_the_internet_2)

By default, Camel CLI automatically resolves the dependencies needed to run Camel, this is done by JBang and Camel respectively. Camel itself detects at runtime if a component has a need for the JARs that are not currently available on the classpath, and can then automatically download the JARs.

Camel downloads these JARs in the following order:

1. from the local disk in `~/.m2/repository`
2. from the internet in Maven Central
3. from internet in the custom 3rd-party Maven repositories
4. from all the repositories found in active profiles of `~/.m2/settings.xml` or a settings file specified using `--maven-settings` option.

If you do not want the Camel CLI to download over the internet, you can turn this off with the `--download` option, as shown:

```
camel run foo.java --download = false
```

Copy to Clipboard

Toggle word wrap

#### [7.3.7. Adding custom JARs Copy link](#adding_custom_jars_2)

Camel CLI automatically detects the dependencies for the Camel components, languages, and data formats from its own release. This means that it is not necessary to specify which JARs to use. However, if you need to add 3rd-party custom JARs then you can specify these with the `--dep` as CLI argument in Maven GAV syntax ( `groupId:artifactId:version` ), such as:

```
camel run foo.java --dep = com.foo:acme:1.0
```

Copy to Clipboard

Toggle word wrap

```
To add a Camel dependency explicitly you can use a shorthand syntax (starting with `camel:` or `camel-`):
```

Copy to Clipboard

Toggle word wrap

```
camel run foo.java --dep = camel-saxon
```

Copy to Clipboard

Toggle word wrap

You can specify multiple dependencies separated by comma:

```
camel run foo.java --dep = camel-saxon,com.foo:acme:1.0
```

Copy to Clipboard

Toggle word wrap

#### [7.3.8. Using 3rd-party Maven repositories Copy link](#using_3rd_party_maven_repositories_2)

Camel CLI downloads from the local repository first, and then from the online Maven Central repository. To download from the 3rd-party Maven repositories, you must specify this as CLI argument, or in the `application.properties` file.

```
camel run foo.java --repos = https://packages.atlassian.com/maven-external
```

Copy to Clipboard

Toggle word wrap

Note

You can specify multiple repositories separated by comma.

The configuration for the 3rd-party Maven repositories is configured in the `application.properties` file with the key `camel.jbang.repos` as shown:

```
camel.jbang.repos = https://packages.atlassian.com/maven-external
```

Copy to Clipboard

Toggle word wrap

When you run Camel route, the `application.properties` is automatically loaded:

```
camel run foo.java
```

Copy to Clipboard

Toggle word wrap

You can also explicitly specify the properties file to use:

```
camel run foo.java application.properties
```

Copy to Clipboard

Toggle word wrap

Or you can specify this as a profile:

```
camel run foo.java --profile = application
```

Copy to Clipboard

Toggle word wrap

Where the profile id is the name of the properties file.

#### [7.3.9. Configuration of Maven usage Copy link](#configuration_of_maven_usage_2)

By default, the existing `~/.m2/settings.xml` file is loaded, so it is possible to alter the behavior of the Maven resolution process. Maven settings file provides the information about the Maven mirrors, credential configuration (potentially encrypted) or active profiles and additional repositories.

Maven repositories can use authentication and the Maven-way to configure credentials is through `<server>` elements:

```
<server>
    <id>external-repository</id>
    <username>camel</username>
    <password>{SSVqy/PexxQHvubrWhdguYuG7HnTvHlaNr6g3dJn7nk=}</password>
</server>
```

Copy to Clipboard

Toggle word wrap

While the password may be specified using plain text, we recommend you configure the maven master password first and then use it to configure repository password:

```
$ mvn -emp Master password: camel { hqXUuec2RowH8dA8vdqkF6jn4NU9ybOsDjuTmWvYj4U = }
```

Copy to Clipboard

Toggle word wrap

The above password must be added to `~/.m2/settings-security.xml` file as shown:

```
<settingsSecurity>
  <master>{hqXUuec2RowH8dA8vdqkF6jn4NU9ybOsDjuTmWvYj4U=}</master>
</settingsSecurity>
```

Copy to Clipboard

Toggle word wrap

Then you can configure a normal password:

```
$ mvn -ep Password: camel { SSVqy/PexxQHvubrWhdguYuG7HnTvHlaNr6g3dJn7nk = }
```

Copy to Clipboard

Toggle word wrap

Then you can use this password in the `<server>/<password>` configuration.

By default, Maven reads the master password from `~/.m2/settings-security.xml` file, but you can override it. Location of the `settings.xml` file itself can be specified as shown:

```
camel run foo.java --maven-settings = /path/to/settings.xml --maven-settings-security = /path/to/settings-security.xml
```

Copy to Clipboard

Toggle word wrap

If you want to run Camel application without assuming any location (even `~/.m2/settings.xml` ), use this option:

```
camel run foo.java --maven-settings = false
```

Copy to Clipboard

Toggle word wrap

#### [7.3.10. Running routes hosted on GitHub Copy link](#running_routes_hosted_on_github_2)

You can run a route that is hosted on the GitHub using the Camels resource loader. For example, to run one of the Camel K examples, use:

```
camel run github:apache:camel-kamelets-examples:jbang/hello-java/Hey.java
```

Copy to Clipboard

Toggle word wrap

You can also use the `https` URL for the GitHub. For example, you can browse the examples from a web-browser and then copy the URL from the browser window and run the example with Camel CLI:

```
camel run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/hello-java
```

Copy to Clipboard

Toggle word wrap

You can also use wildcards (i.e. `\*` ) to match multiple files, such as running all the groovy files:

```
camel run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/languages/*.groovy
```

Copy to Clipboard

Toggle word wrap

Or you can run all files starting with rou*:

```
camel run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/languages/rou*
```

Copy to Clipboard

Toggle word wrap

##### [7.3.10.1. Running routes from the GitHub gists Copy link](#running_routes_from_the_github_gists_2)

Using the gists from the GitHub is a quick way to share the small Camel routes that you can easily run. For example to run a gist, use:

```
camel run https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92
```

Copy to Clipboard

Toggle word wrap

A gist can contain one or more files, and Camel CLI will gather all relevant files, so a gist can contain multiple routes, properties files, and Java beans.

#### [7.3.11. Downloading routes hosted on the GitHub Copy link](#downloading_routes_hosted_on_the_github_2)

You can use Camel CLI to download the existing examples from GitHub to local disk, which allows to modify the example and to run locally. For example, you can download the `dependency injection` example by running the following command:

```
camel init https://github.com/apache/camel-kamelets-examples/tree/main/jbang/dependency-injection
```

Copy to Clipboard

Toggle word wrap

Then the files (not sub folders) are downloaded to the current directory. You can then run the example locally with:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

You can also download to the files to a new folder using the `--directory` option, for example to download the files to a folder named `myproject` , run:

```
camel init https://github.com/apache/camel-kamelets-examples/tree/main/jbang/dependency-injection --directory = myproject
```

Copy to Clipboard

Toggle word wrap

Note

When using `--directory` option, Camel will automatically clean this directory if already exists.

You can run the example in dev mode, to hot-deploy on the source code changes.

```
camel run * --dev
```

Copy to Clipboard

Toggle word wrap

You can download a single file, for example, to download one of the Camel K examples, run:

```
camel init https://github.com/apache/camel-k-examples/blob/main/generic-examples/languages/simple.groovy
```

Copy to Clipboard

Toggle word wrap

This is a groovy route, which you can run with (or use `*` ):

```
camel run simple.groovy
```

Copy to Clipboard

Toggle word wrap

##### [7.3.11.1. Downloading routes form GitHub gists Copy link](#downloading_routes_form_github_gists_2)

You can download the files from the gists as shown:

```
camel init https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92
```

Copy to Clipboard

Toggle word wrap

This downloads the files to local disk, which you can run afterwards:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

You can download to a new folder using the `--directory` option, for example, to download to a folder named `foobar` , run:

```
camel init https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92 --directory = foobar
```

Copy to Clipboard

Toggle word wrap

Note

When using `--directory` option, Camel automatically cleans this directory if already exists.

#### [7.3.12. Running the Camel K integrations or bindings Copy link](#running_the_camel_k_integrations_or_bindings_2)

Camel supports running the Camel K integrations and binding files, that are in the CRD format (Kubernetes Custom Resource Definitions).For example, to run a kamelet binding file named `joke.yaml` :

```
#!/usr/bin/env jbang camel@apache/camel run apiVersion: camel.apache.org/v1alpha1
kind: KameletBinding
metadata:
  name: joke
spec:
  source:
    ref:
      kind: Kamelet
      apiVersion: camel.apache.org/v1
      name: chuck-norris-source
    properties:
      period: 2000 sink:
    ref:
      kind: Kamelet
      apiVersion: camel.apache.org/v1
      name: log-sink
    properties:
      show-headers: false
```

Copy to Clipboard

Toggle word wrap

```
camel run joke.yaml
```

Copy to Clipboard

Toggle word wrap

#### [7.3.13. Run from the clipboard Copy link](#run_from_the_clipboard_2)

You can run the Camel routes directly from the OS clipboard. This allows to copy some code, and then quickly run the route.

```
camel run clipboard. < extension >
```

Copy to Clipboard

Toggle word wrap

Where `<extension>` is the type of the content of the clipboard is, such as `java` , `xml` , or `yaml` .

For example, you can copy this to your clipboard and then run the route:

```
<route>
  <from uri="timer:foo"/>
  <log message="Hello World"/>
</route>
```

Copy to Clipboard

Toggle word wrap

```
camel run clipboard.xml
```

Copy to Clipboard

Toggle word wrap

#### [7.3.14. Controlling the local Camel integrations Copy link](#controlling_the_local_camel_integrations_2)

To list the Camel integrations that are currently running, use the `ps` option:

```
camel ps PID   NAME                          READY  STATUS    AGE 61818 sample.camel.MyCamelApplica... 1 /1   Running  26m38s 62506 test1 1 /1   Running   4m34s
```

Copy to Clipboard

Toggle word wrap

This lists the PID, the name and age of the integration.

You can use the `stop` command to stop any of these running Camel integrations. For example to stop the `test1` , run:

```
camel stop test1
Stopping running Camel integration ( pid: 62506 )
```

Copy to Clipboard

Toggle word wrap

You can use the PID to stop the integration:

```
camel stop 62506 Stopping running Camel integration ( pid: 62506 )
```

Copy to Clipboard

Toggle word wrap

Note

You do not have to type the full name, as the stop command will match the integrations that starts with the input, for example you can type `camel stop t` to stop all integrations starting with `t` .

To stop all integrations, use the `--all` option as follows:

```
camel stop --all Stopping running Camel integration ( pid: 61818 ) Stopping running Camel integration ( pid: 62506 )
```

Copy to Clipboard

Toggle word wrap

#### [7.3.15. Controlling the Spring Boot and Quarkus integrations Copy link](#controlling_the_spring_boot_and_quarkus_integrations)

The Camel CLI by default only controls the Camel integrations that are running using the CLI, for example, `camel run foo.java` .

For the CLI to be able to control and manage the Spring Boot or Quarkus applications, you need to add a dependency to these projects to integrate with the Camel CLI.

**Spring Boot**

In the Spring Boot application, add the following dependency:

```
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-cli-connector-starter</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

**Quarkus**

In the Quarkus application, add the following dependency:

```
<dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-cli-connector</artifactId>
</dependency>
```

Copy to Clipboard

Toggle word wrap

#### [7.3.16. Getting the status of Camel integrations Copy link](#getting_the_status_of_camel_integrations_2)

The `get` command in the Camel CLI is used for getting the Camel specific status for one or all of the running Camel integrations. To display the status of the running Camel integrations, run:

```
camel get
  PID   NAME      CAMEL            PLATFORM            READY  STATUS    AGE    TOTAL  FAILED  INFLIGHT  SINCE-LAST 61818 MyCamel 4.8 .0-SNAPSHOT  Spring Boot v2.7.3 1 /1   Running  28m34s 854 0 0 0s/0s/- 63051 test1 4.8 .0-SNAPSHOT  JBang 1 /1   Running     18s 14 0 0 0s/0s/- 63068 mygroovy 4.8 .0-SNAPSHOT  JBang 1 /1   Running      5s 2 0 0 0s/0s/-
```

Copy to Clipboard

Toggle word wrap

The `camel get` command displays the default integrations, which is equivalent to typing the `camel get integrations` or the `camel get int` commands.

This displays the overall information for the every Camel integration, where you can see the total number of messages processed. The column `Since Last` shows how long time ago the last processed message for three stages (started/completed/failed).

The value of `0s/0s/-` means that the last started and completed message just happened (0 seconds ago), and that there has not been any failed message yet. In this example, `9s/9s/1h3m` means that last started and completed message is 9 seconds ago, and last failed is 1 hour and 3 minutes ago.

You can also see the status of every routes, from all the local Camel integrations with `camel get route` :

```
camel get route
  PID   NAME      ID      FROM                        STATUS    AGE   TOTAL  FAILED  INFLIGHT  MEAN  MIN  MAX  SINCE-LAST 61818 MyCamel   hello   timer://hello?period = 2000 Running  29m2s 870 0 0 0 0 14 0s/0s/- 63051 test1 java timer://java?period = 1000 Running    46s 46 0 0 0 0 9 0s/0s/- 63068 mygroovy  groovy  timer://groovy?period = 1000 Running    34s 34 0 0 0 0 5 0s/0s/-
```

Copy to Clipboard

Toggle word wrap

Note

Use `camel get --help` to display all the available commands.

##### [7.3.16.1. Top status of the Camel integrations Copy link](#literal_top_literal_status_of_the_camel_integrations_2)

The `camel top` command is used for getting top utilization statistics (highest to lowest heap used memory) of the running Camel integrations.

```
camel top PID   NAME     JAVA     CAMEL            PLATFORM            STATUS    AGE         HEAP        NON-HEAP     GC     THREADS   CLASSES 22104 chuck 11.0 .13 4.8 .0-SNAPSHOT  JBang               Running   2m10s 131 /322/4294 MB 70 /73 MB  17ms ( 6 ) 7 /8 7456 /7456 14242 MyCamel 11.0 .13 4.8 .0-SNAPSHOT  Spring Boot v2.7.3  Running  33m40s 115 /332/4294 MB 62 /66 MB  37ms ( 6 ) 16 /16 8428 /8428 22116 bar 11.0 .13 4.8 .0-SNAPSHOT  JBang               Running    2m7s 33 /268/4294 MB 54 /58 MB  20ms ( 4 ) 7 /8 6104 /6104
```

Copy to Clipboard

Toggle word wrap

The `HEAP` column shows the heap memory (used/committed/max) and the non-heap (used/committed). The `GC` column shows the garbage collection information (time and total runs). The `CLASSES` column shows the number of classes (loaded/total).

You can also see the top performing routes (highest to lowest mean processing time) of every routes, from all the local Camel integrations with `camel top route` :

```
camel top route
  PID   NAME     ID                     FROM                                 STATUS    AGE    TOTAL  FAILED  INFLIGHT  MEAN  MIN  MAX  SINCE-LAST 22104 chuck    chuck-norris-source-1  timer://chuck?period = 10000 Started     10s 1 0 0 163 163 163 9s 22116 bar      route1                 timer://yaml2?period = 1000 Started      7s 7 0 0 1 0 11 0s 22104 chuck    chuck                  kamelet://chuck-norris-source        Started     10s 1 0 0 0 0 0 9s 22104 chuck    log-sink-2             kamelet://source?routeId = log-sink-2  Started     10s 1 0 0 0 0 0 9s 14242 MyCamel  hello                  timer://hello?period = 2000 Started  31m41s 948 0 0 0 0 4 0s
```

Copy to Clipboard

Toggle word wrap

Note

Use `camel top --help` to display all the available commands.

##### [7.3.16.2. Starting and Stopping the routes Copy link](#starting_and_stopping_the_routes_2)

The `camel cmd` is used for executing the miscellaneous commands in the running Camel integrations, for example, the commands to start and stop the routes.

To stop all the routes in the `chuck` integration, run:

```
camel cmd stop-route chuck
```

Copy to Clipboard

Toggle word wrap

The status will be then changed to `Stopped` for the `chuck` integration:

```
camel get route
  PID   NAME     ID                     FROM                                 STATUS    AGE   TOTAL  FAILED  INFLIGHT  MEAN  MIN  MAX  SINCE-LAST 81663 chuck    chuck                  kamelet://chuck-norris-source        Stopped 600 0 0 0 0 1 4s 81663 chuck    chuck-norris-source-1  timer://chuck?period = 10000 Stopped 600 0 0 65 52 290 4s 81663 chuck    log-sink-2             kamelet://source?routeId = log-sink-2  Stopped 600 0 0 0 0 1 4s 83415 bar      route1                 timer://yaml2?period = 1000 Started  5m30s 329 0 0 0 0 10 0s 83695 MyCamel  hello                  timer://hello?period = 2000 Started  3m52s 116 0 0 0 0 9 1s
```

Copy to Clipboard

Toggle word wrap

To start the route, run:

```
camel cmd start-route chuck
```

Copy to Clipboard

Toggle word wrap

To stop `all` the routes in every the Camel integration, use the `--all` flag as follows:

```
camel cmd stop-route --all
```

Copy to Clipboard

Toggle word wrap

To start `all` the routes, use:

```
camel cmd start-route --all
```

Copy to Clipboard

Toggle word wrap

Note

You can stop one or more route by their ids by separating them using comma, for example, `camel cmd start-route --id=route1,hello` . Use the `camel cmd start-route --help` command for more details.

##### [7.3.16.3. Configuring the logging levels Copy link](#configuring_the_logging_levels_2)

You can see the current logging levels of the running Camel integrations by:

```
camel cmd logger
  PID   NAME   AGE   LOGGER  LEVEL 90857 bar   2m48s  root    INFO 91103 foo     20s  root    INFO
```

Copy to Clipboard

Toggle word wrap

The logging level can be changed at a runtime. For example, to change the level for the `foo` to DEBUG, run:

```
camel cmd logger --level = DEBUG foo
```

Copy to Clipboard

Toggle word wrap

Note

You can use `--all` to change logging levels for all running integrations.

##### [7.3.16.4. Listing services Copy link](#listing_services_2)

Some Camel integrations may host a service which clients can call, such as REST, or SOAP-WS, or socket-level services using TCP protocols. You can list the available services as shown in the example below:

```
camel get service PID   NAME       COMPONENT      PROTOCOL  SERVICE 1912 netty      netty          tcp       tcp:localhost:4444 2023 greetings  platform-http  rest      http://0.0.0.0:7777/camel/greetings/ { name } ( GET ) 2023 greetings  platform-http  http      http://0.0.0.0:7777/q/dev
```

Copy to Clipboard

Toggle word wrap

Here, you can see the two Camel integrations. The netty integration hosts a TCP service that is available on port 4444. The other Camel integration hosts a REST service that can be called via GET only. The third integration comes with embedded web console (started with the `--console` option).

Note

For a service to be listed the Camel components must be able to advertise the services using [Camel Console](https://camel.apache.org/manual/camel-console.html) .

##### [7.3.16.4.1. Listing state of Circuit Breakers Copy link](#listing_state_of_circuit_breakers_2)

If your Camel integration uses the [Circuit Breaker](https://camel.apache.org/components/4.8.x/eips/circuitBreaker-eip.html) , then you can output the status of the breakers with Camel CLI as follows:

```
camel get circuit-breaker
  PID   NAME  COMPONENT     ROUTE   ID               STATE      PENDING  SUCCESS  FAIL  REJECT 56033 mycb  resilience4j  route1  circuitBreaker1  HALF_OPEN 5 2 3 0
```

Copy to Clipboard

Toggle word wrap

Here we can see the circuit breaker is in `half open` state, that is a state where the breaker is attempting to transition back to closed, if the failures start to drop.

Note

You can run the command with `watch` option to show the latest state, for example, `watch camel get circuit-breaker` .

#### [7.3.17. Scripting from the terminal using pipes Copy link](#scripting_from_the_terminal_using_pipes_2)

You can execute a Camel CLI file as a script that is used for terminal scripting with pipes and filters.

Note

Every time the script is executed a JVM is started with Camel. This is not very fast or low on memory usage, so use the Camel CLI terminal scripting, for example, to use the many Camel components or Kamelets to more easily send or receive data from disparate IT systems.

This requires to add the following line in top of the file, for example, as in the `upper.yaml` file below:

```
///usr/bin/env jbang --quiet camel@apache/camel pipe " $0 " " $@ " ; exit $?
# Will upper-case the input - from:
    uri: "stream:in" steps:
      - setBody:
          simple: " ${body.toUpperCase()} " - to: "stream:out"
```

Copy to Clipboard

Toggle word wrap

To execute this as a script, you need to set the execute file permission:

```
chmod +x upper.yaml
```

Copy to Clipboard

Toggle word wrap

Then you can then execute this as a script:

```
echo "Hello \n World" | ./upper.yaml
```

Copy to Clipboard

Toggle word wrap

This outputs:

```
HELLO
WORLD
```

Copy to Clipboard

Toggle word wrap

You can turn on the logging using `--logging=true` which then logs to `.camel-jbang/camel-pipe.log` file. The name of the logging file cannot be configured.

```
echo "Hello \n World" | ./upper.yaml --logging = true
```

Copy to Clipboard

Toggle word wrap

##### [7.3.17.1. Using stream:in with line vs raw mode Copy link](#using_literal_stream_in_literal_with_line_vs_raw_mode_2)

When using `stream:in` to read data from `System in` then the [Stream Component](https://camel.apache.org/components/4.8.x/stream-component.html) works in two modes:

- line mode (default) - reads input as single lines (separated by line breaks). Message body is a `String` .
- raw mode - reads the entire stream until *end of stream* . Message body is a `byte[]` .

Note

The default mode is due to historically how the stream component was created. Therefore, you may want to set `stream:in?readLine=false` to use raw mode.

#### [7.3.18. Running local Kamelets Copy link](#running_local_kamelets_2)

You can use Camel CLI to try local Kamelets, without the need to publish them on GitHub or package them in a jar.

```
camel run --local-kamelet-dir = /path/to/local/kamelets earthquake.yaml
```

Copy to Clipboard

Toggle word wrap

Note

When the kamelets are from local file system, then they can be live reloaded, if they are updated, when you run Camel CLI in `--dev` mode.

You can also point to a folder in a GitHub repository. For example:

```
camel run --local-kamelet-dir = https://github.com/apache/camel-kamelets-examples/tree/main/custom-kamelets user.java
```

Copy to Clipboard

Toggle word wrap

Note

If a kamelet is loaded from GitHub, then they cannot be live reloaded.

#### [7.3.19. Using the platform-http component Copy link](#using_the_literal_platform_http_literal_component_2)

When a route is started from `platform-http` then the Camel CLI automatically includes a VertX HTTP server running on port 8080. following example shows the route in a file named `server.yaml` :

```
- from : uri : "platform-http:/hello" steps : - set-body : constant : "Hello World"
```

Copy to Clipboard

Toggle word wrap

You can run this example with:

```
camel run server.yaml
```

Copy to Clipboard

Toggle word wrap

And then call the HTTP service with:

```
$ curl http://localhost:8080/hello
Hello World%
```

Copy to Clipboard

Toggle word wrap

#### [7.3.20. Using Java beans and processors Copy link](#using_java_beans_and_processors_2)

There is basic support for including regular Java source files together with Camel routes, and let the Camel CLI runtime compile the Java source. This means you can include smaller utility classes, POJOs, Camel Processors that the application needs.

Note

The Java source files cannot use package names.

#### [7.3.21. Dependency Injection in Java classes Copy link](#dependency_injection_in_java_classes)

When running the Camel integrations with `camel-jbang` , the runtime is `camel-main` based. This means there is no Spring Boot, or Quarkus available. However, there is a support for using annotation based dependency injection in Java classes.

##### [7.3.21.1. Using Spring Boot dependency injection Copy link](#using_spring_boot_dependency_injection)

You can use the following Spring Boot annotations:

- `@org.springframework.stereotype.Component` or `@org.springframework.stereotype.Service` on class level to create an instance of the class and register in the [Registry](https://camel.apache.org/manual/registry.html) .
- `@org.springframework.beans.factory.annotation.Autowired` to dependency inject a bean on a class field. `@org.springframework.beans.factory.annotation.Qualifier` can be used to specify the bean id.
- `@org.springframework.beans.factory.annotation.Value` to inject a [property placeholder](https://camel.apache.org/manual/using-propertyplaceholder.html) . Such as a property defined in `application.properties` .
- `@org.springframework.context.annotation.Bean` on a method to create a bean by invoking the method.

#### [7.3.22. Debugging Copy link](#debugging_2)

There are two kinds of debugging available:

- `Java debugging` - Java code debugging (Standard Java)
- `Camel route debugging` - Debugging Camel routes (requires Camel tooling plugins)

##### [7.3.22.1. Java debugging Copy link](#java_debugging_2)

You can debug your integration scripts by using the `--debug` flag provided by JBang. However, to enable the Java debugging when starting the JVM, use the `jbang` command, instead of `camel` as shown:

```
jbang --debug camel@apache/camel run hello.yaml
Listening for transport dt_socket at address: 4004
```

Copy to Clipboard

Toggle word wrap

As you can see the default listening port is 4004 but can be configured as described in [JBang debugging](https://www.jbang.dev/documentation/guide/latest/debugging.html) .

This is a standard Java debug socket. You can then use the IDE of your choice. You can add a `Processor` to put breakpoints hit during route execution (as opposed to route definition creation).

##### [7.3.22.2. Camel route debugging Copy link](#camel_route_debugging_2)

The Camel route debugger is available by default (the `camel-debug` component is automatically added to the classpath). By default, it can be reached through JMX at the URL `service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi/camel` . You can then use the Integrated Development Environment (IDE) of your choice.

#### [7.3.23. Health Checks Copy link](#health_checks_2)

The status of health checks is accessed using the Camel CLI from the CLI as follows:

```
camel get health
  PID   NAME    AGE  ID             RL  STATE  RATE    SINCE   MESSAGE 61005 mybind   8s  camel/context   R   UP 2 /2/-  1s/3s/-
```

Copy to Clipboard

Toggle word wrap

Here you can see the Camel is `UP` . The application has been running for 8 seconds, and there are two health checks invoked.

The output shows the `default` level of checks as:

- `CamelContext` health check
- Component specific health checks (such as from `camel-kafka` or `camel-aws` )
- Custom health checks
- Any check which are not `UP`

The `RATE` column shows three numbers separated by `/` . So `2/2/-` means 2 checks in total, 2 successful and no failures. The two last columns will reset when a health check changes state as this number is the number of consecutive checks that was successful or failure. So if the health check starts to fail then the numbers could be:

```
camel get health
  PID   NAME     AGE   ID             RL  STATE   RATE    SINCE    MESSAGE 61005 mybind   3m2s  camel/context   R   UP 77 /-/3  1s/-/17s  some kind of error
```

Copy to Clipboard

Toggle word wrap

Here you can see the numbers is changed to `77/-/3` . This means the total number of checks is 77. There is no success, but the check has been failing 3 times in a row. The `SINCE` column corresponds to the `RATE` . So in this case you can see the last check was 1 second ago, and that the check has been failing for 17 second in a row.

You can use `--level=full` to output every health checks that will include consumer and route level checks as well.

A health check may often be failed due to an exception was thrown which can be shown using `--trace` flag:

```
camel get health --trace PID   NAME      AGE   ID                                      RL  STATE    RATE       SINCE     MESSAGE 61038 mykafka  6m19s  camel/context                            R   UP 187 /187/-  1s/6m16s/- 61038 mykafka  6m19s  camel/kafka-consumer-kafka-not-secure...   R  DOWN 187 /-/187  1s/-/6m16s  KafkaConsumer is not ready - Error: Invalid url in bootstrap.servers: value


------------------------------------------------------------------------------------------------------------------------
                                                       STACK-TRACE
------------------------------------------------------------------------------------------------------------------------
    PID: 61038 NAME: mykafka
    AGE: 6m19s
    CHECK-ID: camel/kafka-consumer-kafka-not-secured-source-1
    STATE: DOWN
    RATE: 187 SINCE: 6m16s
    METADATA:
        bootstrap.servers = value
        group.id = 7d8117be-41b4-4c81-b4df-cf26b928d38a
        route.id = kafka-not-secured-source-1
        topic = value
    MESSAGE: KafkaConsumer is not ready - Error: Invalid url in bootstrap.servers: value
    org.apache.kafka.common.KafkaException: Failed to construct kafka consumer
        at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:823 ) at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:664 ) at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:645 ) at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:625 ) at org.apache.camel.component.kafka.DefaultKafkaClientFactory.getConsumer ( DefaultKafkaClientFactory.java:34 ) at org.apache.camel.component.kafka.KafkaFetchRecords.createConsumer ( KafkaFetchRecords.java:241 ) at org.apache.camel.component.kafka.KafkaFetchRecords.createConsumerTask ( KafkaFetchRecords.java:201 ) at org.apache.camel.support.task.ForegroundTask.run ( ForegroundTask.java:123 ) at org.apache.camel.component.kafka.KafkaFetchRecords.run ( KafkaFetchRecords.java:125 ) at java.base/java.util.concurrent.Executors $RunnableAdapter .call ( Executors.java:515 ) at java.base/java.util.concurrent.FutureTask.run ( FutureTask.java:264 ) at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker ( ThreadPoolExecutor.java:1128 ) at java.base/java.util.concurrent.ThreadPoolExecutor $Worker .run ( ThreadPoolExecutor.java:628 ) at java.base/java.lang.Thread.run ( Thread.java:829 ) Caused by: org.apache.kafka.common.config.ConfigException: Invalid url in bootstrap.servers: value
        at org.apache.kafka.clients.ClientUtils.parseAndValidateAddresses ( ClientUtils.java:59 ) at org.apache.kafka.clients.ClientUtils.parseAndValidateAddresses ( ClientUtils.java:48 ) at org.apache.kafka.clients.consumer.KafkaConsumer. < init > ( KafkaConsumer.java:730 ) .. . 13 more
```

Copy to Clipboard

Toggle word wrap

Here you can see that the health check fails because of the `org.apache.kafka.common.config.ConfigException` which is due to invalid configuration: `Invalid url in bootstrap.servers: value` .

Note

Use `camel get health --help` to see all the various options.

### [7.4. Listing what Camel components is available Copy link](#camel-jbang-listing-camel-components-sb)

Camel comes with a lot of artifacts out of the box which are:

- components
- data formats
- expression languages
- miscellaneous components
- kamelets

You can use the Camel CLI to list what Camel provides using the `camel catalog` command. For example, to list all the components:

```
camel catalog components
```

Copy to Clipboard

Toggle word wrap

To see which Kamelets are available:

```
camel catalog kamelets
```

Copy to Clipboard

Toggle word wrap

Tip

Use `camel catalog --help` to see all possible commands.

#### [7.4.1. Displaying component documentation Copy link](#displaying_component_documentation_2)

The `doc` goal can show quick documentation for every component, dataformat, and kamelets. For example, to see the kafka component run:

```
camel doc kafka
```

Copy to Clipboard

Toggle word wrap

Note

The documentation is not the full documentation as shown on the website, as the Camel CLI does not have direct access to this information and can only show a basic description of the component, but include tables for every configuration option.

To see the documentation for jackson dataformat:

```
camel doc jackson
```

Copy to Clipboard

Toggle word wrap

In some rare cases then there may be a component and dataformat with the same name, and the `doc` goal prioritizes components. In such a situation you can prefix the name with dataformat, for example:

```
camel doc dataformat:thrift
```

Copy to Clipboard

Toggle word wrap

You can also see the kamelet documentation such as shown:

```
camel doc aws-kinesis-sink
```

Copy to Clipboard

Toggle word wrap

Note

See [Supported Kamelets](https://access.redhat.com/documentation/en-us/red_hat_integration/2023.q1/html/kamelets_reference/index) for the list of supported kamelets.

##### [7.4.1.1. Browsing online documentation from the Camel website Copy link](#browsing_online_documentation_from_the_camel_website_2)

You can use the `doc` command to quickly open the url in the web browser for the online documentation. For example to browse the kafka component, you use `--open-url` :

```
camel doc kafka --open-url
```

Copy to Clipboard

Toggle word wrap

This also works for data formats, languages, kamelets.

```
camel doc aws-kinesis-sink --open-url
```

Copy to Clipboard

Toggle word wrap

Note

To just get the link to the online documentation, then use `camel doc kafka --url` .

##### [7.4.1.2. Filtering options listed in the tables Copy link](#filtering_options_listed_in_the_tables_2)

Some components may have many options, and in such cases you can use the `--filter` option to only list the options that match the filter either in the name, description, or the group (producer, security, advanced).

For example, to list only security related options:

```
camel doc kafka --filter = security
```

Copy to Clipboard

Toggle word wrap

To list only something about `timeout` :

```
camel doc kafka --filter = timeout
```

Copy to Clipboard

Toggle word wrap

### [7.5. Gathering list of dependencies Copy link](#camel-jbang-list-of-dependencies-sb)

The dependencies are automatically resolved when you work with Camel CLI. This means that you do not have to use a build system like Maven or Gradle to add every Camel components as a dependency.

However, you may want to know what dependencies are required to run the Camel integration. You can use the `dependencies` command to see the dependencies required. The command output does not output a detailed tree, such as `mvn dependencies:tree` , as the output is intended to list which Camel components, and other JARs needed (when using Kamelets).

The dependency output by default is `vanilla` Apache Camel with the `camel-main` as runtime, as shown:

```
camel dependency
org.apache.camel:camel-dsl-modeline:4.8.3
org.apache.camel:camel-health:4.8.3
org.apache.camel:camel-kamelet:4.8.3
org.apache.camel:camel-log:4.8.3
org.apache.camel:camel-rest:4.8.3
org.apache.camel:camel-stream:4.8.3
org.apache.camel:camel-timer:4.8.3
org.apache.camel:camel-yaml-dsl:4.8.3
org.apache.camel.kamelets:camel-kamelets-utils:0.9.3
org.apache.camel.kamelets:camel-kamelets:0.9.3
```

Copy to Clipboard

Toggle word wrap

The output is by default a line per maven dependency in GAV format (groupId:artifactId:version).

You can specify the `Maven` format for the the output as shown:

```
camel dependencies --output=maven
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-main</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-dsl-modeline</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-health</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-kamelet</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-log</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-rest</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-stream</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-timer</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-yaml-dsl</artifactId>
    <version>{camel-core-version}</version>
</dependency>
<dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets-utils</artifactId>
    <version>0.9.3</version>
</dependency>
<dependency>
    <groupId>org.apache.camel.kamelets</groupId>
    <artifactId>camel-kamelets</artifactId>
    <version>0.9.3</version>
</dependency>
```

Copy to Clipboard

Toggle word wrap

You can also choose the target runtime as either`quarkus` or `spring-boot` as shown:

```
camel dependencies --runtime = spring-boot
org.springframework.boot:spring-boot-starter-actuator:3.1.4
org.springframework.boot:spring-boot-starter-web:3.1.4
org.apache.camel.springboot:camel-spring-boot-engine-starter:4.8.3
org.apache.camel.springboot:camel-dsl-modeline-starter:4.8.3
org.apache.camel.springboot:camel-kamelet-starter:4.8.3
org.apache.camel.springboot:camel-log-starter:4.8.3
org.apache.camel.springboot:camel-rest-starter:4.8.3
org.apache.camel.springboot:camel-stream-starter:4.8.3
org.apache.camel.springboot:camel-timer-starter:4.8.3
org.apache.camel.springboot:camel-yaml-dsl-starter:3.20
org.apache.camel.kamelets:camel-kamelets-utils:0.9.3
org.apache.camel.kamelets:camel-kamelets:0.9.3
```

Copy to Clipboard

Toggle word wrap

### [7.6. Open API Copy link](#camel-jbang-open-api-sb)

Camel CLI allows to quickly expose an Open API service using `contract first` approach, where you have an existing OpenAPI specification file. Camel CLI bridges each API endpoints from the OpenAPI specification to a Camel route with the naming convention `direct:<operationId>` . This make it quicker to implement a Camel route for a given operation.

See the [OpenAPI example](https://github.com/apache/camel-kamelets-examples/tree/main/jbang/open-api) for more details.

### [7.7. Troubleshooting Copy link](#camel-jbang-troubleshooting-sb)

When you use JBang, it stores the state in `~/.jbang` directory. This is also the location where JBang stores downloaded JARs. Camel CLI also downloads the needed dependencies while running.

However, these dependencies are downloaded to your local Maven repository `~/.m2` . So when you troubleshoot the problems such as an outdated JAR while running the Camel CLI, try to delete these directories, or parts of it.

### [7.8. Exporting to Camel Spring Boot Copy link](#camel-jbang-exporting-to-camel-spring-boot-sb)

You can `export` your Camel CLI integration to a traditional Java based project such as Spring Boot or Quarkus. You may want to do this after you have built a prototype using Camel CLI, and are in the need of a traditional Java based project with more need for Java coding, or to use the powerful runtimes of Spring Boot, Quarkus or vanilla Camel Main.

#### [7.8.1. Exporting to Camel Spring Boot Copy link](#exporting_to_camel_spring_boot)

The command `export --runtime=spring-boot` exports your current Camel CLI file(s) to a Maven based Spring Boot project with files organized in `src/main/` folder structure.

For example, to export to the Spring Boot using the Maven groupId `com.foo` and the artifactId `acme` and with version `1.0-SNAPSHOT` , run:

```
camel export --runtime = spring-boot --gav = com.foo:acme:1.0-SNAPSHOT
```

Copy to Clipboard

Toggle word wrap

Note

This will export to the `current` directory, this means that files are moved into the needed folder structure.

To export to another directory, run:

```
camel export --runtime = spring-boot --gav = com.foo:acme:1.0-SNAPSHOT --directory = .. /myproject
```

Copy to Clipboard

Toggle word wrap

When exporting to the Spring Boot, the Camel version defined in the `pom.xml` or `build.gradle` is the same version as Camel CLI uses. However, you can specify a different Camel version as shown:

```
camel export --runtime = spring-boot --gav = com.foo:acme:1.0-SNAPSHOT --directory = .. /myproject --camel-spring-boot-version = 4.8 .3.redhat-00009
```

Copy to Clipboard

Toggle word wrap

See the possible options by running the `camel export --help` command for more details.

#### [7.8.2. Exporting with Camel CLI included Copy link](#exporting_with_camel_cli_included_2)

When exporting to Spring Boot, Quarkus or Camel Main, the Camel JBang CLI is not included out of the box. To continue to use the Camel CLI (that is `camel` ), you need to add `camel:cli-connector` in the `--dep` option, as shown:

```
camel export --runtime = spring-boot --gav = com.foo:acme:1.0-SNAPSHOT --dep = camel:cli-connector --directory = .. /myproject
```

Copy to Clipboard

Toggle word wrap

#### [7.8.3. Configuring the export Copy link](#configuring_the_export_2)

The export command by default loads the configuration from `application.properties` file which is used for exporting specific parameters such as selecting the runtime and java version.

The following options related to `exporting` , can be configured in the `application.properties` file:

Expand

| Option                                     | Description                                                                                                                                                                                      |
|--------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ``` camel.jbang.runtime ```                | Runtime (spring-boot, quarkus, or camel-main)                                                                                                                                                    |
| ``` camel.jbang.gav ```                    | The Maven group:artifact:version                                                                                                                                                                 |
| ``` camel.jbang.dependencies ```           | Additional dependencies (Use commas to separate multiple dependencies). See more details at  [Adding custom JARs](https://camel.apache.org/manual/camel-jbang.html#_adding_custom_jars)  .       |
| ``` camel.jbang.classpathFiles ```         | Additional files to add to classpath (Use commas to separate multiple files). See more details at  [Adding custom JARs](https://camel.apache.org/manual/camel-jbang.html#_adding_custom_jars)  . |
| ``` camel.jbang.javaVersion ```            | Java version (11 or 17)                                                                                                                                                                          |
| ``` camel.jbang.kameletsVersion ```        | Apache Camel Kamelets version                                                                                                                                                                    |
| ``` camel.jbang.localKameletDir ```        | Local directory for loading Kamelets                                                                                                                                                             |
| ``` camel.jbang.camelSpringBootVersion ``` | Camel version to use with Spring Boot                                                                                                                                                            |
| ``` camel.jbang.springBootVersion ```      | Spring Boot version                                                                                                                                                                              |
| ``` camel.jbang.quarkusGroupId ```         | Quarkus Platform Maven groupId                                                                                                                                                                   |
| ``` camel.jbang.quarkusArtifactId ```      | Quarkus Platform Maven artifactId                                                                                                                                                                |
| ``` camel.jbang.quarkusVersion ```         | Quarkus Platform version                                                                                                                                                                         |
| ``` camel.jbang.mavenWrapper ```           | Include Maven Wrapper files in exported project                                                                                                                                                  |
| ``` camel.jbang.gradleWrapper ```          | Include Gradle Wrapper files in exported project                                                                                                                                                 |
| ``` camel.jbang.buildTool ```              | Build tool to use (maven or gradle)                                                                                                                                                              |
| ``` camel.jbang.repos ```                  | Additional maven repositories for download on-demand (Use commas to separate multiple repositories)                                                                                              |
| ``` camel.jbang.mavenSettings ```          | Optional location of maven setting.xml file to configure servers, repositories, mirrors and proxies. If set to false, not even the default ~/.m2/settings.xml will be used.                      |
| ``` camel.jbang.mavenSettingsSecurity ```  | Optional location of maven settings-security.xml file to decrypt settings.xml                                                                                                                    |
| ``` camel.jbang.exportDir ```              | Directory where the project will be exported.                                                                                                                                                    |
| ``` camel.jbang.platform-http.port ```     | HTTP server port to use when running standalone Camel, such as when --console is enabled (port 8080 by default).                                                                                 |
| ``` camel.jbang.console ```                | Developer console at /q/dev on local HTTP server (port 8080 by default) when running standalone Camel.                                                                                           |
| ``` camel.jbang.health ```                 | Health check at /q/health on local HTTP server (port 8080 by default) when running standalone Camel.                                                                                             |

Show more

Note

These are the options from the export command. You can see more details and default values using `camel export --help` .

#### [7.8.4. Configuration Copy link](#configuration_2)

Camel CLI config command is used to store and use the user configuration. This eliminates the need to specify CLI options each time. For example, to run a different Camel version, use:

```
camel run * --camel-version=4.8
```

Copy to Clipboard

Toggle word wrap

the `camel-version` can be added to the user configuration such as:

```
camel config set camel-version=4.8
```

Copy to Clipboard

Toggle word wrap

The `run` command uses the user configuration:

```
camel run *
```

Copy to Clipboard

Toggle word wrap

The user configuration file is stored in `~/.camel-jbang-user.properties` .

##### [7.8.4.1. Set and unset configuration Copy link](#set_and_unset_configuration_2)

Every Camel CLI option is added to the user configuration. For example, to export a simple project such as

```
camel init foo.yaml
camel config set gav=com.foo:acme:1.0-SNAPSHOT
camel config set runtime=spring-boot
camel config set deps=org.apache.camel.springboot:camel-timer-starter
camel config set camel-spring-boot-version=4.8.3.redhat-00009
camel config set additional-properties=openshift-maven-plugin-version=1.17.0.redhat-00022

camel export
```

Copy to Clipboard

Toggle word wrap

User configuration keys are unset using the following:

```
camel config unset camel-spring-boot-version
```

Copy to Clipboard

Toggle word wrap

##### [7.8.4.2. List and get configurations Copy link](#list_and_get_configurations_2)

User configuration keys are listed using the following:

```
camel config list
```

Copy to Clipboard

Toggle word wrap

The output for the above mentioned configuration is as follows.

```
runtime = spring-boot
deps = org.apache.camel.springboot:camel-timer-starter
gav = com.foo:acme:1.0-SNAPSHOT
```

Copy to Clipboard

Toggle word wrap

To obtain a value for the given key, use the `get` command.

```
camel config get gav

com.foo:acme:1.0-SNAPSHOT
```

Copy to Clipboard

Toggle word wrap

##### [7.8.4.3. Placeholders substitutes Copy link](#placeholders_substitutes_2)

User configuration values can be used as placeholder substitutes with command line properties, for example:

```
camel config set repos=https://maven.repository.redhat.com/ga

camel run 'Test.java' --logging-level=info --repos=#repos,https://packages.atlassian.com/maven-external
```

Copy to Clipboard

Toggle word wrap

In this example, since repos is set in the user configuration (config set) and the camel run command declares the placeholder #repos, camel run will replace the placeholder so that both repositories will be used during the execution. Notice, that to refer to the configuration value the syntax is #optionName eg #repos.

Note

The placeholder substitution only works for every option that a given Camel command has. You can see all the options a command has using `camel run --help` .

#### [7.8.5. Troubleshooting Copy link](#troubleshooting)

When you use JBang, it stores the state in `~/.jbang` directory. This is also the location where JBang stores downloaded JARs. Camel CLI also downloads the needed dependencies while running. However, these dependencies are downloaded to your local Maven repository `~/.m2` . So when you troubleshoot the problems such as an outdated JAR while running the Camel CLI, try to delete these directories, or parts of it.

## [Legal Notice Copy link](#idm139664544634320)

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