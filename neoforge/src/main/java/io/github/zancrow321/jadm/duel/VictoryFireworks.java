package io.github.zancrow321.jadm.duel;

import io.github.zancrow321.jadm.clan.ClanText;
import io.github.zancrow321.jadm.clan.Clans;
import io.github.zancrow321.jadm.network.DuelFieldPayload;
import io.github.zancrow321.jadm.network.VictoryFireworksPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Fireworks for the winner of a duel, over their half of the field, in the colors of their clans (gold and white
 * without one). Everyone near sees them; the duelists and spectators once the final blow has played out.
 */
final class VictoryFireworks {
    /** How far away people still see them, in blocks. */
    private static final double RANGE = 96;

    private VictoryFireworks() {
    }

    /**
     * @param winners the winning duelists that are there (people, or the NPC that won)
     * @param losers  the others, to find the winner's end of the field when a bot without a body won
     */
    static void celebrate(MinecraftServer server, DuelFieldPayload field, List<Entity> winners, List<Entity> losers) {
        List<Entity> anyone = winners.isEmpty() ? losers : winners;
        if (field == null || anyone.isEmpty() || !(anyone.getFirst().level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = new Vec3(field.x(), field.y(), field.z());
        Vec3 side = Vec3.ZERO;
        if (winners.isEmpty()) {
            // A bot won: its end is across the field from the people it beat.
            for (Entity loser : losers) {
                side = side.add(center.scale(2).subtract(loser.position()));
            }
            side = side.scale(1.0 / losers.size());
        }
        List<Integer> colors = new ArrayList<>();
        Clans clans = Clans.get(server);
        for (Entity winner : winners) {
            side = side.add(winner.position());
            Clans.Clan clan = winner instanceof ServerPlayer ? clans.of(winner.getUUID()) : null;
            if (clan != null && !colors.contains(ClanText.rgb(clan.color))) {
                colors.add(ClanText.rgb(clan.color));
            }
        }
        if (!winners.isEmpty()) {
            side = side.scale(1.0 / winners.size());
        }
        // Halfway between the middle of the mat and the winner's end.
        Vec3 at = new Vec3((center.x + side.x) / 2, center.y, (center.z + side.z) / 2);
        if (!colors.isEmpty()) {
            colors.add(0xFFFFFF);
        }
        double size = field.layout().size() > 0 ? field.layout().size() : 1;
        VictoryFireworksPayload payload = new VictoryFireworksPayload(at.x, at.y, at.z, size, field.layout().ceiling(),
                colors);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) < RANGE * RANGE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
