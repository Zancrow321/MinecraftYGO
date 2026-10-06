package io.github.zancrow321.jadm.compat.figura;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.ClientDuel;
import io.github.zancrow321.jadm.client.disk.DiskClient;
import io.github.zancrow321.jadm.cosmetics.PlayerCosmetics;
import io.github.zancrow321.jadm.duel.DuelDisks;
import io.github.zancrow321.jadm.engine.duel.Board;
import io.github.zancrow321.jadm.engine.duel.DuelView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.entries.FiguraAPI;
import org.figuramc.figura.entries.annotations.FiguraAPIPlugin;
import org.figuramc.figura.lua.LuaWhitelist;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code jadm} global. Every client can tell whether an avatar's owner is dueling and which disk skin they wear;
 * the duel itself (life points, turn, hand counts) is only known on the owner's own client, so elsewhere those
 * return {@code nil}. Hidden information is never exposed.
 */
@FiguraAPIPlugin
@LuaWhitelist
public final class JadmLuaApi implements FiguraAPI {
    private static final Set<UUID> HIDDEN_DISKS = ConcurrentHashMap.newKeySet();

    private final Avatar avatar;

    /** For Figura's plugin loader; {@link #build} makes the one each avatar uses. */
    public JadmLuaApi() {
        this(null);
    }

    private JadmLuaApi(Avatar avatar) {
        this.avatar = avatar;
        if (avatar != null) {
            HIDDEN_DISKS.remove(avatar.owner);
        }
    }

    static boolean diskHidden(UUID owner) {
        return HIDDEN_DISKS.contains(owner);
    }

    private Player owner() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || avatar == null ? null : mc.level.getPlayerByUUID(avatar.owner);
    }

    /** The duel, if this avatar belongs to the local player and they are in one. */
    private DuelView duel() {
        Minecraft mc = Minecraft.getInstance();
        DuelView view = ClientDuel.view();
        return mc.player != null && avatar != null && avatar.owner.equals(mc.player.getUUID()) && view != null
                && view.result() == null ? view : null;
    }

    @LuaWhitelist
    public boolean isDueling() {
        Player owner = owner();
        return owner != null && DiskClient.dueling(owner);
    }

    @LuaWhitelist
    public String getDiskSkin() {
        Player owner = owner();
        return owner == null || DuelDisks.worn(owner).isEmpty() ? null : PlayerCosmetics.skin(DuelDisks.worn(owner));
    }

    /** Hides the mod's duel disk on this avatar, e.g. to draw your own. */
    @LuaWhitelist
    public void setDiskVisible(boolean visible) {
        if (avatar == null) {
            return;
        }
        if (visible) {
            HIDDEN_DISKS.remove(avatar.owner);
        } else {
            HIDDEN_DISKS.add(avatar.owner);
        }
    }

    @LuaWhitelist
    public Integer getLifePoints() {
        DuelView d = duel();
        return d == null ? null : d.board().side(d.you()).lifePoints();
    }

    @LuaWhitelist
    public Integer getOpponentLifePoints() {
        DuelView d = duel();
        return d == null ? null : d.board().side(1 - d.you()).lifePoints();
    }

    @LuaWhitelist
    public String getOpponentName() {
        DuelView d = duel();
        return d == null ? null : d.names().get(1 - d.you());
    }

    @LuaWhitelist
    public Integer getTurn() {
        DuelView d = duel();
        return d == null ? null : d.board().turn();
    }

    @LuaWhitelist
    public Boolean isMyTurn() {
        DuelView d = duel();
        return d == null ? null : d.board().turnPlayer() == d.you();
    }

    @LuaWhitelist
    public String getPhase() {
        DuelView d = duel();
        return d == null ? null : JadmData.text().phase(d.board().phase());
    }

    @LuaWhitelist
    public Integer getHandCount() {
        DuelView d = duel();
        return d == null ? null : d.board().side(d.you()).hand().size();
    }

    @LuaWhitelist
    public Integer getOpponentHandCount() {
        DuelView d = duel();
        return d == null ? null : d.board().side(1 - d.you()).hand().size();
    }

    @LuaWhitelist
    public Integer getDeckCount() {
        DuelView d = duel();
        Board.Side side = d == null ? null : d.board().side(d.you());
        return side == null ? null : side.deckCount();
    }

    @Override
    public FiguraAPI build(Avatar avatar) {
        return new JadmLuaApi(avatar);
    }

    @Override
    public String getName() {
        return "jadm";
    }

    @Override
    public Collection<Class<?>> getWhitelistedClasses() {
        return List.of(JadmLuaApi.class);
    }

    @Override
    public Collection<Class<?>> getDocsClasses() {
        return List.of();
    }

    @Override
    public String toString() {
        return "JadmAPI";
    }
}
