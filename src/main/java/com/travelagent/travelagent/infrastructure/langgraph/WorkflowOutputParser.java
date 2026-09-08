package com.travelagent.travelagent.infrastructure.langgraph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Parses and validates the small JSON contracts exchanged by workflow nodes. */
final class WorkflowOutputParser {
    private static final String INTENT_FIELD = "intent";
    private static final String ROUTE_INTENT = "route";
    private static final String NORMAL_INTENT = "normal";
    private static final String STATUS_FIELD = "status";
    private static final String REQUIREMENTS_FIELD = "requirements";
    private static final String QUESTION_FIELD = "question";
    private static final String TASKS_FIELD = "tasks";
    private static final String ACTION_FIELD = "action";
    private static final String PLAN_FIELD = "plan";
    private static final String ISSUES_FIELD = "issues";
    private static final String EXPERT_FIELD = "expert";
    private static final String TASK_FIELD = "task";
    private static final String STATUS_QUESTION = "QUESTION";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REVISE = "REVISE";
    private static final String ACTION_DELEGATE = "DELEGATE";
    private static final String ROUTE_REVIEWER_NODE = "routeReviewer";
    private static final String EXPERTS_PARALLEL_NODE = "expertsParallel";
    private static final int MAX_REVIEW_ISSUES = 3;
    private static final Set<String> EXPERTS = Set.of("KNOWLEDGE", "ROUTE", "BUDGET");
    private static final List<String> REQUIREMENT_FIELDS =
            List.of("origin", "destination", "date", "days", "people", "budget", "interests", "constraints");
    private static final List<String> REQUIRED_FIELDS =
            List.of("origin", "destination", "date", "days", "people", "budget");

    private WorkflowOutputParser() {
    }

    static String intent(String output) {
        JSONObject json = parseJson(output);
        return json != null && ROUTE_INTENT.equalsIgnoreCase(json.getString(INTENT_FIELD)) ? ROUTE_INTENT : NORMAL_INTENT;
    }

    static RequirementDecision requirements(String output) {
        JSONObject json = parseJson(output);
        if (json != null && validRequirements(json)) {
            return new RequirementDecision(
                    STATUS_CONFIRMED.equalsIgnoreCase(json.getString(STATUS_FIELD)),
                    JSON.toJSONString(json, JSONWriter.Feature.WriteMapNullValue));
        }
        JSONObject fallback = new JSONObject();
        fallback.put(STATUS_FIELD, STATUS_QUESTION);
        fallback.put(QUESTION_FIELD, "请补充路线规划所需的信息。");
        fallback.put(REQUIREMENTS_FIELD, emptyRequirements());
        return new RequirementDecision(false, JSON.toJSONString(fallback, JSONWriter.Feature.WriteMapNullValue));
    }

    static ReviewDecision review(String output) {
        JSONObject json = parseJson(output);
        if (json != null && validReview(json)) {
            return new ReviewDecision(
                    STATUS_APPROVED.equalsIgnoreCase(json.getString(STATUS_FIELD)),
                    JSON.toJSONString(json, JSONWriter.Feature.WriteMapNullValue));
        }
        JSONObject fallback = new JSONObject();
        fallback.put(STATUS_FIELD, STATUS_REVISE);
        fallback.put(ISSUES_FIELD, List.of("审核结果格式无效，请重新审核。"));
        return new ReviewDecision(false, JSON.toJSONString(fallback));
    }

    static PlannerDecision planner(String output) {
        JSONObject json = parseJson(output);
        if (json == null) return new PlannerDecision(ROUTE_REVIEWER_NODE, output, "", false);
        if (ACTION_DELEGATE.equalsIgnoreCase(json.getString(ACTION_FIELD))) {
            JSONArray tasks = json.getJSONArray(TASKS_FIELD);
            JSONArray validTasks = validTasks(tasks);
            if (!validTasks.isEmpty()) {
                return new PlannerDecision(EXPERTS_PARALLEL_NODE, "", JSON.toJSONString(validTasks), true);
            }
        }
        Object plan = json.get(PLAN_FIELD);
        return new PlannerDecision(ROUTE_REVIEWER_NODE, plan == null ? output : JSON.toJSONString(plan), "", false);
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
        String status = value.getString(STATUS_FIELD);
        JSONObject requirements = value.getJSONObject(REQUIREMENTS_FIELD);
        if (!StringUtils.hasText(status) || requirements == null || !hasRequirementFields(requirements)) return false;
        if (STATUS_QUESTION.equalsIgnoreCase(status)) return StringUtils.hasText(value.getString(QUESTION_FIELD));
        return STATUS_CONFIRMED.equalsIgnoreCase(status)
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
            String expert = task.getString(EXPERT_FIELD);
            if (!StringUtils.hasText(expert) || !EXPERTS.contains(expert.trim().toUpperCase(Locale.ROOT))) continue;
            if (task.get(TASK_FIELD) == null) continue;
            JSONObject normalized = new JSONObject();
            normalized.put(EXPERT_FIELD, expert.trim().toUpperCase(Locale.ROOT));
            normalized.put(TASK_FIELD, task.get(TASK_FIELD));
            valid.add(normalized);
        }
        return valid;
    }

    private static boolean validReview(JSONObject value) {
        String status = value.getString(STATUS_FIELD);
        JSONArray issues = value.getJSONArray(ISSUES_FIELD);
        if (!StringUtils.hasText(status) || issues == null) return false;
        if (STATUS_APPROVED.equalsIgnoreCase(status)) return issues.isEmpty();
        return STATUS_REVISE.equalsIgnoreCase(status) && !issues.isEmpty() && issues.size() <= MAX_REVIEW_ISSUES
                && issues.stream().allMatch(item -> item instanceof String text && StringUtils.hasText(text));
    }

    record RequirementDecision(boolean confirmed, String structuredOutput) {
    }

    record ReviewDecision(boolean approved, String structuredOutput) {
    }

    record PlannerDecision(String next, String plan, String tasks, boolean delegates) {
    }
}
