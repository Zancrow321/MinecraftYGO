package io.github.zancrow321.jadm.clan;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A clan's crest is a banner: it is copied from one made on a loom, and handed out as one. */
public final class ClanCrests {
    private ClanCrests() {
    }

    /** The crest on {@code stack}, or {@code null} if it isn't a banner. */
    public static Clans.Crest of(ItemStack stack) {
        if (!(stack.getItem() instanceof BannerItem banner)) {
            return null;
        }
        List<Clans.Crest.Layer> layers = new ArrayList<>();
        BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        for (BannerPatternLayers.Layer layer : patterns.layers()) {
            layer.pattern().unwrapKey().ifPresent(key -> layers.add(
                    new Clans.Crest.Layer(key.location().toString(), layer.color().getId())));
        }
        return new Clans.Crest(banner.getColor().getId(), List.copyOf(layers));
    }

    /** A banner with {@code crest}, named after the clan. Patterns this world doesn't know are left out. */
    public static ItemStack banner(RegistryAccess registries, Clans.Clan clan) {
        Clans.Crest crest = clan.crest;
        DyeColor base = DyeColor.byId(Math.max(0, crest.base()));
        ItemStack stack = new ItemStack(BannerBlock.byColor(base).asItem());
        stack.set(DataComponents.BANNER_PATTERNS, layers(registries, crest));
        stack.set(DataComponents.ITEM_NAME, Component.literal("Banner of " + clan.name + " [" + clan.tag + "]")
                .withStyle(ClanText.color(clan.color)));
        return stack;
    }

    public static BannerPatternLayers layers(RegistryAccess registries, Clans.Crest crest) {
        Registry<BannerPattern> registry = registries.registryOrThrow(Registries.BANNER_PATTERN);
        BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
        for (Clans.Crest.Layer layer : crest.layers()) {
            ResourceLocation id = ResourceLocation.tryParse(layer.pattern());
            Optional<Holder.Reference<BannerPattern>> pattern = id == null ? Optional.empty()
                    : registry.getHolder(ResourceKey.create(Registries.BANNER_PATTERN, id));
            pattern.ifPresent(p -> builder.add(p, DyeColor.byId(layer.color())));
        }
        return builder.build();
    }
}
