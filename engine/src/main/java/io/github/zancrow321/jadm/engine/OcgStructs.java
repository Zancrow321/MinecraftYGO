package io.github.zancrow321.jadm.engine;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * JNA mirrors of the structs and callbacks in {@code ocgapi_types.h}. Field order must match the C declarations.
 */
final class OcgStructs {
    private OcgStructs() {
    }

    @Structure.FieldOrder({"code", "alias", "setcodes", "type", "level", "attribute", "race", "attack", "defense",
            "lscale", "rscale", "linkMarker"})
    public static class CardData extends Structure {
        public int code;
        public int alias;
        /** Zero-terminated uint16_t array; must stay alive until the core is done reading it. */
        public Pointer setcodes;
        public int type;
        public int level;
        public int attribute;
        public long race;
        public int attack;
        public int defense;
        public int lscale;
        public int rscale;
        public int linkMarker;

        public CardData(Pointer p) {
            super(p);
        }
    }

    @Structure.FieldOrder({"startingLP", "startingDrawCount", "drawCountPerTurn"})
    public static class Player extends Structure {
        public int startingLP;
        public int startingDrawCount;
        public int drawCountPerTurn;
    }

    public interface DataReader extends Callback {
        void invoke(Pointer payload, int code, Pointer data);
    }

    public interface DataReaderDone extends Callback {
        void invoke(Pointer payload, Pointer data);
    }

    public interface ScriptReader extends Callback {
        int invoke(Pointer payload, Pointer duel, String name);
    }

    public interface LogHandler extends Callback {
        void invoke(Pointer payload, String message, int type);
    }

    @Structure.FieldOrder({"seed", "flags", "team1", "team2", "cardReader", "payload1", "scriptReader", "payload2",
            "logHandler", "payload3", "cardReaderDone", "payload4", "enableUnsafeLibraries"})
    public static class DuelOptions extends Structure {
        public long[] seed = new long[4];
        public long flags;
        public Player team1 = new Player();
        public Player team2 = new Player();
        public DataReader cardReader;
        public Pointer payload1;
        public ScriptReader scriptReader;
        public Pointer payload2;
        public LogHandler logHandler;
        public Pointer payload3;
        public DataReaderDone cardReaderDone;
        public Pointer payload4;
        public byte enableUnsafeLibraries;
    }

    @Structure.FieldOrder({"team", "duelist", "code", "con", "loc", "seq", "pos"})
    public static class NewCardInfo extends Structure {
        public byte team;
        public byte duelist;
        public int code;
        public byte con;
        public int loc;
        public int seq;
        public int pos;
    }

    @Structure.FieldOrder({"flags", "con", "loc", "seq", "overlaySeq"})
    public static class QueryInfo extends Structure {
        public int flags;
        public byte con;
        public int loc;
        public int seq;
        public int overlaySeq;
    }
}
