package com.travelagent.travelagent.infrastructure.persistence.auth;

import com.travelagent.travelagent.domain.auth.model.AdminUser;
import com.travelagent.travelagent.application.auth.port.out.AdminUserRepository;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper extends AdminUserRepository {

    AdminUser findById(long id);

    AdminUser findByUsername(String username);

    long count();

    int insert(AdminUser adminUser);

    int update(AdminUser adminUser);

    List<AdminUser> findAll();
}
