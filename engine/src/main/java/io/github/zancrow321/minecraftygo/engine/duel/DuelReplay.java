package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ffi.DuelDataSource;
import io.github.zancrow321.minecraftygo.engine.ffi.OcgCore;
import io.github.zancrow321.minecraftygo.engine.script.ScriptProvider;

import java.util.List;

/**
 * A complete record of a duel: the setup (incl. seed) and every response. Replaying it against the same core,
 * scripts and card data reproduces the exact message stream ({@code streamHash}).
 */
public record DuelReplay(DuelSetup setup, List<byte[]> responses, String streamHash) {
	public DuelReplay {
		responses = responses.stream().map(byte[]::clone).toList();
	}

	@Override
	public List<byte[]> responses() {
		return responses.stream().map(byte[]::clone).toList();
	}

	public static DuelReplay of(DuelSetup setup, DuelSession session) {
		return new DuelReplay(setup, session.responses(), session.streamHash());
	}

	/** Re-runs the duel; returns the stream hash of the replayed duel. */
	public String replay(OcgCore core, DuelDataSource dataSource, ScriptProvider scripts) {
		try (DuelSession session = DuelSession.start(core, setup, dataSource, scripts)) {
			for (byte[] response : responses) {
				DuelSession.Step step = session.advance();
				if (step.ended()) {
					throw new IllegalStateException("Duel ended before all responses were replayed");
				}
				session.respondRaw(response);
			}
			session.advance();
			return session.streamHash();
		}
	}
}
