# Project Standards

This file contains build, test, format, and code-style expectations for the camel-kit project.

- Build tool: Maven (Maven Wrapper)
- Build command: `./mvnw clean install`
- Test command: `./mvnw test`
- Test with coverage command: `./mvnw test` (no dedicated coverage profile found)
- Format command: `./mvnw process-sources` (formatter-maven-plugin and impsort-maven-plugin configured)
- Module-specific build: yes (use `-pl <module>` flag)
- Parallelized Maven: no

## Code Style

- Java 17+
- Max line length: 120 characters
- Follow standard Java conventions
- Prefer composition over inheritance
- Follow SOLID principles
- Picocli annotations for CLI commands
- Formatter config: `camel-kit-formatter-config.xml`

## Version

3cd083a1b258655a029be8721737c091622b6101
