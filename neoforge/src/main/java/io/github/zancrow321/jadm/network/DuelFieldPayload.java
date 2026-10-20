package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: where the duel field is projected. Player 0's side faces {@code yaw} (degrees, Minecraft
 * convention), player 1 stands across from them. {@code active == false} removes the field.
 */
public record DuelFieldPayload(boolean active, double x, double y, double z, float yaw, Layout layout)
        implements CustomPacketPayload {
    /**
     * How the mat is split and dressed. Normally {@code sleeves} has one card sleeve per team. In a Battle City duel
     * ({@code split}) each partner has their own half of the team's zones and {@code sleeves} has one per duelist:
     * team 0's first and second partner, then team 1's. {@code watching} is set for a spectator's copy, and
     * {@code nearby} as well for a player who only stands near the duel and sees its field in passing.
     *
     * <p>A player-built arena fits the field to itself: {@code size} scales the whole mat (1 is the usual size),
     * {@code ceiling} is how many blocks of room there are over the mat (0 for open sky), so monsters stay under
     * the roof, and {@code outline} draws only the zone frames so the arena's own floor shows through.
     */
    public record Layout(boolean split, List<String> sleeves, boolean watching, double size, double ceiling,
                         boolean outline, boolean nearby) {
        public static final Layout NONE = new Layout(false, List.of(), false);
        private static final StreamCodec<ByteBuf, List<String>> SLEEVES =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
        public static final StreamCodec<ByteBuf, Layout> STREAM_CODEC = StreamCodec.of((buf, layout) -> {
            buf.writeBoolean(layout.split());
            SLEEVES.encode(buf, layout.sleeves());
            buf.writeBoolean(layout.watching());
            buf.writeDouble(layout.size());
            buf.writeDouble(layout.ceiling());
            buf.writeBoolean(layout.outline());
            buf.writeBoolean(layout.nearby());
        }, buf -> new Layout(buf.readBoolean(), SLEEVES.decode(buf), buf.readBoolean(), buf.readDouble(),
                buf.readDouble(), buf.readBoolean(), buf.readBoolean()));

        public Layout(boolean split, List<String> sleeves, boolean watching, double size, double ceiling,
                      boolean outline) {
            this(split, sleeves, watching, size, ceiling, outline, false);
        }

        public Layout(boolean split, List<String> sleeves, boolean watching) {
            this(split, sleeves, watching, 1, 0, false);
        }
    }

    public static final Type<DuelFieldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "duel_field"));
    public static final StreamCodec<ByteBuf, DuelFieldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DuelFieldPayload::active,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::x,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::y,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::z,
            ByteBufCodecs.FLOAT, DuelFieldPayload::yaw,
            Layout.STREAM_CODEC, DuelFieldPayload::layout,
            DuelFieldPayload::new);

    public DuelFieldPayload(boolean active, double x, double y, double z, float yaw) {
        this(active, x, y, z, yaw, Layout.NONE);
    }

    public DuelFieldPayload withLayout(boolean split, List<String> sleeves) {
        return new DuelFieldPayload(active, x, y, z, yaw, new Layout(split, List.copyOf(sleeves), false,
                layout.size(), layout.ceiling(), layout.outline()));
    }

    /** The field fitted to a player-built arena (see {@link Layout}). */
    public DuelFieldPayload fitted(double size, double ceiling, boolean outline) {
        return new DuelFieldPayload(active, x, y, z, yaw, new Layout(layout.split(), layout.sleeves(),
                layout.watching(), size, ceiling, outline));
    }

    /** The same field seen from the other end. */
    public DuelFieldPayload turned() {
        return new DuelFieldPayload(active, x, y, z, yaw + 180, layout);
    }

    /**
     * The field once the two teams trade ends, after the winner of the coin toss chose to go second: turned, with
     * the card sleeves of the two teams swapped too.
     */
    public DuelFieldPayload endsTraded() {
        List<String> sleeves = layout.sleeves();
        int half = sleeves.size() / 2;
        List<String> traded = new java.util.ArrayList<>(sleeves.subList(half, sleeves.size()));
        traded.addAll(sleeves.subList(0, half));
        return new DuelFieldPayload(active, x, y, z, yaw + 180, new Layout(layout.split(), List.copyOf(traded),
                layout.watching(), layout.size(), layout.ceiling(), layout.outline()));
    }

    /** The copy sent to a spectator. */
    public DuelFieldPayload watching() {
        return new DuelFieldPayload(active, x, y, z, yaw, new Layout(layout.split(), layout.sleeves(), true,
                layout.size(), layout.ceiling(), layout.outline()));
    }

    /** The copy sent to a player standing near the duel, who sees its field without watching it. */
    public DuelFieldPayload nearby() {
        return new DuelFieldPayload(active, x, y, z, yaw, new Layout(layout.split(), layout.sleeves(), true,
                layout.size(), layout.ceiling(), layout.outline(), true));
    }

    public static DuelFieldPayload none() {
        return new DuelFieldPayload(false, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
