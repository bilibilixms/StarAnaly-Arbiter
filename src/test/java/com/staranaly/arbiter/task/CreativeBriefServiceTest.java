package com.staranaly.arbiter.task;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.model.ModelGateway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreativeBriefServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final CreativeBriefService service = new CreativeBriefService(mock(JdbcTemplate.class), tasks,
            mock(TaskImageService.class), mock(ModelGateway.class), new ObjectMapper());

    @Test
    void keepsOnlyKnownSourceLabelsFromModel() {
        CreativeBrief brief = service.parseGenerated("""
                {"objective":"宣传新品","audience":"新用户","coreMessage":"易上手","tone":"自然",
                 "facts":[{"text":"有三种颜色","source":"文档：产品说明.pdf"},
                          {"text":"售价99元","source":"不存在的文件"}],
                 "unknowns":["具体售价"],"directions":[{"name":"体验","angle":"介绍上手过程","headlineIdea":"第一天使用"}]}
                """, Set.of("文档：产品说明.pdf"));

        assertThat(brief.facts()).containsExactly(
                new CreativeBrief.Fact("有三种颜色", "文档：产品说明.pdf"),
                new CreativeBrief.Fact("售价99元", "待核实"));
    }

    @Test
    void cannotConfirmUnverifiedFact() {
        Task task = new Task("宣传新品", "文字要求");
        when(tasks.findById(1L)).thenReturn(java.util.Optional.of(task));
        CreativeBrief.Fact unverified = new CreativeBrief.Fact("价格99元", "待核实");
        SaveCreativeBriefRequest request = new SaveCreativeBriefRequest("宣传新品", "新用户", "易上手",
                "自然", List.of(unverified), List.of("售价"), List.of(
                new CreativeBrief.Direction("体验", "说明过程", "第一次使用")), true);

        assertThatThrownBy(() -> service.save(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("待核实");
    }
}
