package com.travelagent.travelagent.agent.graph;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;

class Neo4jRelationshipPropertiesTest {

    @Test
    void mapsRelationshipPropertiesWithExternalIds() {
        assertThatCode(() -> new Neo4jMappingContext().getPersistentEntity(Attraction.class))
                .doesNotThrowAnyException();
    }
}
