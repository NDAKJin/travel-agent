package com.travelagent.travelagent.domain.rag.service;

import java.util.List;
import com.tencent.tcvdbtext.encoder.SparseVectorBm25Encoder;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

/** Tencent Vector Database's Jieba-based Chinese BM25 sparse encoder. */
@Component
public final class LexicalSparseEncoder {
    private final SparseVectorBm25Encoder encoder = SparseVectorBm25Encoder.getBm25Encoder("zh");

    public SparseVector encode(String text) {
        List<Pair<Long, Float>> values = encoder.encodeQueries(List.of(text == null ? "" : text)).getFirst();
        int[] indices = new int[values.size()]; float[] weights = new float[values.size()];
        for (int i = 0; i < values.size(); i++) { indices[i] = Math.toIntExact(values.get(i).getLeft()); weights[i] = values.get(i).getRight(); }
        return new SparseVector(indices, weights);
    }

    public record SparseVector(int[] indices, float[] values) { }
}
