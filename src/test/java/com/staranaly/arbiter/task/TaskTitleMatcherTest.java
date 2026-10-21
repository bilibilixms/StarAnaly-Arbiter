package com.staranaly.arbiter.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTitleMatcherTest {

    @Test
    void matchesPartialNameAndOneWrongChineseCharacter() {
        String query = TaskTitleMatcher.normalize("分折销售额");
        assertThat(TaskTitleMatcher.distance("按月分析销售额", query)).isEqualTo(1);
        assertThat(TaskTitleMatcher.distance("按月分析销售额", TaskTitleMatcher.normalize("销售额")))
                .isZero();
    }

    @Test
    void rejectsUnrelatedNamesAndAvoidsFuzzyOneCharacterSearch() {
        assertThat(TaskTitleMatcher.distance("采购计划", TaskTitleMatcher.normalize("分折销售额")))
                .isGreaterThan(TaskTitleMatcher.maxDistance("分折销售额"));
        assertThat(TaskTitleMatcher.distance("销售额", TaskTitleMatcher.normalize("错")))
                .isGreaterThan(TaskTitleMatcher.maxDistance("错"));
    }
}
