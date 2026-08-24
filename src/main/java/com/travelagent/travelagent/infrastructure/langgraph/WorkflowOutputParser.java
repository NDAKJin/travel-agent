package com.travelagent.travelagent.infrastructure.langgraph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.domain.planning.service.RequirementPolicy;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Parses and validates the small JSON contracts exchanged by workflow nodes. */
final class WorkflowOutputParser {
    private static final Set<String> EXPERTS = Set.of("KNOWLEDGE", "ROUTE", "BUDGET");
    private static final List<String> REQUIREMENT_FIELDS =
            List.of("origin", "destination", "date", "days", "people", "budget", "interests", "constraints");
    private static final List<String> REQUIRED_FIELDS =
            List.of("origin", "destination", "date", "days", "people", "budget");

    private WorkflowOutputParser() {
    }

    static String intent(String output) {
        JSONObject json = parseJson(output);
        return json != null && "route".equalsIgnoreCase(json.getString("intent")) ? "route" : "normal";
    }

    static RequirementDecision requirements(String output) {
        JSONObject json = parseJson(output);
        if (json != null && validRequirements(json)) {
            return new RequirementDecision(
                    RequirementPolicy.isConfirmed(json.getString("status")),
                    JSON.toJSONString(json, JSONWriter.Feature.WriteMapNullValue));
        }
        JSONObject fallback = new JSONObject();
        fallback.put("status", "QUESTION");
        fallback.put("question", "请补充路线规划所需的信息。");
        fallback.put("requirements", emptyRequirements());
        return new RequirementDecision(false, JSON.toJSONString(fallback, JSONWriter.Feature.WriteMapNullValue));
    }

    static ReviewDecision review(String output) {
        JSONObject json = parseJson(output);
        if (json != null && validReview(json)) {
            return new ReviewDecision(
                    "approved".equalsIgnoreCase(json.getString("status")),
                    JSON.toJSONString(json, JSONWriter.Feature.WriteMapNullValue));
        }
        JSONObject fallback = new JSONObject();
        fallback.put("status", "REVISE");
        fallback.put("issues", List.of("审核结果格式无效，请重新审核。"));
        return new ReviewDecision(false, JSON.toJSONString(fallback));
    }

    static PlannerDecision planner(String output) {
        JSONObject json = parseJson(output);
        if (json == null) return new PlannerDecision("routeReviewer", output, "", false);
        if ("DELEGATE".equalsIgnoreCase(json.getString("action"))) {
            JSONArray tasks = json.getJSONArray("tasks");
            JSONArray validTasks = validTasks(tasks);
            if (!validTasks.isEmpty()) {
                return new PlannerDecision("expertsParallel", "", JSON.toJSONString(validTasks), true);
            }
        }
        Object plan = json.get("plan");
        return new PlannerDecision("routeReviewer", plan == null ? output : JSON.toJSONString(plan), "", false);
    }

    static JSONObject parseJson(String output) {
        if (!StringUtils.hasText(output)) return null;
        String value = output.trim();
        if (!value.startsWith("{") || !value.endsWith("}")) return null;
        try {
            return JSON.parseObject(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean validRequirements(JSONObject value) {
        String status = value.getString("status");
        JSONObject requirements = value.getJSONObject("requirements");
        if (!StringUtils.hasText(status) || requirements == null || !hasRequirementFields(requirements)) return false;
        if ("QUESTION".equalsIgnoreCase(status)) return StringUtils.hasText(value.getString("question"));
        return "CONFIRMED".equalsIgnoreCase(status)
                && StringUtils.hasText(requirements.getString("origin"))
                && StringUtils.hasText(requirements.getString("destination"))
                && StringUtils.hasText(requirements.getString("people"))
                && StringUtils.hasText(requirements.getString("budget"))
                && (StringUtils.hasText(requirements.getString("date"))
                || StringUtils.hasText(requirements.getString("days")));
    }

    private static boolean hasRequirementFields(JSONObject requirements) {
        return REQUIRED_FIELDS.stream().allMatch(requirements::containsKey);
    }

    private static JSONObject emptyRequirements() {
        JSONObject requirements = new JSONObject();
        REQUIREMENT_FIELDS.forEach(field -> requirements.put(field, null));
        return requirements;
    }

    private static JSONArray validTasks(JSONArray tasks) {
        JSONArray valid = new JSONArray();
        if (tasks == null) return valid;
        for (Object item : tasks) {
            if (!(item instanceof JSONObject task)) continue;
            String expert = task.getString("expert");
            if (!StringUtils.hasText(expert) || !EXPERTS.contains(expert.trim().toUpperCase(Locale.ROOT))) continue;
            if (task.get("task") == null) continue;
            JSONObject normalized = new JSONObject();
            normalized.put("expert", expert.trim().toUpperCase(Locale.ROOT));
            normalized.put("task", task.get("task"));
            valid.add(normalized);
        }
        return valid;
    }

    private static boolean validReview(JSONObject value) {
        String status = value.getString("status");
        JSONArray issues = value.getJSONArray("issues");
        if (!StringUtils.hasText(status) || issues == null) return false;
        if ("APPROVED".equalsIgnoreCase(status)) return issues.isEmpty();
        return "REVISE".equalsIgnoreCase(status) && !issues.isEmpty() && issues.size() <= 3
                && issues.stream().allMatch(item -> item instanceof String text && StringUtils.hasText(text));
    }

    record RequirementDecision(boolean confirmed, String structuredOutput) {
    }

    record ReviewDecision(boolean approved, String structuredOutput) {
    }

    record PlannerDecision(String next, String plan, String tasks, boolean delegates) {
    }
}
