package com.travelagent.travelagent.agent.graph;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("Attraction")
@Getter
@Setter
@NoArgsConstructor
public class Attraction {

    @Id
    private String id;
    private String name;
    private String description;
    private String category;
    private Set<String> tags = new HashSet<>();
    private Integer popularity;
    private Double latitude;
    private Double longitude;

    @Relationship(type = "LOCATED_IN")
    private City city;
    @Relationship(type = "CONNECTED_TO")
    private Set<AttractionConnection> connections = new HashSet<>();
    @Relationship(type = "NEARBY")
    private Set<NearbyAttraction> nearbyAttractions = new HashSet<>();
    @Relationship(type = "RELATED_TO")
    private Set<Restaurant> relatedRestaurants = new HashSet<>();
    @Relationship(type = "MATCHES_INTEREST")
    private Set<Interest> interests = new HashSet<>();
}
