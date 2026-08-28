package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.config.DistributionConfig;

class VersionPlaceholderResolver {

    private static final Pattern INSTALL_TIME_PLACEHOLDER = Pattern.compile("(?<!\\{)\\{([A-Z0-9_]+)\\}(?!\\})");

    void substitute(Path mdFile) throws IOException {
        substitute(mdFile, DistributionConfig.loadFromClasspathOrDefaults());
    }

    void substitute(Path mdFile, DistributionConfig dist) throws IOException {
        substitute(mdFile, dist, null);
    }

    void substitute(Path mdFile, DistributionConfig dist, String commandPrefix) throws IOException {
        String content = Files.readString(mdFile);
        Map<String, String> versionData = buildVersionTemplateData(dist);
        if (commandPrefix != null) {
            versionData.put("COMMAND_PREFIX", commandPrefix);
        }

        Matcher matcher = INSTALL_TIME_PLACEHOLDER.matcher(content);
        StringBuffer rendered = new StringBuffer(content.length());
        boolean changed = false;
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = versionData.get(key);
            if (value == null) {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        matcher.appendTail(rendered);
        Files.writeString(mdFile, rendered.toString());
    }

    private static Map<String, String> buildVersionTemplateData(DistributionConfig dist) {
        Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("CAMEL_VERSION", dist.camelMainVersion());
        data.put("CAMEL_MAIN_VERSION", dist.camelMainVersion());
        data.put("CAMEL_SPRINGBOOT_VERSION", dist.camelSpringbootVersion());
        data.put("CAMEL_QUARKUS_VERSION", dist.camelQuarkusVersion());
        data.put("SPRINGBOOT_BOM_VERSION", dist.springbootBomVersion());
        data.put("SPRING_BOOT_VERSION", dist.springBootVersion());
        data.put("QUARKUS_PLATFORM_VERSION", dist.quarkusPlatformVersion());
        data.put("CAMEL_MAIN_SUPPORTED", dist.camelMainSupported());
        data.put("CAMEL_SPRINGBOOT_SUPPORTED", dist.camelSpringbootSupported());
        data.put("CAMEL_QUARKUS_SUPPORTED", dist.camelQuarkusSupported());

        StringBuilder table = new StringBuilder();
        for (var entry : dist.quarkusPlatformMappings().entrySet()) {
            table.append("| ").append(entry.getKey())
                    .append(" | ").append(entry.getValue())
                    .append(" |\n");
        }
        data.put("QUARKUS_PLATFORM_TABLE", table.toString().stripTrailing());

        table = new StringBuilder();
        for (var entry : dist.springBootMappings().entrySet()) {
            table.append("| ").append(entry.getKey())
                    .append(" | ").append(entry.getValue())
                    .append(" |\n");
        }
        data.put("SPRING_BOOT_VERSION_TABLE", table.toString().stripTrailing());
        return data;
    }
}
