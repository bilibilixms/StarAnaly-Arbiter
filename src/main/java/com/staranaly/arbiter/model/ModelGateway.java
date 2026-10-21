package com.staranaly.arbiter.model;

import java.util.List;
import java.time.Duration;

public interface ModelGateway {

    ModelReply next(List<ModelConversationMessage> messages, boolean allowTools, Duration timeout);
}
