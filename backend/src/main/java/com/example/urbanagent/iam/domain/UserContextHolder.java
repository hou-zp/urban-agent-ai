package com.example.urbanagent.iam.domain;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        UserContext context = HOLDER.get();
        if (context == null) {
            return new UserContext("demo-user", "ADMIN", "shaoxing-keqiao");
        }
        return context;
    }

    public static UserContext currentOrNull() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
