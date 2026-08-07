package com.travelagent.travelagent.auth.mapper;

import com.travelagent.travelagent.auth.model.WxUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WxUserMapper {

    WxUser findById(long id);

    WxUser findByOpenId(String openId);

    List<WxUser> searchByKeywordPage(@Param("keyword") String keyword,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    long countByKeyword(@Param("keyword") String keyword);

    long count();

    int insert(WxUser wxUser);

    int update(WxUser wxUser);
}
