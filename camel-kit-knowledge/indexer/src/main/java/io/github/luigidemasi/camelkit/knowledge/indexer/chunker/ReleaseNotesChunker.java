package io.github.luigidemasi.camelkit.knowledge.indexer.chunker;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Specialized chunker for release notes documents.
 * Splits JIRA issue rows from markdown tables into individual chunks,
 * while falling back to SectionChunker for non-table sections.
 *
 * Recognizes JIRA issue patterns: CEQ-*, CSB-*, RHBAC-*, CAMEL-*, ENTESB-*
 * in markdown table rows like:
 * <pre>
 * | [CSB-8351](https://issues.redhat.com/browse/CSB-8351) | description |
 * </pre>
 */
public class ReleaseNotesChunker {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{2,5})\\s+(.+)$", Pattern.MULTILINE);

    private static final Pattern JIRA_ROW_PATTERN = Pattern.compile(
            "^\\|\\s*(?:\\d+\\s*\\|)?\\s*\\[(" +
                    "(?:CEQ|CSB|RHBAC|CAMEL|ENTESB)-\\d+" +
                    ")\\]\\(([^)]+)\\)\\s*\\|\\s*(.+?)\\s*\\|\\s*$",
            Pattern.MULTILINE);

    private static final Pattern UPSTREAM_REF_PATTERN = Pattern.compile(
            "\\[?(CAMEL-\\d+)\\]?");

    private final SectionChunker sectionChunker = new SectionChunker();

    /**
     * A resolved JIRA issue from a release notes table.
     *
     * @param jiraIds     all JIRA IDs (primary + upstream references)
     * @param description issue description from the table
     * @param url         JIRA URL
     * @param sectionTitle heading context (e.g., "4.14.4 fixed issues")
     */
    public record ResolvedIssue(
            List<String> jiraIds,
            String description,
            String url,
            String sectionTitle
    ) {}

    /**
     * Result of chunking release notes: individual JIRA issues + remaining sections.
     */
    public record ChunkResult(
            List<ResolvedIssue> issues,
            List<SectionChunker.Section> otherSections
    ) {}

    /**
     * Chunk release notes markdown into individual JIRA issues and other sections.
     * Table rows with JIRA IDs become ResolvedIssue objects.
     * Everything else (prose, known issues, features) is chunked by SectionChunker.
     */
    public ChunkResult chunk(String markdown) {
        List<ResolvedIssue> issues = new ArrayList<>();
        StringBuilder nonTableContent = new StringBuilder();

        String[] lines = markdown.split("\n");
        String currentHeading = "Introduction";
        boolean inTable = false;

        for (String line : lines) {
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                currentHeading = headingMatcher.group(2).trim();
                inTable = false;
                nonTableContent.append(line).append("\n");
                continue;
            }

            Matcher jiraRowMatcher = JIRA_ROW_PATTERN.matcher(line);
            if (jiraRowMatcher.matches()) {
                inTable = true;
                String primaryId = jiraRowMatcher.group(1);
                String url = jiraRowMatcher.group(2);
                String description = jiraRowMatcher.group(3).trim();

                List<String> allIds = new ArrayList<>();
                allIds.add(primaryId);

                Matcher upstreamMatcher = UPSTREAM_REF_PATTERN.matcher(description);
                while (upstreamMatcher.find()) {
                    String upstreamId = upstreamMatcher.group(1);
                    if (!upstreamId.equals(primaryId) && !allIds.contains(upstreamId)) {
                        allIds.add(upstreamId);
                    }
                }

                issues.add(new ResolvedIssue(allIds, description, url, currentHeading));
                continue;
            }

            if (inTable && line.trim().startsWith("|")) {
                // Table header or separator row — skip
                continue;
            }

            inTable = false;
            nonTableContent.append(line).append("\n");
        }

        List<SectionChunker.Section> otherSections =
                sectionChunker.chunk(nonTableContent.toString());

        return new ChunkResult(issues, otherSections);
    }
}
