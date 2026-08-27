package io.github.luigidemasi.camelkit.generator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Edits direct JSONC object members without reformatting user-owned content. */
final class JsoncObjectEditor {

    private static final ObjectMapper JSON = new ObjectMapper();

    private String content;

    JsoncObjectEditor(String content) {
        this.content = content;
        root();
    }

    String content() {
        return content;
    }

    void replaceRootMemberValue(String name, JsonNode value) {
        Member member = root().lastMember(name);
        if (member == null) {
            throw new IllegalArgumentException("Missing JSONC member: " + name);
        }
        replace(member.valueStart(), member.valueEnd(), render(value, newline(), ""));
    }

    void upsertRootMember(String name, JsonNode value) {
        Member member = root().lastMember(name);
        if (member == null) {
            appendRootMember(name, value);
        } else {
            replace(member.valueStart(), member.valueEnd(), render(value, newline(), ""));
        }
    }

    void appendRootMember(String name, JsonNode value) {
        ObjectNode member = JSON.createObjectNode();
        member.set(name, value);
        appendMembers(root(), member);
    }

    void removeObjectMembers(String objectName, Iterator<String> names) {
        Set<String> removedNames = new HashSet<>();
        names.forEachRemaining(removedNames::add);
        if (removedNames.isEmpty()) {
            return;
        }

        Member objectMember = root().lastMember(objectName);
        if (objectMember == null || content.charAt(objectMember.valueStart()) != '{') {
            return;
        }
        removeMembers(parseObject(objectMember.valueStart()), removedNames);
    }

    void appendObjectMembers(String objectName, ObjectNode members) {
        Member objectMember = root().lastMember(objectName);
        if (objectMember == null || content.charAt(objectMember.valueStart()) != '{') {
            throw new IllegalArgumentException("JSONC member is not an object: " + objectName);
        }
        appendMembers(parseObject(objectMember.valueStart()), members);
    }

    private void removeMembers(ObjectRange object, Set<String> names) {
        List<Member> removed = object.members().stream()
                .filter(member -> names.contains(member.name()))
                .toList();
        if (removed.isEmpty()) {
            return;
        }

        Set<Member> removedSet = Set.copyOf(removed);
        List<Member> kept = object.members().stream()
                .filter(member -> !removedSet.contains(member))
                .toList();
        String commentIndent = memberIndent(object, leadingIndent(object.open()));
        List<Edit> edits = new ArrayList<>();
        for (Member member : removed) {
            edits.add(new Edit(
                    member.keyStart(), member.valueEnd(),
                    preserveComments(
                            content.substring(member.keyStart(), member.valueEnd()),
                            commentIndent)));
            if (member.comma() >= 0) {
                edits.add(new Edit(member.comma(), member.comma() + 1, ""));
            }
        }

        if (!kept.isEmpty()) {
            Member lastKept = kept.get(kept.size() - 1);
            boolean removedAfter = removed.stream().anyMatch(
                    member -> member.keyStart() > lastKept.keyStart());
            if (removedAfter && lastKept.comma() >= 0) {
                edits.add(new Edit(lastKept.comma(), lastKept.comma() + 1, ""));
            }
        }
        apply(edits);
    }

    private void appendMembers(ObjectRange object, ObjectNode members) {
        if (members.isEmpty()) {
            return;
        }

        String newline = newline();
        String objectIndent = leadingIndent(object.open());
        String memberIndent = memberIndent(object, objectIndent);
        String rendered = renderMembers(members, newline, memberIndent);
        List<Edit> edits = new ArrayList<>();
        boolean commaAtClose = false;

        if (!object.members().isEmpty()) {
            Member last = object.members().get(object.members().size() - 1);
            if (last.comma() < 0) {
                if (last.valueEnd() == object.close()) {
                    commaAtClose = true;
                } else {
                    edits.add(new Edit(last.valueEnd(), last.valueEnd(), ","));
                }
            }
        }

        int lineStart = lineStart(object.close());
        boolean indentedClose = onlyIndent(lineStart, object.close());
        String insertion;
        if (indentedClose) {
            String closeIndent = content.substring(lineStart, object.close());
            String suffix = memberIndent.startsWith(closeIndent)
                    ? memberIndent.substring(closeIndent.length())
                    : "  ";
            insertion = suffix + rendered + newline + closeIndent;
        } else {
            insertion = newline + memberIndent + rendered + newline + objectIndent;
        }
        if (commaAtClose) {
            insertion = "," + insertion;
        }
        edits.add(new Edit(object.close(), object.close(), insertion));
        apply(edits);
    }

    private String renderMembers(ObjectNode members, String newline, String indent) {
        List<String> rendered = new ArrayList<>();
        members.fields().forEachRemaining(entry -> rendered.add(
                quote(entry.getKey()) + ": " + render(entry.getValue(), newline, indent)));
        return String.join("," + newline + indent, rendered);
    }

    private String render(JsonNode value, String newline, String indent) {
        try {
            String rendered = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)
                    .replace("\r\n", "\n");
            return rendered.replace("\n", newline + indent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not render JSONC value", e);
        }
    }

    private String quote(String name) {
        try {
            return JSON.writeValueAsString(name);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not render JSONC member name", e);
        }
    }

    private String preserveComments(String removed, String indent) {
        StringBuilder preserved = new StringBuilder();
        int cursor = 0;
        while (cursor < removed.length()) {
            char current = removed.charAt(cursor);
            if (startsComment(removed, cursor, "//")) {
                int end = lineCommentEnd(removed, cursor);
                preserved.append(newline()).append(indent).append(removed, cursor, end).append(newline());
                cursor = end;
            } else if (startsComment(removed, cursor, "/*")) {
                int end = removed.indexOf("*/", cursor + 2) + 2;
                preserved.append(newline()).append(indent).append(removed, cursor, end);
                cursor = end;
            } else if (current == '"') {
                cursor = scanString(removed, cursor);
            } else {
                cursor++;
            }
        }
        return preserved.toString();
    }

    private ObjectRange root() {
        int start = skipTrivia(0);
        if (start >= content.length() || content.charAt(start) != '{') {
            throw new IllegalArgumentException("JSONC root must be an object");
        }
        return parseObject(start);
    }

    private ObjectRange parseObject(int open) {
        List<Member> members = new ArrayList<>();
        int cursor = open + 1;
        while (true) {
            cursor = skipTrivia(cursor);
            if (content.charAt(cursor) == '}') {
                return new ObjectRange(open, cursor, members);
            }

            int keyStart = cursor;
            int keyEnd = scanString(content, keyStart);
            String name = unquote(content.substring(keyStart, keyEnd));
            cursor = skipTrivia(keyEnd);
            if (content.charAt(cursor) != ':') {
                throw new IllegalArgumentException("Missing JSONC member colon");
            }
            int valueStart = skipTrivia(cursor + 1);
            int valueEnd = scanValue(valueStart);
            cursor = skipTrivia(valueEnd);
            int comma = -1;
            if (content.charAt(cursor) == ',') {
                comma = cursor++;
            }
            members.add(new Member(name, keyStart, valueStart, valueEnd, comma));
            if (comma < 0) {
                cursor = skipTrivia(cursor);
                if (content.charAt(cursor) != '}') {
                    throw new IllegalArgumentException("Missing JSONC member comma");
                }
            }
        }
    }

    private int scanValue(int start) {
        char first = content.charAt(start);
        if (first == '"') {
            return scanString(content, start);
        }
        if (first == '{' || first == '[') {
            Deque<Character> closes = new ArrayDeque<>();
            closes.push(first == '{' ? '}' : ']');
            int cursor = start + 1;
            while (!closes.isEmpty()) {
                char current = content.charAt(cursor);
                if (current == '"') {
                    cursor = scanString(content, cursor);
                } else if (startsComment(content, cursor, "//")) {
                    cursor = lineCommentEnd(cursor);
                } else if (startsComment(content, cursor, "/*")) {
                    cursor = blockCommentEnd(cursor);
                } else {
                    if (current == '{' || current == '[') {
                        closes.push(current == '{' ? '}' : ']');
                    } else if (current == closes.peek()) {
                        closes.pop();
                    }
                    cursor++;
                }
            }
            return cursor;
        }

        int cursor = start;
        while (cursor < content.length()) {
            char current = content.charAt(cursor);
            if (Character.isWhitespace(current) || current == ',' || current == '}' || current == ']'
                    || startsComment(content, cursor, "//") || startsComment(content, cursor, "/*")) {
                return cursor;
            }
            cursor++;
        }
        return cursor;
    }

    private int skipTrivia(int start) {
        int cursor = start;
        while (cursor < content.length()) {
            if (Character.isWhitespace(content.charAt(cursor))) {
                cursor++;
            } else if (startsComment(content, cursor, "//")) {
                cursor = lineCommentEnd(cursor);
            } else if (startsComment(content, cursor, "/*")) {
                cursor = blockCommentEnd(cursor);
            } else {
                break;
            }
        }
        return cursor;
    }

    private int lineCommentEnd(int start) {
        return lineCommentEnd(content, start);
    }

    private int lineCommentEnd(String value, int start) {
        int lineFeed = value.indexOf('\n', start + 2);
        int carriageReturn = value.indexOf('\r', start + 2);
        if (lineFeed < 0) {
            return carriageReturn < 0 ? value.length() : carriageReturn;
        }
        return carriageReturn < 0 ? lineFeed : Math.min(lineFeed, carriageReturn);
    }

    private int blockCommentEnd(int start) {
        return content.indexOf("*/", start + 2) + 2;
    }

    private static int scanString(String value, int start) {
        int cursor = start + 1;
        while (cursor < value.length()) {
            char current = value.charAt(cursor++);
            if (current == '\\') {
                cursor++;
            } else if (current == '"') {
                return cursor;
            }
        }
        throw new IllegalArgumentException("Unterminated JSONC string");
    }

    private String unquote(String value) {
        try {
            return JSON.readValue(value, String.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSONC member name", e);
        }
    }

    private String newline() {
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (current == '\r') {
                return i + 1 < content.length() && content.charAt(i + 1) == '\n' ? "\r\n" : "\r";
            }
            if (current == '\n') {
                return "\n";
            }
        }
        return System.lineSeparator();
    }

    private String memberIndent(ObjectRange object, String objectIndent) {
        if (!object.members().isEmpty()) {
            Member first = object.members().get(0);
            int start = lineStart(first.keyStart());
            if (onlyIndent(start, first.keyStart())) {
                return content.substring(start, first.keyStart());
            }
        }
        return objectIndent + "  ";
    }

    private String leadingIndent(int position) {
        int start = lineStart(position);
        int end = start;
        while (end < content.length() && (content.charAt(end) == ' ' || content.charAt(end) == '\t')) {
            end++;
        }
        return content.substring(start, end);
    }

    private int lineStart(int position) {
        int before = Math.max(0, position - 1);
        int start = Math.max(content.lastIndexOf('\n', before), content.lastIndexOf('\r', before));
        return start < 0 ? 0 : start + 1;
    }

    private boolean onlyIndent(int start, int end) {
        for (int i = start; i < end; i++) {
            char current = content.charAt(i);
            if (current != ' ' && current != '\t' && current != '\r') {
                return false;
            }
        }
        return true;
    }

    private void replace(int start, int end, String replacement) {
        content = content.substring(0, start) + replacement + content.substring(end);
    }

    private void apply(List<Edit> edits) {
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        for (Edit edit : edits) {
            replace(edit.start(), edit.end(), edit.replacement());
        }
    }

    private static boolean startsComment(String value, int position, String marker) {
        return position + 1 < value.length() && value.startsWith(marker, position);
    }

    private record ObjectRange(int open, int close, List<Member> members) {
        private Member lastMember(String name) {
            for (int i = members.size() - 1; i >= 0; i--) {
                if (name.equals(members.get(i).name())) {
                    return members.get(i);
                }
            }
            return null;
        }
    }

    private record Member(
            String name,
            int keyStart,
            int valueStart,
            int valueEnd,
            int comma) {
    }

    private record Edit(int start, int end, String replacement) {
    }
}
