package io.github.zancrow321.minecraftygo.engine;

/**
 * The stats OCG-Core asks for when it creates a card. Mirrors one row of the {@code datas} table in {@code cards.cdb}.
 *
 * @param setcodes archetype codes (each fits in 16 bits)
 */
public record CardData(int code, int alias, int[] setcodes, int type, int level, int attribute, long race,
                       int attack, int defense, int lscale, int rscale, int linkMarker) {
}
