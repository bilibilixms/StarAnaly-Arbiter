package com.staranaly.arbiter.model;

import java.util.List;

public interface ModelGateway {

    String complete(List<ModelMessage> messages);
}
