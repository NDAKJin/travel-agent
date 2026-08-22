package com.travelagent.travelagent.application.rag.port.out;

import com.travelagent.travelagent.application.rag.model.RerankCandidate;
import com.travelagent.travelagent.application.rag.model.RerankResult;
import java.util.List;

public interface RerankService {
    List<RerankResult> rerank(String query, List<RerankCandidate> candidates);
}
