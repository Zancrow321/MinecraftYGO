package io.github.zancrow321.minecraftygo.engine;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.HashMap;
import java.util.Map;

/**
 * One running duel inside OCG-Core. Not thread-safe; close it to free the native duel.
 *
 * <p>The usual loop is: add cards with {@link #newCard}, call {@link #start()}, then repeatedly
 * {@link #process()} and read {@link #getMessage()} until the status is {@link Status#AWAITING} (send a
 * {@link #setResponse response}) or {@link Status#END}.
 */
public final class OcgDuel implements AutoCloseable {
    private final OcgCoreLibrary lib;
    private Pointer handle;

    // The core keeps raw pointers to these callbacks, so they must stay strongly reachable while the duel lives.
    private final OcgStructs.DataReader cardReader;
    private final OcgStructs.ScriptReader scriptReader;
    private final OcgStructs.LogHandler logHandler;
    private final OcgStructs.DataReaderDone cardReaderDone;
    // Setcode arrays handed to the core, kept alive until it reports it is done with them.
    private final Map<Long, Memory> pendingSetcodes = new HashMap<>();

    OcgDuel(OcgCore core, DuelSettings settings, CardDataProvider cards, ScriptProvider scripts,
            DuelLogHandler log) {
        this.lib = core.lib;
        this.cardReader = (payload, code, data) -> writeCardData(cards.get(code), code, data);
        this.cardReaderDone = (payload, data) -> pendingSetcodes.remove(Pointer.nativeValue(data));
        this.scriptReader = (payload, duel, name) -> {
            byte[] script = scripts.read(name);
            return script == null ? 0 : lib.OCG_LoadScript(duel, script, script.length, name);
        };
        this.logHandler = (payload, message, type) -> log.log(DuelLogHandler.LogType.of(type), message);

        OcgStructs.DuelOptions options = new OcgStructs.DuelOptions();
        System.arraycopy(settings.seed(), 0, options.seed, 0, 4);
        options.flags = settings.flags();
        copyTeam(settings.team1(), options.team1);
        copyTeam(settings.team2(), options.team2);
        options.cardReader = cardReader;
        options.scriptReader = scriptReader;
        options.logHandler = logHandler;
        options.cardReaderDone = cardReaderDone;
        options.enableUnsafeLibraries = 0;

        PointerByReference out = new PointerByReference();
        int status = lib.OCG_CreateDuel(out, options);
        if (status != 0) {
            throw new DuelCreationException(DuelCreationException.Status.of(status));
        }
        this.handle = out.getValue();
    }

    private static void copyTeam(DuelSettings.Team from, OcgStructs.Player to) {
        to.startingLP = from.startingLp();
        to.startingDrawCount = from.startingDrawCount();
        to.drawCountPerTurn = from.drawCountPerTurn();
    }

    private void writeCardData(CardData card, int code, Pointer target) {
        OcgStructs.CardData data = new OcgStructs.CardData(target);
        data.code = code;
        if (card != null) {
            data.alias = card.alias();
            data.type = card.type();
            data.level = card.level();
            data.attribute = card.attribute();
            data.race = card.race();
            data.attack = card.attack();
            data.defense = card.defense();
            data.lscale = card.lscale();
            data.rscale = card.rscale();
            data.linkMarker = card.linkMarker();
        }
        int[] setcodes = card == null ? new int[0] : card.setcodes();
        Memory memory = new Memory(2L * (setcodes.length + 1));
        for (int i = 0; i < setcodes.length; i++) {
            memory.setShort(2L * i, (short) setcodes[i]);
        }
        memory.setShort(2L * setcodes.length, (short) 0);
        pendingSetcodes.put(Pointer.nativeValue(target), memory);
        data.setcodes = memory;
        data.write();
    }

    /**
     * Places a card before the duel starts (or while it runs, for tests).
     */
    public void newCard(int team, int duelist, int code, int controller, int location, int sequence, int position) {
        OcgStructs.NewCardInfo info = new OcgStructs.NewCardInfo();
        info.team = (byte) team;
        info.duelist = (byte) duelist;
        info.code = code;
        info.con = (byte) controller;
        info.loc = location;
        info.seq = sequence;
        info.pos = position;
        lib.OCG_DuelNewCard(handle(), info);
    }

    public void start() {
        lib.OCG_StartDuel(handle());
    }

    public Status process() {
        return Status.of(lib.OCG_DuelProcess(handle()));
    }

    /**
     * @return the raw message buffer produced by the last {@link #process()} call
     */
    public byte[] getMessage() {
        IntByReference length = new IntByReference();
        return readBuffer(lib.OCG_DuelGetMessage(handle(), length), length);
    }

    public void setResponse(byte[] response) {
        lib.OCG_DuelSetResponse(handle(), response, response.length);
    }

    public int queryCount(int team, int location) {
        return lib.OCG_DuelQueryCount(handle(), (byte) team, location);
    }

    /**
     * @return the raw field query buffer
     */
    public byte[] queryField() {
        IntByReference length = new IntByReference();
        return readBuffer(lib.OCG_DuelQueryField(handle(), length), length);
    }

    private static byte[] readBuffer(Pointer buffer, IntByReference length) {
        int size = length.getValue();
        return buffer == null || size <= 0 ? new byte[0] : buffer.getByteArray(0, size);
    }

    private Pointer handle() {
        if (handle == null) {
            throw new IllegalStateException("Duel is closed");
        }
        return handle;
    }

    @Override
    public void close() {
        if (handle != null) {
            lib.OCG_DestroyDuel(handle);
            handle = null;
            pendingSetcodes.clear();
        }
    }

    /** Mirrors {@code OCG_DuelStatus}. */
    public enum Status {
        END, AWAITING, CONTINUE;

        static Status of(int raw) {
            return switch (raw) {
                case 0 -> END;
                case 1 -> AWAITING;
                case 2 -> CONTINUE;
                default -> throw new IllegalStateException("Unknown duel status " + raw);
            };
        }
    }
}
