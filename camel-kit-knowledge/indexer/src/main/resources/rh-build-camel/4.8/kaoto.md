## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Kaoto
2. 1. Overview of Kaoto
3. 2. Installing Kaoto
4. 3. Getting started with Kaoto
5. 4. The Visual Designer
6. 5. Kaoto DataMapper
7. 6. Generating a catalog
8. [Legal Notice](#idm140168123263056)

Format Multi-page Single-page View full doc as PDF

# Kaoto

Red Hat build of Apache Camel 4.8

## Create and edit integrations based on Apache Camel with Kaoto

[Legal Notice](#idm140168123263056)

**Abstract**

This guide provides a comprehensive overview of Kaoto, a visual design tool tailored for creating Apache Camel integrations. It covers both the installation process and the utilization of Kaoto to expedite the development lifecycle.

## [Chapter 1. Overview of Kaoto Copy link](#overview-of-kaoto)

Important

The VS Code extensions for Apache Camel are listed as development support. For more information about the scope of development support, see [Development Support Scope of Coverage for Red Hat Build of Apache Camel](https://access.redhat.com/articles/7043889) .

[Kaoto](https://kaoto.io/) is an acronym for **K** amel **O** rchestration **T** ool. It is a low code and no code integration designer to create and edit integrations based on [Apache Camel](https://camel.apache.org/) . Kaoto is extendable, flexible, and adaptable to different use cases. For more information about the history of Kaoto, see [Statistics and History of Kaoto](https://kaoto.io/timeline) .

Kaoto offers a rich catalog of building blocks for use in graphical design. By default, Kaoto loads the official upstream [Camel Catalog](https://camel.apache.org/components/next/) and [Kamelet](https://camel.apache.org/camel-kamelets) Catalog.

Kaoto stands out as an intuitive visual platform for Apache Camel integrations, specifically designed to streamline the development process. It offers an accessible entry point for junior integration engineers through its low-code/no-code capabilities, making the initial steps of integration development straightforward. Moreover, Kaoto supports a seamless transition to more sophisticated features, allowing expert Camel developers to develop and fine-tune complex integration routes effectively.

The audience for this guide is Apache Camel developers. This guide assumes familiarity with Apache Camel and the processing requirements for your organization.

Benefits of using Kaoto can be listed as follows:

- **Enhanced Visual Development Experience** By leveraging Kaoto's visual designing capabilities, users can intuitively create, view, and edit Camel integrations through the user interface. This low-code/no-code approach significantly reduces the learning curve for new users and accelerates the development process for seasoned developers.
- **Comprehensive Component Catalog Accessibility** Kaoto provides immediate access to a rich catalog of Camel components, enterprise integration patterns (EIPs), and Kamelets. This extensive Catalog enables developers to easily find and implement the necessary components for their integration solutions. By having these resources readily available, developers can focus more on solving business problems rather than spending time searching for and learning about different components.
- **Streamlined Integration Development Process** The platform is designed with an efficient user experience in mind, optimizing the steps required to create comprehensive integrations. This efficiency is achieved through features like auto-completion, configuration forms, and interactive feedback mechanisms. As a result, developers can quickly assemble and configure integrations, reducing the overall development time. This streamlined process encourages experimentation and innovation by making it easier to prototype and test different approaches.

### [1.1. Why Kaoto? Copy link](#why_kaoto)

- **Camel at Heart** Using the power of Apache Camel: Kaoto utilizes the Apache Camel models and schemas to always offer you all available Camel features.
- **Local Development** VS Code Extension: We provide Kaoto as an extension you can install from the [Microsoft Marketplace](https://marketplace.visualstudio.com/) and also from the [Open VSX Marketplace](https://open-vsx.org/) .
- **LCNC: Low Code and No Code** Care about developers: You can seamlessly switch between any IDE and Kaoto, allowing mixed teams and converting low-code integrators to developers.
- **FLOSS heart** Free Libre and Open Source Forever: Truly open with no vendor lock-in. Use, reuse, share, modify, and resell to the users' needs.

## [Chapter 2. Installing Kaoto Copy link](#installing-kaoto)

### [2.1. Pre-requisites Copy link](#installation-of-kaoto)

#### [2.1.1. Microsoft Visual Studio Code Copy link](#microsoft_visual_studio_code)

Kaoto ships as a Microsoft Visual Studio Code extension. If you haven't installed VS Code on your manchine yet, do that now.

Visit the [download page](https://code.visualstudio.com/docs/setup/setup-overview) and follow the installation instructions that apply best for you.

#### [2.1.2. Camel CLI Copy link](#camel_cli)

To give you the best user experience we recommend to install the Camel CLI, which offers various functionalities for Camel developers.

Follow the below steps to install it.

1. Install [JBang](https://www.jbang.dev/) following these [instructions](https://www.jbang.dev/download/) .
2. Verify that [JBang](https://www.jbang.dev/) is working by executing the following from a command shell. This should output the version of installed [JBang](https://www.jbang.dev/) . `jbang version` Copy to Clipboard Toggle word wrap
3. Run the following command from a command shell to install the [Camel CLI](https://camel.apache.org/manual/camel-jbang.html) : `jbang app install camel@apache/camel` Copy to Clipboard Toggle word wrap
4. Check if the [Camel CLI](https://camel.apache.org/manual/camel-jbang.html) is working by executing the following from a command shell. This should output the version of the installed [Camel CLI](https://camel.apache.org/manual/camel-jbang.html) . `camel version` Copy to Clipboard Toggle word wrap

#### [2.1.3. Citrus Testing Copy link](#citrus_testing)

If you want to work with the Citrus testing framework, we strongly recommend to install the Citrus JBang plugin.

Follow the below steps to install it.

1. Install [JBang](https://www.jbang.dev/) following these [instructions](https://www.jbang.dev/download/) .
2. Verify that [JBang](https://www.jbang.dev/) is working by executing the following from a command shell. This should output the version of installed [JBang](https://www.jbang.dev/) . `jbang version` Copy to Clipboard Toggle word wrap
3. Run the following command from a command shell to install the [Citrus JBang plugin](https://github.com/apache/camel-jbang-examples?tab=readme-ov-file#integration-testing) : `jbang app install citrus@citrusframework/citrus` Copy to Clipboard Toggle word wrap
4. Check if the [Citrus JBang plugin](https://github.com/apache/camel-jbang-examples?tab=readme-ov-file#integration-testing) is working by executing the following from a command shell. This should output the version of the installed [Citrus JBang plugin](https://github.com/apache/camel-jbang-examples?tab=readme-ov-file#integration-testing) . `citrus --version` Copy to Clipboard Toggle word wrap

### [2.2. Installation Copy link](#installation)

Important

It is recommended to install the [Extension Pack for Apache Camel by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.apache-camel-extension-pack) . It will provide a set of tools to manage, edit, run, and debug Camel integrations in various contexts.

The following procedure explains how to install VS Code and other essential extensions required to get started with Kaoto.

1. If you do not have Visual Studio Code installed, install it from [here](https://code.visualstudio.com/docs/setup/setup-overview) .
2. Launch VS Code.
3. Install the [Extension Pack for Apache Camel by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.apache-camel-extension-pack) into your Visual Studio Code instance.
4. To run and debug Camel integrations using VS Code UI, install [JBang CLI](https://www.jbang.dev/download/) . Optionally, to run Camel integrations from the command line, install [Camel CLI](https://access.redhat.com/documentation/en-us/red_hat_build_of_apache_camel/4.0/html/tooling_guide/camel-jbang#installing-camel-jbang) .
extpack install

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
extpack install

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

Important

Create and select a workspace folder where all the integration will be stored. Selecting a workspace is important to ensure all required commands will be available and behave correctly.

## [Chapter 3. Getting started with Kaoto Copy link](#getting-started-with-kaoto)

This section describes how to:

- Setup a workspace in VS Code
- Access the important commands to create your integration
- Create your first Camel route
- Run your Camel route locally
- Get access to the source code of the Camel route

### [3.1. Preparing the Workspace Copy link](#preparing_the_workspace)

Visual Studio Code requires you to create a workspace for your project to access the full functionality of the environment.

1. If you haven't done so yet, open your Visual Studio Code instance. You can do that by finding the right launcher on your computer or by opening a command shell and executing `code` .
2. This should leave you with a window like the one below.
empty vscode

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
empty vscode

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [3.2. Executing VS Code Commands Copy link](#executing_vs_code_commands)

The following procedure explains how to create and store integrations with Kaoto.

1. Select your workspace folder which will store your project files. Go to the `File` menu and select the entry `Open Folder` . In the following screen browse to the folder you would like to use and select it. Note Some VS Code commands require a workspace to show up and function correctly. If you haven't set one up and opened it you should check the [Section 3.1, "Preparing the Workspace"](#preparing_the_workspace) section.
open folder

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
open folder

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. To access the VS Code command palette you have to press either `F1` or `CTRL+SHIFT+P` . This will open up an entry field which you can use to filter for the commands you are interested in.
5. In our case we are interested in the `Camel` related commands, so lets type `Camel` into the field and check the filtered results. `Create a Camel route using YAML DSL` Copy to Clipboard Toggle word wrap
camel commands

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
camel commands

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [3.3. Creating Camel routes Copy link](#creating-camel-routes-kaoto)

1. If you followed the steps from [Section 3.2, "Executing VS Code Commands"](#executing_vs_code_commands) section, you should still see the filtered Camel commands. Select the command called `Camel: Create a Camel route using YAML DSL` and hit `Enter` .
2. Next you are asked to provide a name for the file to be created. Feel free to pick a name without stating a file extension (example: `simpleTest` ) and hit `Enter` again. Note If the execution fails you should double check if you have installed the [Camel CLI](https://camel.apache.org/manual/camel-jbang.html) correctly. Follow the instructions outline in the section [Camel CLI](https://kaoto.io/docs/manual/02_gettingstarted/#camel-cli) section.
3. If all worked correctly you should now see your new file with the extension ``.camel.yaml` and the Kaoto Visual Designer should open up.

If the Kaoto editor doesn't open check you followed the [Installing Kaoto](#installing-kaoto) section and your file is named like `filename.camel.yaml` . If you named the file differently then rename it to match this naming rule.

1. You should now see a similar screen like the one below.
new route

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
new route

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

#### [3.3.1. Launching the Camel route Copy link](#launching_the_camel_route)

1. The Camel route we created in the [previous chapter](#creating-camel-routes-kaoto) can already be launched. Let's try to start the Route by clicking the `Run Camel Application with JBang` button in the top right of the Kaoto Visual Editor.
launch button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
launch button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. A terminal should open up and it will show you the output of your launched Camel route.
launch terminal

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
launch terminal

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. You can terminate the execution of your Camel route at any time by selecting the terminal window and pressing `CTRL+C` .

#### [3.3.2. Accessing the Source Code Copy link](#accessing_the_source_code)

You might wonder how the source of your new Camel route looks like. While Kaoto tries relief users from the burden of working with the source code, we still allow access to it via the default Visual Studio Code Text Editor.

To access your source code you can either right click the tab labeled with the filename and then select `Reopen Editor` with and then choosing `Text Editor` .

Another way of doing that is to right-click the file in the Explorer view on the left side of the window and then similarly selecting the menu item `Open With` and then choosing `Text Editor` .

If you want to revert back to the Kaoto Editor you can follow the same steps as above but instead of Text Editor you should then select `Kaoto Editor` .

## [Chapter 4. The Visual Designer Copy link](#visual-designer)

The following picture shows the different parts of the Kaoto Visual Editor.

ui overview

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

ui overview

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

1. The Explorer View with the selected file
2. The currently displayed Integration
3. The configuration panel where you can adjust and customize settings for the selected step
4. Drop down showing the currently selected integration type, here **Camel route**
5. Drop down showing the currently selected Camel route. This is handy if you have more than one Camel route defined in your file. You can rename, delete, select and switch the visibility for each Camel route
6. Drop down enabling you to add more Routes or other global elements to your integration. **Only visible for Camel routes** !
7. Copies the full source code of your integration to the Clipboard
8. Exports the currently visible Integration as a PNG image
9. Drop down showing the available Camel versions. Different runtimes are available, like Camel Main, Springboot and Quarkus.
10. This button bar provides you with functionalities like Zoom In / Out, Reset the View, Switching the layout direction between horizontal and vertical and grants you access to the comprehensive Camel Catalog, containing all the available Components/Connectors, Enterprise Integration Patterns and Kamelets
11. A step in your Integration with an Error-Marker to indicate a problem with the configuration of the step.
12. The toolbar of the selected step. It provides available actions for the current selection.

### [4.1. Working with Camel routes Copy link](#working_with_camel_routes)

In Apache Camel, a route is a set of processing steps that are applied to a message as it travels from a source to a destination. A route typically consists of a series of processing steps that are connected in a linear sequence.

A Camel route is where the integration flow is defined. For example, you can write a Camel route to specify how two systems can be integrated. You can also specify how the data can be manipulated, routed, or mediated between the systems.

#### [4.1.1. Creating a new Camel route Copy link](#creating_a_new_camel_route)

We already covered how to create a new Camel route YAML file in the chapter Create your first Camel route.

Let's use another way of creating a new Camel route.

1. If you have your route from the other chapter still open, click on the Route selection drop down and then delete all the routes using the trashbin icon.
delete route

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
delete route

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Once you confirmed the deletion of all your routes you should see a blank screen like below.
no route

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
no route

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. You can now create a new Camel route by either clicking the `+ New` button in the center of the canvas or by using the same button in the upper menu bar of the Canvas, next to the Route selection drop down, which will both put a template route in place which uses a **Timer** component to send every second a message to the **Log** component.

#### [4.1.2. Adding a step Copy link](#adding_a_step)

Now lets add a new step between the **Timer** and the **Log** component to modify the message body.

There are two ways of adding a step to the route. You can either Right-Click on the step you want to insert before or after. This will bring up a context menu with the available actions to choose from. An easier alternative would be to hover over the connection between the two steps you want to insert between and then click on the **+** button that appears.

1. Hover over the connection between the **Timer** and the **Log** steps now and click the **+** button to execute the **Add step** action. Important When using the right-click context menu, the set of available actions depend on the selected step and can vary. There are actions for appending, prepending, replacing and deleting steps as well as some more specialized actions.
step actions

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
step actions

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. This will open up the Camel Catalog where you can search the step you want to add.
catalog

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
catalog

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. As already mentioned we would like to modify the message body before sending it along to the **Log** component. To achieve that we need to add a **Processor** called **setBody** . Let's enter this name into the filter text field on top of the Catalog.
catalog setbody

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
catalog setbody

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
10. You can now select the **setBody** tile to add it to your route. Select the new added step now on the canvas to open the configuration form to the right.
setbody step

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
setbody step

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
13. Let's change the **Expression** text field to `Hello from Kaoto!` .
14. Now the **Log** component will receive a `Hello from Kaoto!` message every second and logs it to the console. Important When using the right click context menu of a step, adding new steps is usually done with two actions. **Prepend** can be used to add a step before the selected step and **Append** will add the new step after the selected step. However, on the first step of a flow and on steps that can have children, the **Add Step** action is used.

#### [4.1.3. Replacing a step Copy link](#replacing_a_step)

1. You can replace any step on the canvas by hovering over or by selecting the step. This will spawn a toolbar which contains a button for the Replace action.
replace button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
replace button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Alternatively you can do that also by invoking the context menu on a step and selecting the item **Replace** . Both ways it will open up the Camel Catalog and you can choose the replacement from there.

#### [4.1.4. Deleting a step Copy link](#deleting_a_step)

Warning

When invoking the **Delete** action on a step with children or on a container element containing children there will be a confirmation dialog because you are about to delete not just the single step or container but also all the contained children. **Be cautious** !

1. You can delete any step on the canvas by hovering over or by selecting the step. This will spawn a toolbar which contains a button for the Delete action.
delete button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
delete button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Alternatively you can do that also by invoking the context menu on a step and selecting the item **Delete** . This will remove the step from your integration.

#### [4.1.5. Enable / Disable a step Copy link](#enable_disable_a_step)

1. You can enable or disable any step on the canvas by hovering over or by selecting the step. This will spawn a toolbar which contains a button for the Enable / Disable action. Important Disabling a step will instruct the **Apache Camel** runtime to ignore the step when executing the flow. This can be convenient when prototyping a new route.
disable button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
disable button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Alternatively you can enable / disable any step in your route by invoking the context menu on a step and selecting the item **Enable / Disable** .
disabled step

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
disabled step

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. In the picture above the **Log** component has been disabled. The icon is grayed out and there is a marker icon at the top right of the step to indicate it is disabled.

## [Chapter 5. Kaoto DataMapper Copy link](#data-mapper)

Note

Currently Kaoto DataMapper is only supported inside the Visual Studio Code extension as a technical preview feature. In the future we will aim to bring this functionality also to the pure web version of Kaoto.

Note

At the moment the DataMapper only supports XML schema for rendering the data structure and it internally generates a single XSLT step to perform configured data mappings at runtime. While you can consume multiple XML documents with using Camel Variables and/or Message Headers which are mapped to XSLT parameters, the output is only a Camel Message Body.

datamapper done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

datamapper done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

In addition to the regular Camel steps, Kaoto now supports a **Kaoto DataMapper** step to be placed in the Camel route. The Kaoto DataMapper step provides a graphical user interface to create data mappings inside the Camel route.

### [5.1. Adding a DataMapper step Copy link](#adding_a_datamapper_step)

1. Add a **Kaoto DataMapper** step in your Camel route. When you `Append` , `Prepend` , or `Replace` a step in the Kaoto Design view, you can find the **Kaoto DataMapper** step in the catalog.
catalog datamapper tile

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
catalog datamapper tile

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

1. Click the added **Kaoto DataMapper** step in the Kaoto Design to open the config form.
kaoto datamapper step

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
kaoto datamapper step

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. In the **Kaoto DataMapper** config form, click the `Configure` button.
datamapper configure button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper configure button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. This will open the visual DataMapper editor.
datamapper blank

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper blank

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [5.2. Source and Target Copy link](#source_and_target)

In the DataMapper canvas, you can see `Source` at the left and `Target` section at the right side.

datamapper source target

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

datamapper source target

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

The `Source` section represents the input side of your mappings, where the DataMapper step reads the data from. This is mapped to the incoming Camel `Message` as well as possible Camel `Variables` .

The `Target` section represents the output side of your mappings, where the DataMapper step writes the data to. This is mapped to the outgoing Camel `Message` .

### [5.3. Parameters Copy link](#parameters)

The `Parameters` section inside the `Source` is mapped to any of incoming Camel `Variables` and `Message Headers` . For example, if there is an incoming Camel Variable `orderSequence` , you can consume it by adding a Parameter `orderSequence` in the DataMapper Source/Parameters section.

Follow the below steps to add a parameter:

1. Click the plus (+) button on the right side of the `Parameters` title.
datamapper add parameter

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper add parameter

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Now type the parameter name and click the check button on the right. Note While Camel Exchange Properties are also mapped to Parameters in current `camel-xslt-saxon` implementation, after the [Camel Variables](https://camel.apache.org/manual/variables.html) has been introduced, it is no longer recommended to store application data into Camel Exchange Properties. We encourage to use [Camel Variables](https://camel.apache.org/manual/variables.html) instead.
datamapper add parameter confirm

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper add parameter confirm

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [5.4. Attaching Document schema files Copy link](#attaching_document_schema_files)

If any of `Source Body` , `Target Body` and/or `Parameter(s)` are structured data, you can attach a schema file and visualize the data structure in a tree style view.

Note

If the data is not structured and just a primitive value, you don't need to attach a schema file.

Follow the below steps to attach a schema file:

1. Place schema file(s) inside the workspace directory.
2. Click `Attach a schema` button in one of the `Source Body` , `Target Body` or `Parameters` sections.
datamapper attach schema

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper attach schema

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

1. Select the schema file to attach
datamapper select schema

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper select schema

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Now the document structure is rendered inside a tree.
datamapper schema attached

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper schema attached

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [5.5. Creating simple mappings Copy link](#creating_simple_mappings)

#### [5.5.1. Creating a mapping by dragging and dropping a field Copy link](#creating_a_mapping_by_dragging_and_dropping_a_field)

When you perform drag and drop between the source and the target, a mapping is created and a line is drawn between the fields.

**Example** : Mapping the `Name` fields by dragging and dropping the source `Name` field on the target `Name` field.

**Before** :

datamapper drag name

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

datamapper drag name

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

**After** :

datamapper drop name

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

datamapper drop name

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

#### [5.5.2. Creating a mapping by typing XPath expression Copy link](#creating_a_mapping_by_typing_xpath_expression)

You can also create a mapping by entering a `XPath` expression.

1. Click the 3 dots context menu and selecton the target field and choose `Add selector expression` .
datamapper add selector

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper add selector

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Then enter the `XPath` expression
datamapper type xpath

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper type xpath

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [5.6. Creating conditional mappings Copy link](#creating_conditional_mappings)

The DataMapper supports creating 3 types of conditional mappings:

1. **`if`** - The mapping is created only when the specified condition is met.
2. **`choose-when-otherwise`** - The mapping is created depending on how the condition is satisfied. If the `when` branch condition is satisfied, the `when` branch mapping is .created. If no `when` branch condition is satisfied, then the `otherwise` branch mapping is created.
3. **`for-each`** - The mapping is created for each item in the collection. Collection means multiple occurrences, which is often represented as an array.

#### [5.6.1. Creating an if mapping Copy link](#creating_an_literal_if_literal_mapping)

1. Click the 3 dots context menu on the target section's field. Then select `wrap with "if"` to create a mapping.
datamapper if 3dots

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper if 3dots

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper if if

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper if if

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
6. Configure the `if` condition. You can drag the source field and drop it into the input field to build a condition, or alternatively type everything manually.
datamapper if condition

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper if condition

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
9. Configure the mapping by using drag and drop or by typing it manually.
datamapper if mapping

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper if mapping

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

#### [5.6.2. Creating a choose-when-otherwise mapping Copy link](#creating_a_literal_choose_when_otherwise_literal_mapping)

1. Click the 3 dots context menu on the target section's field. Then select `wrap with "choose-when-otherwise"` to create a mapping.
datamapper choose choose

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper choose choose

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Configure the `when` condition.
datamapper choose when condition

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper choose when condition

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. Configure the mapping for the `when` branch.
datamapper choose when mapping

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper choose when mapping

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
10. Configure the mapping for the `otherwise` branch.
datamapper choose otherwise mapping

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper choose otherwise mapping

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
13. If required, you can add one or more `when` branches. To add another `when` branch you can click the 3 dots menu on the `choose` field in the `Target` section and then select `Add "when"` .
datamapper choose add when

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper choose add when

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper choose when added

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper choose when added

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

#### [5.6.3. Creating a for-each mapping Copy link](#creating_a_literal_for_each_literal_mapping)

When a field is a collection field (means multiple occurrences, often represented as an array), you can create a `for-each` mapping. The layer icon on the field indicates that it is a collection field.

datamapper collection field

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

datamapper collection field

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

1. Click the 3 dots context menu on the target section's collection field. Then select wrap with "for-each" to create a mapping.
datamapper for each for each

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper for each for each

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Configure the for-each condition by specifying the source collection field to iterate over.
datamapper for each condition

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper for each condition

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. Configure the mappings below. Note that the mapping field path is now a relative path from the collection field specified in the for-each condition.
datamapper for each mappings

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper for each mappings

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [5.7. Using XPath expression editor Copy link](#using_xpath_expression_editor)

Note

The `XPath` editor is still under initial development and it currently supports only limited drag and drop. In future releases, more syntax assisting features will be added.

If you want to write something more in `XPath` expression rather than just a field path, you can launch the `XPath` expression editor and work with it. There is a pencil icon on the target field which launches the `XPath` expression editor when you click it.

1. Click the pencil button on a target field which has a mapping.
datamapper xpath pencil

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper xpath pencil

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. This will open up the `XPath` editor.
datamapper xpath editor

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper xpath editor

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. You can then type in the editor at the right or drag a `Field` from the left and drop onto the editor.
datamapper xpath dnd fields

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper xpath dnd fields

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
10. You can also drag and drop `XPath` functions from the `Function` tab on the left side.
datamapper xpath functions

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper xpath functions

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
13. Drag the function and drop it onto the editor.
datamapper xpath functions dnd

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper xpath functions dnd

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
16. Once it's completed, click the `Close` button at the bottom left.
datamapper xpath close

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper xpath close

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
19. Now you can see the new mapping in the tree view.
datamapper xpath done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper xpath done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [5.8. Deleting a mapping Copy link](#deleting_a_mapping)

1. To delete a mapping you can click the dustbin button next to the target field.
datamapper delete mapping btn

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper delete mapping btn

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. You then have to confirm the deletion by clicking the `Confirm` button.
datamapper delete mapping confirm

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper delete mapping confirm

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. Mapping is deleted.
datamapper delete mapping done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper delete mapping done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [5.9. Deleting a parameter Copy link](#deleting_a_parameter)

1. To delete a parameter, click the dustbin button next to the parameter.
datamapper delete param trash

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper delete param trash

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. You then have to confirm the deletion by clicking the `Confirm` button.
datamapper delete param confirm

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper delete param confirm

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. The parameter is deleted.
datamapper delete param done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper delete param done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

### [5.10. Detaching a schema Copy link](#detaching_a_schema)

Similar to attaching a schema you can also remove / detach a schema.

1. Click the `Detach schema` button.
datamapper detach button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper detach button

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. Click the `Confirm` button.
datamapper detach confirm

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper detach confirm

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. Now the Document got back to be a primitive value.
datamapper detach done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
datamapper detach done

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

## [Chapter 6. Generating a catalog Copy link](#generating-catalog)

### [6.1. Overview Copy link](#generating-a-catalog)

By default, every Kaoto release includes the latest Camel version available at the moment of the build, nevertheless, generating a different set of catalogs is possible.

### [6.2. Catalog generator CLI Copy link](#catalog_generator_cli)

Kaoto provides a Camel catalog generator CLI to ease this process, it supports the following runtimes:

1. Main
2. Quarkus
3. Springboot

#### [6.2.1. Using Camel catalog Copy link](#using_camel_catalog)

1. Clone the [Kaoto project](https://kaoto.io/docs/manual/09_generatingcatalog/git@github.com:KaotoIO/kaoto.git)
2. Navigate to the packages/catalog-generator directory
3. Install the project dependencies: `./mvnw install` Copy to Clipboard Toggle word wrap
4. Run the project with the following command: `./mvnw package; java -jar ./target/catalog-generator-0.0.1-SNAPSHOT.jar -o ./dist/camel-catalog -k 4.6.0 -m 4.6.0 -n "My Catalog"` Copy to Clipboard Toggle word wrap
5. This will generate a Catalog library containing:
6. The resulting files will be in the `packages/catalog-generator/dist/camel-catalog` folder
7. Providing that folder through a http server will make it available for using it in Kaoto

#### [6.2.2. Creating a Catalog library with different runtimes Copy link](#creating_a_catalog_library_with_different_runtimes)

1. In order to add multiple runtimes to the Catalog library, we can provide each runtime with its version using the following flags: `-m,--main <version> Camel Main version. If not specified, it will use the generator installed version -q,--quarkus <version> Camel Extensions for Quarkus version -s,--springboot <version> Camel SpringBoot version` Copy to Clipboard Toggle word wrap
2. For instance, running the following command will create a Catalog library with Camel Main 4.6.0 and Camel extensions for Quarkus 3.8.0: `./mvnw package; java -jar ./target/catalog-generator-0.0.1-SNAPSHOT.jar -o ./dist/camel-catalog -k 4.6.0 -m 4.6.0 -q 3.8.0 -n "My Catalog"` Copy to Clipboard Toggle word wrap
3. For a different Kamelets catalog version, the `--kamelets or -k` flag can be specified. `./mvnw package; java -jar ./target/catalog-generator-0.0.1-SNAPSHOT.jar -o ./dist/camel-catalog -k 4.5.0 -m 4.6.0 -n "My Catalog"` Copy to Clipboard Toggle word wrap

#### [6.2.3. Instructing Kaoto to use a specific Catalog library Copy link](#instructing_kaoto_to_use_a_specific_catalog_library)

1. In VSCode, go to the settings page and look for "Kaoto"
vscode kaoto settings

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
vscode kaoto settings

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
4. In the `TextField` , provide the URL of the `index.json` file that specifies the location of the subsequent catalogs, for instance, the public Kaoto catalog can be used:
setting kaoto catalog url

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
setting kaoto catalog url

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
7. Restart Kaoto for the changes to have effect
kaoto runtime selector

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->
kaoto runtime selector

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

## [Legal Notice Copy link](#idm140168123263056)

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