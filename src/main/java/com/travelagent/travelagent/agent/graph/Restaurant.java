package com.travelagent.travelagent.agent.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("Restaurant")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    @Id
    private String id;
    private String name;
    private String cuisine;
    private String priceRange;
    private Double rating;

    @Relationship(type = "LOCATED_IN")
    private City city;
}
