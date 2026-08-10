package com.travelagent.travelagent.admin.mapper;

import com.travelagent.travelagent.admin.model.ServicePointCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServicePointCategoryMapper { List<ServicePointCategory> findAll(); int insert(ServicePointCategory category); }
