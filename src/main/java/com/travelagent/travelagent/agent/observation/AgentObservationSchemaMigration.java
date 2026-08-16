package com.travelagent.travelagent.agent.observation;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
final class AgentObservationSchemaMigration implements ApplicationRunner {
    private final DataSource dataSource;

    AgentObservationSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null,
                     "agent_observation_log", "next_decision")) {
            if (columns.next()) return;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate(
                    "ALTER TABLE agent_observation_log ADD COLUMN next_decision VARCHAR(64)");
        }
    }
}
