package com.travelagent.travelagent.agent.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Interest")
@Getter
@Setter
@NoArgsConstructor
public class Interest {

    @Id
    private String name;
}
