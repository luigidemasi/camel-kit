package io.github.luigidemasi.camelkit.util;

import java.io.IOException;

public class TemplateNotFoundException extends IOException {

    public TemplateNotFoundException(String templatePath) {
        super("Template not found: " + templatePath);
    }
}
