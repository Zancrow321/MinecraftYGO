package io.github.zancrow321.jadm.cosmetics;

import io.github.zancrow321.jadm.Jadm;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The disk skins and card sleeves, and what unlocks them. The textures come from
 * {@code tools/textures/make_cosmetic_textures.py}.
 */
public final class Cosmetics {
    public enum Stat {
        NONE(""), WINS("Win %d duels"), NPC_WINS("Beat %d NPC duelists"), PACKS("Open %d booster packs");

        private final String requirement;

        Stat(String requirement) {
            this.requirement = requirement;
        }
    }

    /** One skin or sleeve, unlocked once {@code stat} reaches {@code needed}. */
    public record Cosmetic(String id, String name, Stat stat, int needed) {
        public boolean unlocked(CosmeticsData data) {
            return stat == Stat.NONE || data.stat(stat) >= needed;
        }

        public String requirement() {
            return stat.requirement.formatted(needed);
        }
    }

    public static final String DEFAULT_SKIN = "battle_city";
    public static final String DEFAULT_SLEEVE = "classic";

    public static final List<Cosmetic> SKINS = List.of(
            new Cosmetic(DEFAULT_SKIN, "Battle City", Stat.NONE, 0),
            new Cosmetic("slifer_red", "Slifer Red", Stat.NONE, 0),
            new Cosmetic("ra_yellow", "Ra Yellow", Stat.WINS, 3),
            new Cosmetic("obelisk_blue", "Obelisk Blue", Stat.WINS, 10),
            new Cosmetic("shadow", "Shadow", Stat.NPC_WINS, 5),
            new Cosmetic("crimson", "Crimson", Stat.PACKS, 20),
            new Cosmetic("gold", "Gold", Stat.WINS, 25));

    public static final List<Cosmetic> SLEEVES = List.of(
            new Cosmetic(DEFAULT_SLEEVE, "Classic", Stat.NONE, 0),
            new Cosmetic("crimson", "Crimson", Stat.NONE, 0),
            new Cosmetic("emerald", "Emerald", Stat.WINS, 3),
            new Cosmetic("amethyst", "Amethyst", Stat.NPC_WINS, 3),
            new Cosmetic("onyx", "Onyx", Stat.PACKS, 10),
            new Cosmetic("gold", "Gold", Stat.WINS, 25));

    private Cosmetics() {
    }

    public static Cosmetic skin(String id) {
        return find(SKINS, id);
    }

    public static Cosmetic sleeve(String id) {
        return find(SLEEVES, id);
    }

    private static Cosmetic find(List<Cosmetic> list, String id) {
        return list.stream().filter(c -> c.id().equals(id)).findFirst().orElse(list.get(0));
    }

    /** The disk texture for a skin; {@code emissive} for its glowing layer. */
    public static ResourceLocation diskTexture(String base, String skin, boolean emissive) {
        String id = base + (skin == null || skin.equals(DEFAULT_SKIN) ? "" : "_" + skin) + (emissive ? "_e" : "");
        return ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/disk/" + id + ".png");
    }

    public static ResourceLocation sleeveTexture(String sleeve) {
        String id = sleeve == null || sleeve.equals(DEFAULT_SLEEVE) ? "card_back" : "sleeve_" + sleeve(sleeve).id();
        return ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/field/" + id + ".png");
    }
}
