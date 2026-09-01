package com.travelagent.travelagent.domain.rag.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import org.springframework.stereotype.Component;

/** Chinese lexical BM25 encoder for Qdrant sparse vectors. Qdrant applies IDF. */
@Component
public final class LexicalSparseEncoder {
    private static final double K1 = 1.2d;
    private static final double B = 0.75d;
    private static final int DIMENSIONS = 1 << 24;
    private static final Pattern TOKEN = Pattern.compile("[\\u4e00-\\u9fff]+|[A-Za-z]+|\\d+(?:[./-]\\d+)*");
    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    public SparseVector encode(String text) { return encode(text, 1L, Map.of(), 1d); }

    /** BM25 TF saturation and length normalization; IDF is applied by Qdrant. */
    public SparseVector encode(String text, long corpusSize, Map<Integer, Long> documentFrequency,
            double averageDocumentLength) {
        List<String> tokens = tokenize(text);
        Map<Integer, Integer> termFrequency = new LinkedHashMap<>();
        for (String token : tokens) termFrequency.merge(tokenIndex(token), 1, Integer::sum);
        double length = tokens.size();
        Map<Integer, Float> weights = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : termFrequency.entrySet()) {
            double norm = entry.getValue() + K1 * (1d - B + B * length / Math.max(averageDocumentLength, 1d));
            weights.put(entry.getKey(), (float) (entry.getValue() * (K1 + 1d) / norm));
        }
        int[] indices = new int[weights.size()];
        float[] values = new float[weights.size()];
        int i = 0;
        for (Map.Entry<Integer, Float> entry : weights.entrySet()) {
            indices[i] = entry.getKey(); values[i] = entry.getValue(); i++;
        }
        return new SparseVector(indices, values);
    }

    public List<String> tokenize(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.codePoints().allMatch(this::isChinese)) {
                for (SegToken segment : segmenter.process(token, JiebaSegmenter.SegMode.INDEX)) {
                    if (!segment.word.isBlank()) result.add(segment.word);
                }
            } else result.add(token);
        }
        return result;
    }

    private boolean isChinese(int codePoint) { return codePoint >= 0x4e00 && codePoint <= 0x9fff; }

    private int tokenIndex(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
            return (value & 0x7fffffff) % DIMENSIONS;
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record SparseVector(int[] indices, float[] values) { }
}
