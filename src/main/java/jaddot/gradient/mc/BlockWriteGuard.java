package jaddot.gradient.mc;

public class BlockWriteGuard {
    private BlockWriteGuard() {}

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void begin() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void end() {
        int d = DEPTH.get() - 1;
        if (d < 0) d = 0;
        DEPTH.set(d);
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static void runGuarded(Runnable r) {
        begin();
        try {
            r.run();
        } finally {
            end();
        }
    }
}
