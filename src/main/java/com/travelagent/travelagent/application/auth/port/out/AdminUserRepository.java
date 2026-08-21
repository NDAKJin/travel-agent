package com.travelagent.travelagent.application.auth.port.out;

import com.travelagent.travelagent.domain.auth.model.AdminUser;
import java.util.List;

public interface AdminUserRepository {

    AdminUser findById(long id);

    AdminUser findByUsername(String username);

    long count();

    int insert(AdminUser adminUser);

    int update(AdminUser adminUser);

    List<AdminUser> findAll();
}
