package io.github.zancrow321.jadm.engine;

/**
 * Thrown when {@code OCG_CreateDuel} fails.
 */
public class DuelCreationException extends RuntimeException {
    private final Status status;

    public DuelCreationException(Status status) {
        super("OCG_CreateDuel failed: " + status);
        this.status = status;
    }

    public Status status() {
        return status;
    }

    /** Mirrors {@code OCG_DuelCreationStatus}. */
    public enum Status {
        SUCCESS, NO_OUTPUT, NOT_CREATED, NULL_DATA_READER, NULL_SCRIPT_READER, INCOMPATIBLE_LUA_API, NULL_RNG_SEED,
        UNKNOWN;

        static Status of(int raw) {
            Status[] values = values();
            return raw >= 0 && raw < values.length - 1 ? values[raw] : UNKNOWN;
        }
    }
}
