package io.github.zancrow321.minecraftygo.engine.ffi;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.data.CardData;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * {@link OcgCore} on top of the Foreign Function &amp; Memory API (Java 22+).
 *
 * <p>Rules enforced here: upcall stubs live in a per-duel arena that outlives every native call of the duel;
 * exceptions never cross the native boundary (they are captured and re-thrown by the Java call that triggered
 * them, after which the duel is unusable); every buffer returned by the core is copied immediately.
 */
final class FfmOcgCore implements OcgCore {
	private static final Linker LINKER = Linker.nativeLinker();
	private static final Map<Path, FfmOcgCore> LOADED = new ConcurrentHashMap<>();

	private static final FunctionDescriptor CARD_READER = FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS);
	private static final FunctionDescriptor SCRIPT_READER = FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS);
	private static final FunctionDescriptor LOG_HANDLER = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT);
	private static final MethodHandle READ_CARD;
	private static final MethodHandle READ_SCRIPT;
	private static final MethodHandle LOG;

	static {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			READ_CARD = lookup.findVirtual(FfmDuel.class, "readCard",
					MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class));
			READ_SCRIPT = lookup.findVirtual(FfmDuel.class, "readScript",
					MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
			LOG = lookup.findVirtual(FfmDuel.class, "log",
					MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, int.class));
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private final MethodHandle createDuel;
	private final MethodHandle destroyDuel;
	private final MethodHandle duelNewCard;
	private final MethodHandle startDuel;
	private final MethodHandle duelProcess;
	private final MethodHandle duelGetMessage;
	private final MethodHandle duelSetResponse;
	private final MethodHandle loadScript;
	private final MethodHandle duelQueryCount;
	private final MethodHandle duelQuery;
	private final MethodHandle duelQueryLocation;
	private final MethodHandle duelQueryField;
	private final int versionMajor;
	private final int versionMinor;

	static FfmOcgCore load(Path library) {
		return LOADED.computeIfAbsent(library.toAbsolutePath().normalize(), FfmOcgCore::new);
	}

	private FfmOcgCore(Path library) {
		SymbolLookup lookup = SymbolLookup.libraryLookup(library, Arena.global());
		MethodHandle getVersion = downcall(lookup, "OCG_GetVersion", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
		createDuel = downcall(lookup, "OCG_CreateDuel", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
		destroyDuel = downcall(lookup, "OCG_DestroyDuel", FunctionDescriptor.ofVoid(ADDRESS));
		duelNewCard = downcall(lookup, "OCG_DuelNewCard", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
		startDuel = downcall(lookup, "OCG_StartDuel", FunctionDescriptor.ofVoid(ADDRESS));
		duelProcess = downcall(lookup, "OCG_DuelProcess", FunctionDescriptor.of(JAVA_INT, ADDRESS));
		duelGetMessage = downcall(lookup, "OCG_DuelGetMessage", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
		duelSetResponse = downcall(lookup, "OCG_DuelSetResponse", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT));
		loadScript = downcall(lookup, "OCG_LoadScript", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
		duelQueryCount = downcall(lookup, "OCG_DuelQueryCount", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_BYTE, JAVA_INT));
		duelQuery = downcall(lookup, "OCG_DuelQuery", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
		duelQueryLocation = downcall(lookup, "OCG_DuelQueryLocation", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
		duelQueryField = downcall(lookup, "OCG_DuelQueryField", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment major = arena.allocate(JAVA_INT);
			MemorySegment minor = arena.allocate(JAVA_INT);
			getVersion.invokeExact(major, minor);
			versionMajor = major.get(JAVA_INT, 0);
			versionMinor = minor.get(JAVA_INT, 0);
		} catch (Throwable t) {
			throw new OcgException("OCG_GetVersion failed", t);
		}
		if (versionMajor != OcgConstants.OCG_VERSION_MAJOR || versionMinor != OcgConstants.OCG_VERSION_MINOR) {
			throw new OcgException("Incompatible ocgcore API " + versionMajor + "." + versionMinor + " (expected "
					+ OcgConstants.OCG_VERSION_MAJOR + "." + OcgConstants.OCG_VERSION_MINOR + ") in " + library);
		}
	}

	private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
		return LINKER.downcallHandle(lookup.findOrThrow(name), descriptor);
	}

	@Override
	public int versionMajor() {
		return versionMajor;
	}

	@Override
	public int versionMinor() {
		return versionMinor;
	}

	@Override
	public NativeDuel createDuel(DuelOptions options, DuelDataSource dataSource) {
		return new FfmDuel(options, dataSource);
	}

	private static long offset(StructLayout layout, String field) {
		return layout.byteOffset(PathElement.groupElement(field));
	}

	private static final long CD_CODE = offset(OcgLayouts.CARD_DATA, "code");
	private static final long CD_ALIAS = offset(OcgLayouts.CARD_DATA, "alias");
	private static final long CD_SETCODES = offset(OcgLayouts.CARD_DATA, "setcodes");
	private static final long CD_TYPE = offset(OcgLayouts.CARD_DATA, "type");
	private static final long CD_LEVEL = offset(OcgLayouts.CARD_DATA, "level");
	private static final long CD_ATTRIBUTE = offset(OcgLayouts.CARD_DATA, "attribute");
	private static final long CD_RACE = offset(OcgLayouts.CARD_DATA, "race");
	private static final long CD_ATTACK = offset(OcgLayouts.CARD_DATA, "attack");
	private static final long CD_DEFENSE = offset(OcgLayouts.CARD_DATA, "defense");
	private static final long CD_LSCALE = offset(OcgLayouts.CARD_DATA, "lscale");
	private static final long CD_RSCALE = offset(OcgLayouts.CARD_DATA, "rscale");
	private static final long CD_LINK_MARKER = offset(OcgLayouts.CARD_DATA, "link_marker");

	private static final long DO_SEED = offset(OcgLayouts.DUEL_OPTIONS, "seed");
	private static final long DO_FLAGS = offset(OcgLayouts.DUEL_OPTIONS, "flags");
	private static final long DO_TEAM1 = offset(OcgLayouts.DUEL_OPTIONS, "team1");
	private static final long DO_TEAM2 = offset(OcgLayouts.DUEL_OPTIONS, "team2");
	private static final long DO_CARD_READER = offset(OcgLayouts.DUEL_OPTIONS, "cardReader");
	private static final long DO_SCRIPT_READER = offset(OcgLayouts.DUEL_OPTIONS, "scriptReader");
	private static final long DO_LOG_HANDLER = offset(OcgLayouts.DUEL_OPTIONS, "logHandler");
	private static final long DO_UNSAFE_LIBS = offset(OcgLayouts.DUEL_OPTIONS, "enableUnsafeLibraries");

	private static final long NC_TEAM = offset(OcgLayouts.NEW_CARD_INFO, "team");
	private static final long NC_DUELIST = offset(OcgLayouts.NEW_CARD_INFO, "duelist");
	private static final long NC_CODE = offset(OcgLayouts.NEW_CARD_INFO, "code");
	private static final long NC_CON = offset(OcgLayouts.NEW_CARD_INFO, "con");
	private static final long NC_LOC = offset(OcgLayouts.NEW_CARD_INFO, "loc");
	private static final long NC_SEQ = offset(OcgLayouts.NEW_CARD_INFO, "seq");
	private static final long NC_POS = offset(OcgLayouts.NEW_CARD_INFO, "pos");

	private static final long QI_FLAGS = offset(OcgLayouts.QUERY_INFO, "flags");
	private static final long QI_CON = offset(OcgLayouts.QUERY_INFO, "con");
	private static final long QI_LOC = offset(OcgLayouts.QUERY_INFO, "loc");
	private static final long QI_SEQ = offset(OcgLayouts.QUERY_INFO, "seq");
	private static final long QI_OVERLAY_SEQ = offset(OcgLayouts.QUERY_INFO, "overlay_seq");

	private static void writeTeam(MemorySegment options, long base, DuelOptions.Team team) {
		options.set(JAVA_INT, base, team.startingLp());
		options.set(JAVA_INT, base + 4, team.startingDrawCount());
		options.set(JAVA_INT, base + 8, team.drawCountPerTurn());
	}

	private static String readCString(MemorySegment pointer) {
		return pointer.equals(MemorySegment.NULL) ? "" : pointer.reinterpret(Long.MAX_VALUE).getString(0);
	}

	private static byte[] copy(MemorySegment pointer, int length) {
		if (length <= 0 || pointer.equals(MemorySegment.NULL)) {
			return new byte[0];
		}
		return pointer.reinterpret(length).toArray(JAVA_BYTE);
	}

	final class FfmDuel implements NativeDuel {
		private final Arena arena = Arena.ofShared();
		private final DuelDataSource source;
		private final Map<Integer, MemorySegment> setcodeArrays = new ConcurrentHashMap<>();
		private final MemorySegment lengthOut;
		private MemorySegment handle;
		private Throwable callbackFailure;

		FfmDuel(DuelOptions options, DuelDataSource source) {
			this.source = source;
			this.lengthOut = arena.allocate(JAVA_INT);
			try {
				MemorySegment cardReader = LINKER.upcallStub(READ_CARD.bindTo(this), CARD_READER, arena);
				MemorySegment scriptReader = LINKER.upcallStub(READ_SCRIPT.bindTo(this), SCRIPT_READER, arena);
				MemorySegment logHandler = LINKER.upcallStub(LOG.bindTo(this), LOG_HANDLER, arena);

				MemorySegment opts = arena.allocate(OcgLayouts.DUEL_OPTIONS);
				long[] seed = options.seed();
				for (int i = 0; i < 4; i++) {
					opts.set(JAVA_LONG, DO_SEED + i * 8L, seed[i]);
				}
				opts.set(JAVA_LONG, DO_FLAGS, options.flags());
				writeTeam(opts, DO_TEAM1, options.team1());
				writeTeam(opts, DO_TEAM2, options.team2());
				opts.set(ADDRESS, DO_CARD_READER, cardReader);
				opts.set(ADDRESS, DO_SCRIPT_READER, scriptReader);
				opts.set(ADDRESS, DO_LOG_HANDLER, logHandler);
				opts.set(JAVA_BYTE, DO_UNSAFE_LIBS, (byte) 0);

				MemorySegment out = arena.allocate(ADDRESS);
				int status = (int) createDuel.invokeExact(out, opts);
				if (status != 0) {
					throw new OcgException("OCG_CreateDuel failed with status " + status);
				}
				handle = out.get(ADDRESS, 0);
				rethrowCallbackFailure();
			} catch (Throwable t) {
				if (handle != null) {
					destroyQuietly();
				}
				arena.close();
				throw t instanceof OcgException e ? e : new OcgException("Failed to create duel", t);
			}
		}

		// ---- upcalls (never throw) --------------------------------------------------------------

		void readCard(MemorySegment payload, int code, MemorySegment data) {
			try {
				Optional<CardData> card = source.card(code);
				if (card.isEmpty()) {
					return;
				}
				CardData c = card.get();
				MemorySegment target = data.reinterpret(OcgLayouts.CARD_DATA.byteSize());
				target.set(JAVA_INT, CD_CODE, c.code());
				target.set(JAVA_INT, CD_ALIAS, c.alias());
				target.set(ADDRESS, CD_SETCODES, setcodes(c));
				target.set(JAVA_INT, CD_TYPE, c.type());
				target.set(JAVA_INT, CD_LEVEL, c.level());
				target.set(JAVA_INT, CD_ATTRIBUTE, c.attribute());
				target.set(JAVA_LONG, CD_RACE, c.race());
				target.set(JAVA_INT, CD_ATTACK, c.attack());
				target.set(JAVA_INT, CD_DEFENSE, c.defense());
				target.set(JAVA_INT, CD_LSCALE, c.leftScale());
				target.set(JAVA_INT, CD_RSCALE, c.rightScale());
				target.set(JAVA_INT, CD_LINK_MARKER, c.linkMarker());
			} catch (Throwable t) {
				recordFailure(t);
			}
		}

		/** Zero-terminated uint16 array, kept alive (and cached per code) for the lifetime of the duel. */
		private MemorySegment setcodes(CardData card) {
			int[] codes = card.setcodes();
			if (codes.length == 0) {
				return MemorySegment.NULL;
			}
			return setcodeArrays.computeIfAbsent(card.code(), _ -> {
				MemorySegment array = arena.allocate(JAVA_SHORT, codes.length + 1L);
				for (int i = 0; i < codes.length; i++) {
					array.setAtIndex(JAVA_SHORT, i, (short) codes[i]);
				}
				return array;
			});
		}

		int readScript(MemorySegment payload, MemorySegment duel, MemorySegment name) {
			try {
				String scriptName = readCString(name);
				Optional<byte[]> script = source.script(scriptName);
				if (script.isEmpty()) {
					return 0;
				}
				byte[] bytes = script.get();
				try (Arena temp = Arena.ofConfined()) {
					MemorySegment buffer = temp.allocateFrom(JAVA_BYTE, bytes);
					int loaded = (int) loadScript.invokeExact(duel, buffer, bytes.length, name);
					return loaded != 0 ? 1 : 0;
				}
			} catch (Throwable t) {
				recordFailure(t);
				return 0;
			}
		}

		void log(MemorySegment payload, MemorySegment message, int type) {
			try {
				source.log(LogType.fromNative(type), readCString(message));
			} catch (Throwable t) {
				recordFailure(t);
			}
		}

		private void recordFailure(Throwable t) {
			if (callbackFailure == null) {
				callbackFailure = t;
			} else if (callbackFailure != t) {
				callbackFailure.addSuppressed(t);
			}
		}

		private void rethrowCallbackFailure() {
			if (callbackFailure != null) {
				throw new OcgException("Duel callback failed", callbackFailure);
			}
		}

		private MemorySegment handle() {
			if (handle == null) {
				throw new IllegalStateException("Duel is closed");
			}
			rethrowCallbackFailure();
			return handle;
		}

		// ---- API ------------------------------------------------------------------------------------

		@Override
		public void newCard(NewCard card) {
			MemorySegment duel = handle();
			try (Arena temp = Arena.ofConfined()) {
				MemorySegment info = temp.allocate(OcgLayouts.NEW_CARD_INFO);
				info.set(JAVA_BYTE, NC_TEAM, (byte) card.team());
				info.set(JAVA_BYTE, NC_DUELIST, (byte) card.duelist());
				info.set(JAVA_INT, NC_CODE, card.code());
				info.set(JAVA_BYTE, NC_CON, (byte) card.controller());
				info.set(JAVA_INT, NC_LOC, card.location());
				info.set(JAVA_INT, NC_SEQ, card.sequence());
				info.set(JAVA_INT, NC_POS, card.position());
				duelNewCard.invokeExact(duel, info);
			} catch (Throwable t) {
				throw wrap("OCG_DuelNewCard", t);
			}
			rethrowCallbackFailure();
		}

		@Override
		public boolean loadScript(String name, byte[] script) {
			MemorySegment duel = handle();
			int result;
			try (Arena temp = Arena.ofConfined()) {
				MemorySegment buffer = temp.allocateFrom(JAVA_BYTE, script);
				MemorySegment cName = temp.allocateFrom(name);
				result = (int) loadScript.invokeExact(duel, buffer, script.length, cName);
			} catch (Throwable t) {
				throw wrap("OCG_LoadScript", t);
			}
			rethrowCallbackFailure();
			return result != 0;
		}

		@Override
		public void start() {
			MemorySegment duel = handle();
			try {
				startDuel.invokeExact(duel);
			} catch (Throwable t) {
				throw wrap("OCG_StartDuel", t);
			}
			rethrowCallbackFailure();
		}

		@Override
		public DuelStatus process() {
			MemorySegment duel = handle();
			int status;
			try {
				status = (int) duelProcess.invokeExact(duel);
			} catch (Throwable t) {
				throw wrap("OCG_DuelProcess", t);
			}
			rethrowCallbackFailure();
			return DuelStatus.fromNative(status);
		}

		@Override
		public byte[] getMessage() {
			MemorySegment duel = handle();
			try {
				MemorySegment pointer = (MemorySegment) duelGetMessage.invokeExact(duel, lengthOut);
				return copy(pointer, lengthOut.get(JAVA_INT, 0));
			} catch (Throwable t) {
				throw wrap("OCG_DuelGetMessage", t);
			}
		}

		@Override
		public void setResponse(byte[] response) {
			MemorySegment duel = handle();
			try (Arena temp = Arena.ofConfined()) {
				MemorySegment buffer = response.length == 0 ? MemorySegment.NULL : temp.allocateFrom(JAVA_BYTE, response);
				duelSetResponse.invokeExact(duel, buffer, response.length);
			} catch (Throwable t) {
				throw wrap("OCG_DuelSetResponse", t);
			}
		}

		@Override
		public int queryCount(int team, int location) {
			MemorySegment duel = handle();
			try {
				return (int) duelQueryCount.invokeExact(duel, (byte) team, location);
			} catch (Throwable t) {
				throw wrap("OCG_DuelQueryCount", t);
			}
		}

		@Override
		public byte[] query(QueryRequest request) {
			return runQuery(duelQuery, request, "OCG_DuelQuery");
		}

		@Override
		public byte[] queryLocation(QueryRequest request) {
			return runQuery(duelQueryLocation, request, "OCG_DuelQueryLocation");
		}

		private byte[] runQuery(MethodHandle function, QueryRequest request, String name) {
			MemorySegment duel = handle();
			try (Arena temp = Arena.ofConfined()) {
				MemorySegment info = temp.allocate(OcgLayouts.QUERY_INFO);
				info.set(JAVA_INT, QI_FLAGS, request.flags());
				info.set(JAVA_BYTE, QI_CON, (byte) request.controller());
				info.set(JAVA_INT, QI_LOC, request.location());
				info.set(JAVA_INT, QI_SEQ, request.sequence());
				info.set(JAVA_INT, QI_OVERLAY_SEQ, request.overlaySequence());
				MemorySegment pointer = (MemorySegment) function.invokeExact(duel, lengthOut, info);
				return copy(pointer, lengthOut.get(JAVA_INT, 0));
			} catch (Throwable t) {
				throw wrap(name, t);
			}
		}

		@Override
		public byte[] queryField() {
			MemorySegment duel = handle();
			try {
				MemorySegment pointer = (MemorySegment) duelQueryField.invokeExact(duel, lengthOut);
				return copy(pointer, lengthOut.get(JAVA_INT, 0));
			} catch (Throwable t) {
				throw wrap("OCG_DuelQueryField", t);
			}
		}

		@Override
		public boolean isClosed() {
			return handle == null;
		}

		@Override
		public void close() {
			if (handle == null) {
				return;
			}
			destroyQuietly();
			arena.close();
		}

		private void destroyQuietly() {
			try {
				destroyDuel.invokeExact(handle);
			} catch (Throwable t) {
				throw wrap("OCG_DestroyDuel", t);
			} finally {
				handle = null;
			}
		}

		private OcgException wrap(String function, Throwable t) {
			return t instanceof OcgException e ? e : new OcgException(function + " failed", t);
		}
	}
}
