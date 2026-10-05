package io.github.zancrow321.minecraftygo.engine.data;

import java.util.Collection;
import java.util.Optional;

/** Read access to the numeric card data the engine needs. */
public interface CardDatabase {
	Optional<CardData> find(int code);

	/** All known card codes (used e.g. to answer {@code MSG_ANNOUNCE_CARD}). */
	Collection<Integer> codes();
}
