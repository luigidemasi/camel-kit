package io.github.luigidemasi.camelkit;

import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

    private static final String POM_PROPERTIES = "META-INF/maven/io.github.luigidemasi/camel-kit-core/pom.properties";

    @Override
    public String[] getVersion() throws Exception {
        Properties props = new Properties();
        try (InputStream is = VersionProvider.class.getResourceAsStream("/" + POM_PROPERTIES)) {
            if (is != null) {
                props.load(is);
            }
        }
        return new String[]{props.getProperty("version", "unknown")};
    }
}
