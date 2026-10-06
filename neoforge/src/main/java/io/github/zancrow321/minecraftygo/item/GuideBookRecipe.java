package io.github.zancrow321.minecraftygo.item;

import com.mojang.serialization.MapCodec;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * A shapeless recipe that only works while {@code [guide] craftable} is on. Recipes load before the server's config,
 * so the setting is checked when crafting rather than with a load condition.
 */
public final class GuideBookRecipe extends ShapelessRecipe {
    public static final MapCodec<GuideBookRecipe> CODEC = RecipeSerializer.SHAPELESS_RECIPE.codec()
            .xmap(GuideBookRecipe::new, recipe -> recipe);
    public static final StreamCodec<RegistryFriendlyByteBuf, GuideBookRecipe> STREAM_CODEC =
            RecipeSerializer.SHAPELESS_RECIPE.streamCodec().map(GuideBookRecipe::new, recipe -> recipe);

    private GuideBookRecipe(ShapelessRecipe recipe) {
        super(recipe.getGroup(), recipe.category(), recipe.getResultItem(null),
                recipe.getIngredients());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return craftable() && super.matches(input, level);
    }

    private static boolean craftable() {
        try {
            return YgoServerConfig.GUIDE_CRAFTABLE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return YgoItems.GUIDE_BOOK_RECIPE.get();
    }
}
