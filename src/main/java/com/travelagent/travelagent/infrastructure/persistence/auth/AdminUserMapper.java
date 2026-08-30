package com.travelagent.travelagent.infrastructure.persistence.auth;

import com.travelagent.travelagent.domain.auth.model.AdminUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper {

    AdminUser findById(long id);

    AdminUser findByUsername(String username);

    long count();

    int insert(AdminUser adminUser);

    int update(AdminUser adminUser);

    List<AdminUser> findAll();
}
