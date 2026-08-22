package com.travelagent.travelagent.application.rag.port.in;

/** RAG 查询用例，Agent 不依赖 Qdrant 或具体重排实现。 */
public interface KnowledgeRetriever {

    String enrich(String task);
}
