///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//DEPS io.github.luigidemasi:camel-kit-core:${camel.kit.version:0.3.2-SNAPSHOT}

package main;

import io.github.luigidemasi.camelkit.CamelKitMain;

/**
 * JBang entry point for Camel-Kit CLI.
 * Design Apache Camel integrations with AI coding assistants.
 */
public class CamelKit {

    public static void main(String... args) {
        CamelKitMain.run(args);
    }
}
