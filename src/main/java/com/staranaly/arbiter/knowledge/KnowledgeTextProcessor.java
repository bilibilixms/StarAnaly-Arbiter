package com.staranaly.arbiter.knowledge;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class KnowledgeTextProcessor {

    public static final int MAX_BYTES = 1024 * 1024;
    private static final int CHUNK_CHARS = 800;
    private static final int MAX_CHUNKS = 2_000;

    public String decode(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new KnowledgeInputException("资料必须非空且不超过 1 MiB。");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new KnowledgeInputException("资料必须使用 UTF-8 编码。");
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (text.isBlank() || text.indexOf('\0') >= 0) {
            throw new KnowledgeInputException("资料为空或包含无效字符。");
        }
        return text;
    }

    public List<KnowledgeChunk> chunks(String text) {
        List<KnowledgeChunk> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int startLine = 0;
        int endLine = 0;
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = lines[index];
            if (line.isBlank()) {
                flush(result, current, startLine, endLine);
                startLine = 0;
                continue;
            }
            if (line.length() > CHUNK_CHARS) {
                flush(result, current, startLine, endLine);
                current.setLength(0);
                startLine = 0;
                for (int offset = 0; offset < line.length(); offset += CHUNK_CHARS) {
                    String part = line.substring(offset, Math.min(offset + CHUNK_CHARS, line.length()));
                    result.add(new KnowledgeChunk(result.size() + 1, lineNumber, lineNumber, part));
                    checkCount(result);
                }
                continue;
            }
            if (!current.isEmpty() && current.length() + line.length() + 1 > CHUNK_CHARS) {
                flush(result, current, startLine, endLine);
                current.setLength(0);
                startLine = 0;
            }
            if (current.isEmpty()) {
                startLine = lineNumber;
            } else {
                current.append('\n');
            }
            current.append(line);
            endLine = lineNumber;
        }
        flush(result, current, startLine, endLine);
        return List.copyOf(result);
    }

    public List<KnowledgeHit> rank(List<KnowledgeHit> candidates, String query, int limit) {
        if (query == null || query.isBlank() || query.length() > 200 || limit < 1 || limit > 5) {
            throw new KnowledgeInputException("检索词必须为 2 到 200 字符，结果数量为 1 到 5。");
        }
        Set<String> tokens = bigrams(query);
        if (tokens.isEmpty()) {
            throw new KnowledgeInputException("检索词至少需要两个有效字符。");
        }
        return candidates.stream()
                .map(hit -> new KnowledgeHit(hit.chunkId(), hit.documentId(), hit.documentName(),
                        hit.startLine(), hit.endLine(), hit.excerpt(), overlap(tokens, bigrams(hit.excerpt()))))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingInt(KnowledgeHit::score).reversed()
                        .thenComparing(KnowledgeHit::documentId).thenComparing(KnowledgeHit::chunkId))
                .limit(limit)
                .toList();
    }

    private static void flush(List<KnowledgeChunk> result, StringBuilder current,
            int startLine, int endLine) {
        if (!current.isEmpty()) {
            result.add(new KnowledgeChunk(result.size() + 1, startLine, endLine, current.toString()));
            checkCount(result);
            current.setLength(0);
        }
    }

    private static void checkCount(List<KnowledgeChunk> result) {
        if (result.size() > MAX_CHUNKS) {
            throw new KnowledgeInputException("资料分段不能超过 2,000 段。");
        }
    }

    private static Set<String> bigrams(String value) {
        int[] normalized = value.codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toLowerCase)
                .toArray();
        Set<String> result = new HashSet<>();
        for (int index = 0; index + 1 < normalized.length; index++) {
            result.add(new String(normalized, index, 2));
        }
        return result;
    }

    private static int overlap(Set<String> query, Set<String> candidate) {
        int count = 0;
        for (String token : query) {
            if (candidate.contains(token)) {
                count++;
            }
        }
        return count;
    }
}
