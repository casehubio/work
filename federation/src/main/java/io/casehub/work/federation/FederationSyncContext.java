package io.casehub.work.federation;

public final class FederationSyncContext implements AutoCloseable {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private FederationSyncContext() {}

    public static FederationSyncContext activate() {
        ACTIVE.set(true);
        return new FederationSyncContext();
    }

    @Override
    public void close() {
        ACTIVE.remove();
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }
}
