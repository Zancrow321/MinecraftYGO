package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.deck.Deck;
import io.github.zancrow321.minecraftygo.engine.ffi.DuelDataSource;
import io.github.zancrow321.minecraftygo.engine.ffi.DuelStatus;
import io.github.zancrow321.minecraftygo.engine.ffi.NativeDuel;
import io.github.zancrow321.minecraftygo.engine.ffi.NewCard;
import io.github.zancrow321.minecraftygo.engine.ffi.OcgCore;
import io.github.zancrow321.minecraftygo.engine.ffi.OcgException;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.message.MessageParser;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse;
import io.github.zancrow321.minecraftygo.engine.prompt.ResponseEncoder;
import io.github.zancrow321.minecraftygo.engine.script.ScriptProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Owns one native duel and drives the process/prompt/response cycle. Not thread-safe; use it from a single thread
 * (the duel thread). Lifecycle: {@link #start} → repeatedly {@link #advance()} and {@link #respond} → {@link #close()}.
 */
public final class DuelSession implements AutoCloseable {
	private static final List<String> BOOTSTRAP_SCRIPTS = List.of("constant.lua", "utility.lua");

	private final NativeDuel duel;
	private final MessageDigest streamHash;
	private final List<byte[]> responses = new ArrayList<>();
	private Prompt pendingPrompt;
	private Prompt lastPrompt;
	private boolean ended;
	private int retries;

	private DuelSession(NativeDuel duel) {
		this.duel = duel;
		try {
			this.streamHash = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Creates the native duel, loads the bootstrap scripts, adds all (host-shuffled) decks and starts the duel. */
	public static DuelSession start(OcgCore core, DuelSetup setup, DuelDataSource dataSource, ScriptProvider scripts) {
		NativeDuel duel = core.createDuel(setup.toOptions(), dataSource);
		try {
			for (String name : BOOTSTRAP_SCRIPTS) {
				byte[] source = scripts.read(name).orElseThrow(() -> new OcgException("Missing bootstrap script " + name));
				if (!duel.loadScript(name, source)) {
					throw new OcgException("Failed to load bootstrap script " + name);
				}
			}
			SplittableRandom shuffle = new SplittableRandom(mix(setup.seed()));
			for (int team = 0; team < 2; team++) {
				List<Deck> duelists = setup.teams().get(team);
				for (int duelist = 0; duelist < duelists.size(); duelist++) {
					Deck deck = duelists.get(duelist);
					List<Integer> main = new ArrayList<>(deck.main());
					shuffle(main, shuffle);
					for (int code : main) {
						duel.newCard(new NewCard(team, duelist, code, team, OcgConstants.LOCATION_DECK, 0,
								OcgConstants.POS_FACEDOWN_DEFENSE));
					}
					for (int code : deck.extra()) {
						duel.newCard(new NewCard(team, duelist, code, team, OcgConstants.LOCATION_EXTRA, 0,
								OcgConstants.POS_FACEDOWN_DEFENSE));
					}
				}
			}
			duel.start();
			return new DuelSession(duel);
		} catch (RuntimeException e) {
			duel.close();
			throw e;
		}
	}

	private static long mix(long[] seed) {
		long value = 0x9E3779B97F4A7C15L;
		for (long s : seed) {
			value = (value ^ s) * 0xBF58476D1CE4E5B9L;
			value ^= value >>> 31;
		}
		return value;
	}

	private static <T> void shuffle(List<T> list, SplittableRandom random) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			T tmp = list.get(i);
			list.set(i, list.get(j));
			list.set(j, tmp);
		}
	}

	/** Result of one {@link #advance()}: all new messages, and the prompt to answer unless the duel ended. */
	public record Step(List<CoreMessage> messages, Prompt prompt, boolean ended) {}

	/** Processes until a response is required or the duel ends. */
	public Step advance() {
		if (ended) {
			return new Step(List.of(), null, true);
		}
		if (pendingPrompt != null) {
			throw new IllegalStateException("Answer the pending prompt first: " + pendingPrompt);
		}
		List<CoreMessage> collected = new ArrayList<>();
		while (true) {
			DuelStatus status = duel.process();
			byte[] buffer = duel.getMessage();
			streamHash.update(buffer);
			List<CoreMessage> messages = MessageParser.parse(buffer);
			collected.addAll(messages);
			if (status == DuelStatus.END) {
				ended = true;
				return new Step(collected, null, true);
			}
			if (status == DuelStatus.AWAITING) {
				pendingPrompt = findPrompt(messages);
				lastPrompt = pendingPrompt;
				return new Step(collected, pendingPrompt, false);
			}
		}
	}

	private Prompt findPrompt(List<CoreMessage> messages) {
		for (int i = messages.size() - 1; i >= 0; i--) {
			CoreMessage message = messages.get(i);
			if (message instanceof Prompt prompt) {
				return prompt;
			}
			if (message instanceof Event.Retry) {
				retries++;
				if (lastPrompt == null) {
					throw new OcgException("MSG_RETRY without a previous prompt");
				}
				return lastPrompt;
			}
		}
		throw new OcgException("Duel is awaiting a response but no prompt was sent: " + messages);
	}

	public void respond(PromptResponse response) {
		if (pendingPrompt == null) {
			throw new IllegalStateException("No prompt is pending");
		}
		respondRaw(ResponseEncoder.encode(pendingPrompt, response));
	}

	/** Sends pre-encoded response bytes (validation is skipped). */
	public void respondRaw(byte[] response) {
		if (pendingPrompt == null) {
			throw new IllegalStateException("No prompt is pending");
		}
		duel.setResponse(response);
		responses.add(response.clone());
		pendingPrompt = null;
	}

	public Prompt pendingPrompt() {
		return pendingPrompt;
	}

	public boolean ended() {
		return ended;
	}

	/** Number of {@code MSG_RETRY} the core sent, i.e. rejected responses. */
	public int retries() {
		return retries;
	}

	public int responseCount() {
		return responses.size();
	}

	/** All responses sent so far (together with the setup this is a complete replay). */
	public List<byte[]> responses() {
		return responses.stream().map(byte[]::clone).toList();
	}

	/** SHA-256 over every message buffer produced so far, for determinism checks. */
	public String streamHash() {
		try {
			return HexFormat.of().formatHex(((MessageDigest) streamHash.clone()).digest());
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Raw access for queries; do not process or respond through it. */
	public NativeDuel nativeDuel() {
		return duel;
	}

	@Override
	public void close() {
		duel.close();
	}
}
