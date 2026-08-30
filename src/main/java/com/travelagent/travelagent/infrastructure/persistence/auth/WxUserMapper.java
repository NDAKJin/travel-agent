package com.travelagent.travelagent.infrastructure.persistence.auth;

import com.travelagent.travelagent.domain.auth.model.WxUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WxUserMapper {

    WxUser findById(long id);

    WxUser findByOpenId(String openId);
    WxUser findByEmail(String email);
    WxUser findByPhone(String phone);

    List<WxUser> searchByKeywordPage(@Param("keyword") String keyword,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    long countByKeyword(@Param("keyword") String keyword);

    long count();

    int insert(WxUser wxUser);

    int update(WxUser wxUser);
}
