package com.travelagent.travelagent.infrastructure.persistence.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    @Override public void run(ApplicationArguments args) {
        addColumn("phone", "VARCHAR(32) NULL"); addColumn("email", "VARCHAR(255) NULL");
        try { jdbcTemplate.execute("CREATE UNIQUE INDEX uk_wx_user_phone ON wx_user(phone)"); } catch (RuntimeException ignored) { }
        try { jdbcTemplate.execute("CREATE UNIQUE INDEX uk_wx_user_email ON wx_user(email)"); } catch (RuntimeException ignored) { }
    }
    private void addColumn(String name, String definition) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wx_user' AND column_name=?", Integer.class, name);
        if (count != null && count == 0) jdbcTemplate.execute("ALTER TABLE wx_user ADD COLUMN " + name + " " + definition);
    }
}
