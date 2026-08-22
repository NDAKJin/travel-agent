package com.travelagent.travelagent.application.rag.ingestion;

interface RagIngestionNode {
    String stage();

    void execute(RagIngestionContext context) throws Exception;
}
