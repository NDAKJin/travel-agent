package com.travelagent.travelagent.domain.rag.service;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Lightweight Jieba TF sparse encoder. Qdrant applies IDF to BM25-style scoring. */
@Component
public final class LexicalSparseEncoder {
    private static final int SPARSE_DIMENSIONS = 1 << 20;
    private static final ThreadLocal<JiebaSegmenter> SEGMENTER = ThreadLocal.withInitial(JiebaSegmenter::new);

    public SparseVector encode(String text) {
        if (text == null || text.isBlank()) return empty();
        Map<Integer, Integer> counts = new HashMap<>();
        List<SegToken> tokens = SEGMENTER.get().process(text, JiebaSegmenter.SegMode.INDEX);
        for (SegToken token : tokens) {
            String word = token.word == null ? "" : token.word.trim();
            if (word.isEmpty() || isPunctuation(word)) continue;
            counts.merge(hashIndex(word), 1, Integer::sum);
        }
        if (counts.isEmpty()) return empty();
        int[] indices = counts.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        float[] values = new float[indices.length];
        for (int i = 0; i < indices.length; i++) {
            values[i] = (float) Math.sqrt(counts.get(indices[i]));
        }
        return new SparseVector(indices, values);
    }

    private int hashIndex(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= item & 0xffL;
            hash *= 0x100000001b3L;
        }
        return (int) ((hash & Long.MAX_VALUE) % SPARSE_DIMENSIONS);
    }

    private boolean isPunctuation(String value) {
        return value.codePoints().allMatch(codePoint -> !Character.isLetterOrDigit(codePoint));
    }

    private SparseVector empty() { return new SparseVector(new int[0], new float[0]); }

    public record SparseVector(int[] indices, float[] values) { }
}
