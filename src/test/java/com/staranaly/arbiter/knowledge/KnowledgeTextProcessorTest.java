package com.staranaly.arbiter.knowledge;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeTextProcessorTest {

    private final KnowledgeTextProcessor processor = new KnowledgeTextProcessor();

    @Test
    void preservesSourceLinesAndRanksRelevantParagraph() {
        String text = processor.decode(("\uFEFF# 退货规则\r\n\r\n"
                + "普通订单自签收日起 14 天内可以申请退货。\r\n"
                + "超过 14 天需人工审核。\r\n\r\n"
                + "其他说明。\r\n").getBytes(StandardCharsets.UTF_8));
        List<KnowledgeChunk> chunks = processor.chunks(text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(1).startLine()).isEqualTo(3);
        assertThat(chunks.get(1).endLine()).isEqualTo(4);
        assertThat(chunks.get(1).content()).contains("14 天", "人工审核");

        List<KnowledgeHit> hits = processor.rank(List.of(
                new KnowledgeHit(1L, 1L, "规则.md", 1, 1, chunks.get(0).content(), 0),
                new KnowledgeHit(2L, 1L, "规则.md", 3, 4, chunks.get(1).content(), 0)),
                "普通订单何时可以申请退货", 2);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunkId()).isEqualTo(2L);
        assertThat(processor.rank(List.of(
                new KnowledgeHit(2L, 1L, "规则.md", 3, 4, chunks.get(1).content(), 0)),
                "火星天气", 5)).isEmpty();
    }

    @Test
    void rejectsMalformedUtf8AndInvalidQueries() {
        assertThatThrownBy(() -> processor.decode(new byte[] {(byte) 0xC3, (byte) 0x28}))
                .isInstanceOf(KnowledgeInputException.class);
        assertThatThrownBy(() -> processor.rank(List.of(), "?", 5))
                .isInstanceOf(KnowledgeInputException.class);
        assertThatThrownBy(() -> processor.rank(List.of(), "退货", 6))
                .isInstanceOf(KnowledgeInputException.class);
    }

    @Test
    void keepsSupplementaryUnicodeCharacterWholeAtChunkBoundary() {
        List<KnowledgeChunk> chunks = processor.chunks("a".repeat(799) + "😀" + "b");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("a".repeat(799));
        assertThat(chunks.get(1).content()).isEqualTo("😀b");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.startLine()).isEqualTo(1));
    }

    @Test
    void rejectsNulOversizedFileAndTooManyChunks() {
        assertThatThrownBy(() -> processor.decode("说明\0正文".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(KnowledgeInputException.class);
        assertThatThrownBy(() -> processor.decode(new byte[KnowledgeTextProcessor.MAX_BYTES + 1]))
                .isInstanceOf(KnowledgeInputException.class);
        assertThatThrownBy(() -> processor.chunks("资料\n\n".repeat(2_001)))
                .isInstanceOf(KnowledgeInputException.class)
                .hasMessageContaining("2,000");
    }
}
