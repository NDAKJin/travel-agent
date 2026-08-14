package com.travelagent.travelagent.agent.graph;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("City")
@Getter
@Setter
@NoArgsConstructor
public class City {

    @Id
    private String name;
    private String country;
    private String description;

    @Relationship(type = "HAS_ATTRACTION")
    private Set<Attraction> attractions = new HashSet<>();
}
