package com.travelagent.travelagent.domain.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LexicalSparseEncoderTest {

    @Test
    void encodesJiebaTokensAsSortedNonEmptySparseVector() {
        LexicalSparseEncoder.SparseVector vector = new LexicalSparseEncoder().encode("杭州西湖景区");

        assertThat(vector.indices()).isNotEmpty().isSorted();
        assertThat(vector.values()).hasSameSizeAs(vector.indices());
        for (float value : vector.values()) {
            assertThat(value).isPositive();
        }
    }

    @Test
    void blankTextProducesEmptyVector() {
        LexicalSparseEncoder.SparseVector vector = new LexicalSparseEncoder().encode("  ");

        assertThat(vector.indices()).isEmpty();
        assertThat(vector.values()).isEmpty();
    }
}
