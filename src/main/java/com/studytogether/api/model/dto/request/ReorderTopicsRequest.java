package com.studytogether.api.model.dto.request;

import java.util.List;

public record ReorderTopicsRequest(List<Long> topicIds) {}
