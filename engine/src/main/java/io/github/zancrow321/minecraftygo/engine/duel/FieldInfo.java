package io.github.zancrow321.minecraftygo.engine.duel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The parts of {@code OCG_DuelQueryField} the mod uses: life points and deck sizes.
 */
final class FieldInfo {
    final int[] lifePoints = new int[2];
    final int[] deckCounts = new int[2];

    static FieldInfo parse(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        FieldInfo info = new FieldInfo();
        b.getInt(); // duel options (low 32 bits)
        for (int player = 0; player < 2; player++) {
            info.lifePoints[player] = b.getInt();
            for (int zone = 0; zone < 7 + 8; zone++) {
                if (b.get() != 0) {
                    b.get(); // position
                    b.getInt(); // overlay count
                }
            }
            info.deckCounts[player] = b.getInt();
            b.position(b.position() + 5 * 4); // hand, grave, removed, extra, extra pendulum counts
        }
        return info;
    }
}
