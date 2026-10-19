package io.github.zancrow321.jadm.display;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Where things sit in a display. Positions are in blocks from the middle of the block, as in the display's model:
 * facing south (+z), its back at the north side, up being up. A card frame or showcase lying on the floor is that
 * model tipped onto its back.
 */
public final class DisplayGeometry {
    /**
     * Where one item stands: the middle of its bottom edge, how tall it may be and how far it leans back, in degrees.
     *
     * @param width the most it may be wide (products only; cards keep their shape)
     */
    public record Place(float x, float y, float z, float height, float width, float tilt) {
    }

    private static final List<Place> FRAME = List.of(place(8, 4.5f, 1.1f, 9, 0, 0));
    private static final List<Place> SHOWCASE = showcase();
    private static final List<Place> TOPLOADER = List.of(place(8, 2.2f, 8, 11, 0, 12));
    private static final List<Place> PACKS = List.of(place(3.2f, 1, 7, 8, 4.6f, 15), place(8, 1, 7, 8, 4.6f, 15),
            place(12.8f, 1, 7, 8, 4.6f, 15));

    private DisplayGeometry() {
    }

    /** A place given in pixels of the model, from its corner. */
    private static Place place(float x, float y, float z, float height, float width, float tilt) {
        return new Place(x / 16 - 0.5f, y / 16 - 0.5f, z / 16 - 0.5f, height / 16, width / 16, tilt);
    }

    private static List<Place> showcase() {
        List<Place> places = new ArrayList<>();
        for (float bottom : new float[]{10.5f, 6, 1.5f}) {
            for (float x : new float[]{3.8f, 8, 12.2f}) {
                places.add(place(x, bottom, 1.1f, 4, 0, 0));
            }
        }
        return List.copyOf(places);
    }

    /** The places of a display's slots, in slot order. */
    public static List<Place> places(DisplayKind kind) {
        return switch (kind) {
            case CARD_FRAME -> FRAME;
            case SHOWCASE -> SHOWCASE;
            case TOPLOADER_STAND -> TOPLOADER;
            case PACK_DISPLAY -> PACKS;
        };
    }

    /** Turns the model, around the middle of the block, the way the display stands. */
    public static Quaternionf rotation(BlockState state) {
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(
                -state.getValue(DisplayBlock.FACING).toYRot()));
        if (state.getBlock() instanceof DisplayBlock block && block.kind().wall()
                && state.getValue(DisplayBlock.FACE) == AttachFace.FLOOR) {
            q.rotateX((float) Math.toRadians(-90));
        }
        return q;
    }

    /** A point of the model, from the middle of the block, where it is in the placed display. */
    public static Vector3f toBlock(BlockState state, Vector3f model) {
        return rotation(state).transform(model);
    }
}
