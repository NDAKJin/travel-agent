package com.travelagent.travelagent.infrastructure.planning.port;

/** 规划领域调用专家能力的端口。具体专家由基础设施适配器提供。 */
public interface RouteExpertGateway {

    String execute(String expert, String task);
}
