package com.staranaly.arbiter.knowledge;

import java.util.List;

public record KnowledgeSearchResponse(String query, List<KnowledgeHit> hits) {
}
