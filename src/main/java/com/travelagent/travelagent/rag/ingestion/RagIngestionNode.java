package com.travelagent.travelagent.rag.ingestion;

interface RagIngestionNode {
    String stage();

    void execute(RagIngestionContext context) throws Exception;
}
