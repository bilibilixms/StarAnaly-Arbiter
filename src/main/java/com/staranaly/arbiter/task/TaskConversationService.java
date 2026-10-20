package com.staranaly.arbiter.task;

import java.util.List;

import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelMessage;
import com.staranaly.arbiter.model.ModelServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskConversationService {

    private final TaskConversationState state;
    private final ModelGateway modelGateway;

    public TaskConversationService(TaskConversationState state, ModelGateway modelGateway) {
        this.state = state;
        this.modelGateway = modelGateway;
    }

    public TaskMessageResponse send(Long taskId, String content) {
        String normalizedContent = content.trim();
        List<ModelMessage> context = state.begin(taskId, normalizedContent);
        String answer;
        try {
            answer = modelGateway.complete(context);
        } catch (ModelServiceException exception) {
            state.fail(taskId, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            ModelServiceException failure = new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型服务调用失败。");
            state.fail(taskId, failure.getMessage());
            throw failure;
        }
        return state.succeed(taskId, normalizedContent, answer);
    }

    public List<TaskMessageResponse> list(Long taskId) {
        return state.list(taskId);
    }
}
