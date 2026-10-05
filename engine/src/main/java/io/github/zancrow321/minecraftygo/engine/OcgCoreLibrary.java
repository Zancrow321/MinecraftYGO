package io.github.zancrow321.minecraftygo.engine;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Raw JNA mapping of {@code ocgapi.h}. Use {@link OcgCore} and {@link OcgDuel} instead of calling this directly.
 */
interface OcgCoreLibrary extends Library {
    void OCG_GetVersion(IntByReference major, IntByReference minor);

    int OCG_CreateDuel(PointerByReference outDuel, OcgStructs.DuelOptions options);

    void OCG_DestroyDuel(Pointer duel);

    void OCG_DuelNewCard(Pointer duel, OcgStructs.NewCardInfo info);

    void OCG_StartDuel(Pointer duel);

    int OCG_DuelProcess(Pointer duel);

    Pointer OCG_DuelGetMessage(Pointer duel, IntByReference length);

    void OCG_DuelSetResponse(Pointer duel, byte[] buffer, int length);

    int OCG_LoadScript(Pointer duel, byte[] buffer, int length, String name);

    int OCG_DuelQueryCount(Pointer duel, byte team, int location);

    Pointer OCG_DuelQuery(Pointer duel, IntByReference length, OcgStructs.QueryInfo info);

    Pointer OCG_DuelQueryLocation(Pointer duel, IntByReference length, OcgStructs.QueryInfo info);

    Pointer OCG_DuelQueryField(Pointer duel, IntByReference length);
}
