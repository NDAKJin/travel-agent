package com.travelagent.travelagent.rag.rerank;

import java.util.List;

public interface RerankService {
    List<RerankResult> rerank(String query, List<RerankCandidate> candidates);
}
