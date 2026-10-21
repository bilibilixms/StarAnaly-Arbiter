package com.staranaly.arbiter.csv;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();

    @Test
    void parsesQuotedCommasAndEscapedQuotes() {
        CsvData data = parser.parse("name,amount\r\n\"A,B\",2\r\n\"C\"\"D\",3\r\n"
                .getBytes(StandardCharsets.UTF_8));

        assertThat(data.headers()).containsExactly("name", "amount");
        assertThat(data.rows()).containsExactly(
                java.util.List.of("A,B", "2"), java.util.List.of("C\"D", "3"));
    }

    @Test
    void rejectsBadEncodingAndUnevenRows() {
        assertThatThrownBy(() -> parser.parse(new byte[] {(byte) 0xC3, (byte) 0x28}))
                .isInstanceOf(CsvInputException.class).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> parser.parse("a,b\n1\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CsvInputException.class).hasMessageContaining("列数不一致");
    }
}
