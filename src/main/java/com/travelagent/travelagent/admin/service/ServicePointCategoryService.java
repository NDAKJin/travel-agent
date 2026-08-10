package com.travelagent.travelagent.admin.service;

import com.travelagent.travelagent.admin.mapper.ServicePointCategoryMapper;
import com.travelagent.travelagent.admin.model.ServicePointCategory;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service @RequiredArgsConstructor
public class ServicePointCategoryService {
  private final ServicePointCategoryMapper mapper;
  public List<String> list() { return mapper.findAll().stream().map(ServicePointCategory::getName).toList(); }
  public String create(String name) {
    if (!StringUtils.hasText(name)) throw new IllegalArgumentException("类型名称不能为空");
    String value = name.trim();
    if (list().stream().anyMatch(value::equals)) return value;
    ServicePointCategory category = new ServicePointCategory(); category.setName(value); category.setCreatedAt(Instant.now()); mapper.insert(category); return value;
  }
}
