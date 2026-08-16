package com.travelagent.travelagent.agent.observation;

public final class AgentObservationContextHolder {
    private static final ThreadLocal<AgentObservationContext> CURRENT = new ThreadLocal<>();

    private AgentObservationContextHolder() { }

    public static AgentObservationContext current() {
        return CURRENT.get();
    }

    public static Scope open(AgentObservationContext context) {
        AgentObservationContext previous = CURRENT.get();
        CURRENT.set(context);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
