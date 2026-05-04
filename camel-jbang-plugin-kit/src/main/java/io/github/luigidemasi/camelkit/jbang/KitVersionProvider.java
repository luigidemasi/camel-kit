package io.github.luigidemasi.camelkit.jbang;

import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

public class KitVersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
        Properties props = new Properties();
        try (InputStream is = KitVersionProvider.class.getClassLoader().getResourceAsStream("version.properties")) {
            if (is != null) {
                props.load(is);
            }
        }
        return new String[]{props.getProperty("version", "unknown")};
    }
}
