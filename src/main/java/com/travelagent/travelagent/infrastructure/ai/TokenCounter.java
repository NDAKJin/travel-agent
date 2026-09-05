package com.travelagent.travelagent.infrastructure.ai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import org.springframework.stereotype.Component;

@Component
public class TokenCounter {
    private final Encoding encoding;

    public TokenCounter() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE);
    }

    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : encoding.encodeOrdinary(text).size();
    }
}
