package com.staranaly.arbiter.share;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class PosterJobServiceTest {
    @Test
    void rejectsPaidGenerationBeforeSubmittingWhenDailyImageLimitIsReached() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TaskRepository tasks = mock(TaskRepository.class);
        PosterGenerationService generator = mock(PosterGenerationService.class);
        when(generator.configured()).thenReturn(true);
        when(tasks.existsById(3L)).thenReturn(true);
        doReturn(List.of(40)).when(jdbc).query(startsWith("SELECT image_count"), any(org.springframework.jdbc.core.RowMapper.class), any());
        PosterJobService service = new PosterJobService(jdbc, new ObjectMapper(), tasks, generator, 40);
        try {
            assertThatThrownBy(() -> service.submit(3, "20e902fa-f132-4e03-bedd-14f40f186af7",
                    "活动海报", "", 1, null, List.of(), "", ""))
                    .isInstanceOf(XiaohongshuInputException.class).hasMessageContaining("额度不足");
        } finally { service.shutdown(); }
    }
}
