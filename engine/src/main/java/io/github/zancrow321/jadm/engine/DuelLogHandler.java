package io.github.zancrow321.jadm.engine;

/**
 * Receives log output from the core and from card scripts.
 */
@FunctionalInterface
public interface DuelLogHandler {
    void log(LogType type, String message);

    enum LogType {
        ERROR, FROM_SCRIPT, FOR_DEBUG, UNDEFINED;

        static LogType of(int raw) {
            LogType[] values = values();
            return raw >= 0 && raw < values.length ? values[raw] : UNDEFINED;
        }
    }
}
