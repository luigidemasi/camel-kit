package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;

public class QuteTemplateEngine {

    private final Engine engine;

    public QuteTemplateEngine() {
        this.engine = Engine.builder().addDefaults().build();
    }

    public String render(String templatePath, Map<String, Object> data) {
        String content = readFromClasspath(templatePath);
        Template template = engine.parse(content);
        var instance = template.instance();
        data.forEach(instance::data);
        return instance.render();
    }

    public String renderString(String templateContent, Map<String, Object> data) {
        Template template = engine.parse(templateContent);
        var instance = template.instance();
        data.forEach(instance::data);
        return instance.render();
    }

    private String readFromClasspath(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Template not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read template: " + path, e);
        }
    }
}
