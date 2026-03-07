package io.github.luigidemasi.camelkit.knowledge.indexer.chunker;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits Markdown text into sections at heading boundaries (##, ###, ####).
 * Each section becomes a self-contained chunk with its heading as the title.
 *
 * Designed to process Docling output, which converts all document formats
 * (PDF, HTML, AsciiDoc, etc.) into consistent Markdown with heading hierarchy.
 */
public class SectionChunker {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{2,4})\\s+(.+)$", Pattern.MULTILINE);

    /**
     * A single section extracted from the document.
     */
    public record Section(String title, String content, int level) {}

    /**
     * Split markdown text into sections at heading boundaries.
     * Content before the first heading is included as a section with title "Introduction".
     *
     * @param markdown the full markdown text
     * @return list of sections, each with a title and content
     */
    public List<Section> chunk(String markdown) {
        List<Section> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(markdown);

        int lastEnd = 0;
        String currentTitle = "Introduction";
        int currentLevel = 1;

        while (matcher.find()) {
            // Save content from previous section
            String content = markdown.substring(lastEnd, matcher.start()).trim();
            if (!content.isEmpty()) {
                sections.add(new Section(currentTitle, content, currentLevel));
            }

            currentLevel = matcher.group(1).length();
            currentTitle = matcher.group(2).trim();
            lastEnd = matcher.end();
        }

        // Save last section
        String remaining = markdown.substring(lastEnd).trim();
        if (!remaining.isEmpty()) {
            sections.add(new Section(currentTitle, remaining, currentLevel));
        }

        return sections;
    }
}
