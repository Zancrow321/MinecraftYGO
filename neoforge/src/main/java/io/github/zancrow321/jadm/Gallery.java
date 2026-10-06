package io.github.zancrow321.jadm;

import io.github.zancrow321.jadm.entity.MonsterEntity;
import io.github.zancrow321.jadm.entity.JadmEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code /jadm gallery [page]}: a debug grid of every modeled monster, one page at a time, in front of the caller.
 * {@code /jadm gallery clear} removes it again.
 */
final class Gallery {
    private static final int COLUMNS = 8;
    private static final int ROWS = 5;
    private static final double SPACING = 6;
    private static final double CLEAR_RADIUS = 128;

    private Gallery() {
    }

    static int show(CommandSourceStack source, int page) {
        List<Integer> codes = JadmData.modeled().monsters();
        int perPage = COLUMNS * ROWS;
        int pages = (codes.size() + perPage - 1) / perPage;
        if (page > pages) {
            source.sendFailure(Component.literal("The gallery has " + pages + " pages"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        clearAround(level, source.getPosition());
        // Lay the grid out ahead of the caller, facing them.
        float yaw = source.getRotation().y;
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 origin = source.getPosition().add(forward.scale(SPACING));
        int from = (page - 1) * perPage;
        int to = Math.min(codes.size(), from + perPage);
        for (int i = from; i < to; i++) {
            int slot = i - from;
            Vec3 pos = origin.add(forward.scale(slot / COLUMNS * SPACING))
                    .add(right.scale((slot % COLUMNS - (COLUMNS - 1) / 2.0) * SPACING));
            MonsterEntity monster = JadmEntities.MONSTER.get().create(level);
            if (monster == null) {
                continue;
            }
            int code = codes.get(i);
            monster.setCode(code);
            monster.moveTo(pos.x, Math.floor(pos.y), pos.z, yaw + 180, 0);
            monster.setCustomName(Component.literal(JadmData.text().cardName(code)));
            monster.setCustomNameVisible(true);
            level.addFreshEntity(monster);
        }
        source.sendSuccess(() -> Component.literal("Gallery page " + page + " of " + pages + " ("
                + (to - from) + " monsters)"), false);
        return to - from;
    }

    static int clear(CommandSourceStack source) {
        int removed = clearAround(source.getLevel(), source.getPosition());
        source.sendSuccess(() -> Component.literal("Removed " + removed + " gallery monsters"), false);
        return removed;
    }

    private static int clearAround(ServerLevel level, Vec3 center) {
        List<MonsterEntity> monsters = level.getEntitiesOfClass(MonsterEntity.class,
                new AABB(center, center).inflate(CLEAR_RADIUS));
        monsters.forEach(MonsterEntity::discard);
        return monsters.size();
    }
}
