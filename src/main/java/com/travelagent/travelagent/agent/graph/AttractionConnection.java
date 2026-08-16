package com.travelagent.travelagent.agent.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class AttractionConnection {

    @Id
    @GeneratedValue
    private String id;
    private Double distanceMeters;
    private Integer durationMinutes;
    private String transport;

    @TargetNode
    private Attraction attraction;
}
