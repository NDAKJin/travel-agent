package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.admin.dto.AdminScenicSpotResponse;
import com.travelagent.travelagent.agent.model.ScenicSpotKnowledge;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class ScenicSpotKnowledgeGraphService {

    private static final String VECTOR_INDEX = "scenic_spot_embedding";
    private static final String SHORTEST_PATH_QUERY = """
            MATCH (from:ScenicSpot {id: $fromId}), (to:ScenicSpot {id: $toId})
            MATCH path = shortestPath((from)-[:CONNECTED_TO*..10]-(to))
            RETURN [node IN nodes(path) | {id: node.id, name: node.name}] AS spots,
                   length(path) AS hopCount,
                   reduce(distanceMeters = 0.0, relationship IN relationships(path) |
                       distanceMeters + coalesce(relationship.distanceMeters, 0.0)) AS distanceMeters
            """;

    private final Driver driver;
    private final EmbeddingModel embeddingModel;

    public ScenicSpotKnowledgeGraphService(Driver driver, EmbeddingModel embeddingModel) {
        this.driver = driver;
        this.embeddingModel = embeddingModel;
    }

    public void upsert(AdminScenicSpotResponse spot) {
        float[] embedding = embeddingModel.embed(knowledgeText(spot.name(), spot.city(), spot.description()));
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("CREATE CONSTRAINT scenic_spot_id IF NOT EXISTS FOR (s:ScenicSpot) REQUIRE s.id IS UNIQUE");
                tx.run("CREATE CONSTRAINT city_name IF NOT EXISTS FOR (c:City) REQUIRE c.name IS UNIQUE");
                tx.run("CREATE VECTOR INDEX " + VECTOR_INDEX + " IF NOT EXISTS FOR (s:ScenicSpot) ON (s.embedding) "
                        + "OPTIONS {indexConfig: {`vector.dimensions`: " + embedding.length
                        + ", `vector.similarity_function`: 'cosine'}}");
                tx.run("MERGE (s:ScenicSpot {id: $id}) "
                                + "SET s.name = $name, s.description = $description, s.longitude = $longitude, "
                                + "s.latitude = $latitude, s.embedding = $embedding, s.updatedAt = $updatedAt",
                        Map.of("id", spot.id(), "name", spot.name(), "description", spot.description(),
                                "longitude", spot.longitude(), "latitude", spot.latitude(),
                                "embedding", toList(embedding), "updatedAt", spot.updatedAt().toString()));
                tx.run("MATCH (s:ScenicSpot {id: $id})-[r:LOCATED_IN]->() DELETE r "
                                + "WITH s MERGE (c:City {name: $city}) MERGE (s)-[:LOCATED_IN]->(c)",
                        Map.of("id", spot.id(), "city", spot.city()));
                tx.run("MATCH (source:ScenicSpot {id: $id})-[r:NEARBY|CONNECTED_TO]->() DELETE r", Map.of("id", spot.id()));
                tx.run("MATCH (source:ScenicSpot {id: $id}), (other:ScenicSpot) "
                                + "WHERE other.id <> source.id "
                                + "WITH source, other, point.distance(point({longitude: source.longitude, latitude: source.latitude}), "
                                + "point({longitude: other.longitude, latitude: other.latitude})) AS distance "
                                + "ORDER BY distance LIMIT 5 "
                                + "MERGE (source)-[r:CONNECTED_TO]->(other) SET r.distanceMeters = distance",
                        Map.of("id", spot.id()));
                return null;
            });
        }
    }

    public void delete(String id) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run("MATCH (s:ScenicSpot {id: $id}) DETACH DELETE s", Map.of("id", id)));
        }
    }

    public void reindex(List<AdminScenicSpotResponse> spots) {
        for (AdminScenicSpotResponse spot : spots) upsert(spot);
    }

    public List<ScenicSpotKnowledge> search(String query, int limit) {
        float[] embedding = embeddingModel.embed(query);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> tx.run("CALL db.index.vector.queryNodes('" + VECTOR_INDEX
                            + "', $limit, $embedding) YIELD node, score "
                            + "OPTIONAL MATCH (node)-[:CONNECTED_TO]->(nearby) "
                            + "OPTIONAL MATCH (node)-[:LOCATED_IN]->(city:City) "
                            + "RETURN node.id AS id, node.name AS name, city.name AS city, node.description AS description, "
                            + "node.longitude AS longitude, node.latitude AS latitude, score, collect(nearby.name) AS nearby "
                            + "ORDER BY score DESC", Map.of("limit", limit, "embedding", toList(embedding)))
                    .list(record -> new ScenicSpotKnowledge(record.get("id").asString(), record.get("name").asString(),
                            record.get("city").isNull() ? "" : record.get("city").asString(), record.get("description").asString(), record.get("longitude").asDouble(),
                            record.get("latitude").asDouble(), record.get("score").asDouble(),
                            record.get("nearby").asList(value -> value.asString()))));
        }
    }

    public Map<String, Object> shortestPath(String fromId, String toId) {
        try (Session session = driver.session()) {
            List<Map<String, Object>> paths = session.executeRead(tx -> tx.run(SHORTEST_PATH_QUERY,
                            Map.of("fromId", fromId, "toId", toId))
                    .list(record -> Map.<String, Object>of(
                            "spots", record.get("spots").asList(value -> value.asMap()),
                            "hopCount", record.get("hopCount").asInt(),
                            "distanceMeters", record.get("distanceMeters").asDouble())));
            return paths.isEmpty() ? Map.of("found", false) : paths.getFirst();
        }
    }

    static String knowledgeText(String name, String city, String description) {
        return name + "\n" + city + "\n" + description;
    }

    static String shortestPathQuery() { return SHORTEST_PATH_QUERY; }

    private List<Float> toList(float[] values) {
        List<Float> result = new java.util.ArrayList<>(values.length);
        for (float value : values) result.add(value);
        return result;
    }
}
