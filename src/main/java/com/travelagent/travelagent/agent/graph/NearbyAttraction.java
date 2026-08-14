package com.travelagent.travelagent.agent.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class NearbyAttraction {

    @RelationshipId
    private Long id;
    private Integer walkTime;

    @TargetNode
    private Attraction attraction;
}
