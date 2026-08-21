package com.travelagent.travelagent.application.auth.port.out;

import com.travelagent.travelagent.domain.auth.model.WxUser;
import java.util.List;

public interface WxUserRepository {

    WxUser findById(long id);

    WxUser findByOpenId(String openId);

    List<WxUser> searchByKeywordPage(String keyword, int offset, int size);

    long countByKeyword(String keyword);

    long count();

    int insert(WxUser wxUser);

    int update(WxUser wxUser);
}
