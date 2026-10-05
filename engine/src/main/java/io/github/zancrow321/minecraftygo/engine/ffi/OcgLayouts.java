package io.github.zancrow321.minecraftygo.engine.ffi;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * FFM layouts of the public ocgcore structs (ocgapi_types.h) with explicit padding. They are identical on all
 * supported 64-bit ABIs (SysV x86-64, AArch64, Win64) and verified against the native layout probe in tests.
 */
public final class OcgLayouts {
	private OcgLayouts() {}

	public static final StructLayout CARD_DATA = MemoryLayout.structLayout(
			JAVA_INT.withName("code"),
			JAVA_INT.withName("alias"),
			ADDRESS.withName("setcodes"),
			JAVA_INT.withName("type"),
			JAVA_INT.withName("level"),
			JAVA_INT.withName("attribute"),
			MemoryLayout.paddingLayout(4),
			JAVA_LONG.withName("race"),
			JAVA_INT.withName("attack"),
			JAVA_INT.withName("defense"),
			JAVA_INT.withName("lscale"),
			JAVA_INT.withName("rscale"),
			JAVA_INT.withName("link_marker"),
			MemoryLayout.paddingLayout(4)).withName("OCG_CardData");

	public static final StructLayout PLAYER = MemoryLayout.structLayout(
			JAVA_INT.withName("startingLP"),
			JAVA_INT.withName("startingDrawCount"),
			JAVA_INT.withName("drawCountPerTurn")).withName("OCG_Player");

	public static final StructLayout DUEL_OPTIONS = MemoryLayout.structLayout(
			MemoryLayout.sequenceLayout(4, JAVA_LONG).withName("seed"),
			JAVA_LONG.withName("flags"),
			PLAYER.withName("team1"),
			PLAYER.withName("team2"),
			ADDRESS.withName("cardReader"),
			ADDRESS.withName("payload1"),
			ADDRESS.withName("scriptReader"),
			ADDRESS.withName("payload2"),
			ADDRESS.withName("logHandler"),
			ADDRESS.withName("payload3"),
			ADDRESS.withName("cardReaderDone"),
			ADDRESS.withName("payload4"),
			JAVA_BYTE.withName("enableUnsafeLibraries"),
			MemoryLayout.paddingLayout(7)).withName("OCG_DuelOptions");

	public static final StructLayout NEW_CARD_INFO = MemoryLayout.structLayout(
			JAVA_BYTE.withName("team"),
			JAVA_BYTE.withName("duelist"),
			MemoryLayout.paddingLayout(2),
			JAVA_INT.withName("code"),
			JAVA_BYTE.withName("con"),
			MemoryLayout.paddingLayout(3),
			JAVA_INT.withName("loc"),
			JAVA_INT.withName("seq"),
			JAVA_INT.withName("pos")).withName("OCG_NewCardInfo");

	public static final StructLayout QUERY_INFO = MemoryLayout.structLayout(
			JAVA_INT.withName("flags"),
			JAVA_BYTE.withName("con"),
			MemoryLayout.paddingLayout(3),
			JAVA_INT.withName("loc"),
			JAVA_INT.withName("seq"),
			JAVA_INT.withName("overlay_seq")).withName("OCG_QueryInfo");
}
