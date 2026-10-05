package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.DuelAgent;
import io.github.zancrow321.minecraftygo.engine.ai.SeatAgents;
import io.github.zancrow321.minecraftygo.engine.ffi.DuelDataSource;
import io.github.zancrow321.minecraftygo.engine.ffi.OcgCore;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.prompt.InvalidResponseException;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;
import io.github.zancrow321.minecraftygo.engine.query.FieldStateReader;
import io.github.zancrow321.minecraftygo.engine.script.ScriptProvider;
import io.github.zancrow321.minecraftygo.engine.view.Visibility;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs one duel on its own thread (all native calls of the duel happen there). Bots answer inline; human seats get a
 * {@link PromptTicket} through the {@link Listener} and answer with {@link #submit}. Every listener callback is
 * invoked on the duel thread; implementations hand work over to their own thread (e.g. the Minecraft server thread).
 */
public final class DuelRunner {
	private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

	/** How a duel ended. */
	public enum EndKind {
		/** The core declared a winner (or a draw). */
		FINISHED,
		/** A team surrendered or forfeited (e.g. by too many timeouts or leaving). */
		SURRENDER,
		/** Stopped by the host without a result (server shutdown, admin). */
		ABORTED,
		/** Native or script failure. */
		ERROR
	}

	/** {@code winner}: team 0/1, 2 for a draw, -1 if there is none (aborted/error). */
	public record Outcome(EndKind kind, int winner, int reason, DuelReplay replay) {}

	/** A prompt waiting for a human answer; {@code id} must be echoed in {@link #submit}. */
	public record PromptTicket(long id, int team, int duelist, Prompt prompt) {}

	/** Callbacks from the duel thread. */
	public interface Listener {
		/** Messages visible to {@code viewer} (team 0/1 or {@link Visibility#SPECTATOR}), already redacted. */
		void onMessages(int viewer, List<CoreMessage> messages);

		/** The field after a batch of messages, already redacted for {@code viewer}. */
		default void onField(int viewer, FieldState field) {}

		void onPrompt(PromptTicket ticket);

		default void onInvalidResponse(PromptTicket ticket, String reason) {}

		default void onTimeout(PromptTicket ticket) {}

		void onFinished(Outcome outcome);

		default void onError(Throwable error) {}
	}

	private sealed interface Command {}

	private record Submit(long ticketId, int team, int duelist, PromptResponse response) implements Command {}

	private record Surrender(int team) implements Command {}

	private record Abort() implements Command {}

	private final OcgCore core;
	private final DuelSetup setup;
	private final DuelDataSource dataSource;
	private final ScriptProvider scripts;
	private final SeatAgents bots;
	private final Listener listener;
	private final Duration promptTimeout;
	private final DuelAgent timeoutAgent;
	private final BlockingQueue<Command> commands = new LinkedBlockingQueue<>();
	private final AtomicBoolean started = new AtomicBoolean();
	private final AtomicBoolean finished = new AtomicBoolean();
	private final AtomicLong lastProgressNanos = new AtomicLong(System.nanoTime());
	private final TagRotation rotation;
	private Thread thread;
	private long nextTicketId = 1;

	/**
	 * @param bots          agents for bot seats; return null for human seats
	 * @param promptTimeout time a human has per decision; {@code timeoutAgent} answers when it runs out
	 */
	public DuelRunner(OcgCore core, DuelSetup setup, DuelDataSource dataSource, ScriptProvider scripts, SeatAgents bots,
			Listener listener, Duration promptTimeout, DuelAgent timeoutAgent) {
		this.core = core;
		this.setup = setup;
		this.dataSource = dataSource;
		this.scripts = scripts;
		this.bots = bots;
		this.listener = listener;
		this.promptTimeout = promptTimeout;
		this.timeoutAgent = timeoutAgent;
		this.rotation = new TagRotation(setup.teams().get(0).size(), setup.teams().get(1).size());
	}

	public void start() {
		if (!started.compareAndSet(false, true)) {
			throw new IllegalStateException("Already started");
		}
		thread = Thread.ofPlatform().daemon().name("ygo-duel-" + THREAD_COUNTER.incrementAndGet()).start(this::run);
	}

	/** Queues a human answer. Answers for stale tickets or wrong seats are ignored by the duel thread. */
	public void submit(long ticketId, int team, int duelist, PromptResponse response) {
		commands.add(new Submit(ticketId, team, duelist, response));
	}

	public void surrender(int team) {
		commands.add(new Surrender(team));
	}

	public void abort() {
		commands.add(new Abort());
	}

	public boolean isFinished() {
		return finished.get();
	}

	/** {@link System#nanoTime()} of the last completed native step, for hang detection. */
	public long lastProgressNanos() {
		return lastProgressNanos.get();
	}

	/** Waits for the duel thread to end. */
	public boolean join(Duration timeout) throws InterruptedException {
		return thread == null || thread.join(timeout);
	}

	private void run() {
		DuelSession session = null;
		try {
			session = DuelSession.start(core, setup, dataSource, scripts);
			Outcome outcome = loop(session);
			finish(outcome);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			finish(new Outcome(EndKind.ABORTED, -1, 0, replay(session)));
		} catch (Throwable t) {
			listener.onError(t);
			finish(new Outcome(EndKind.ERROR, -1, 0, replay(session)));
		} finally {
			if (session != null) {
				session.close();
			}
		}
	}

	private DuelReplay replay(DuelSession session) {
		return session == null ? null : DuelReplay.of(setup, session);
	}

	private void finish(Outcome outcome) {
		if (finished.compareAndSet(false, true)) {
			listener.onFinished(outcome);
		}
	}

	private Outcome loop(DuelSession session) throws InterruptedException {
		while (true) {
			Outcome early = drainControlCommands(session);
			if (early != null) {
				return early;
			}
			DuelSession.Step step = session.advance();
			lastProgressNanos.set(System.nanoTime());
			Event.Win win = dispatch(session, step.messages());
			if (win != null || step.ended()) {
				return win == null ? new Outcome(EndKind.FINISHED, 2, 0, replay(session))
						: new Outcome(EndKind.FINISHED, win.player(), win.reason(), replay(session));
			}
			Prompt prompt = step.prompt();
			int team = prompt.player();
			int duelist = rotation.active(team);
			DuelAgent bot = bots.agent(team, duelist);
			if (bot != null) {
				bot.observeField(Visibility.redact(FieldStateReader.read(session.nativeDuel()), team));
				session.respond(bot.respond(prompt));
				continue;
			}
			Outcome ended = awaitHuman(session, new PromptTicket(nextTicketId++, team, duelist, prompt));
			if (ended != null) {
				return ended;
			}
		}
	}

	/** Handles surrender/abort that arrived while bots were playing. Stale submits are dropped. */
	private Outcome drainControlCommands(DuelSession session) {
		List<Command> pending = new ArrayList<>();
		commands.drainTo(pending);
		for (Command command : pending) {
			Outcome outcome = control(command, session);
			if (outcome != null) {
				return outcome;
			}
		}
		return null;
	}

	private Outcome control(Command command, DuelSession session) {
		return switch (command) {
			case Surrender s -> new Outcome(EndKind.SURRENDER, 1 - s.team(), 0, replay(session));
			case Abort _ -> new Outcome(EndKind.ABORTED, -1, 0, replay(session));
			case Submit _ -> null;
		};
	}

	private Outcome awaitHuman(DuelSession session, PromptTicket ticket) throws InterruptedException {
		listener.onPrompt(ticket);
		long deadline = System.nanoTime() + promptTimeout.toNanos();
		while (true) {
			long remaining = deadline - System.nanoTime();
			Command command = remaining <= 0 ? null : commands.poll(remaining, TimeUnit.NANOSECONDS);
			if (command == null) {
				listener.onTimeout(ticket);
				timeoutAgent.observeField(Visibility.redact(FieldStateReader.read(session.nativeDuel()), ticket.team()));
				session.respond(timeoutAgent.respond(ticket.prompt()));
				return null;
			}
			if (command instanceof Submit submit) {
				if (submit.ticketId() != ticket.id() || submit.team() != ticket.team() || submit.duelist() != ticket.duelist()) {
					continue;
				}
				try {
					session.respond(submit.response());
					return null;
				} catch (InvalidResponseException e) {
					listener.onInvalidResponse(ticket, e.getMessage());
					continue;
				}
			}
			Outcome outcome = control(command, session);
			if (outcome != null) {
				return outcome;
			}
		}
	}

	/** Sends every message to each viewer it is visible to; returns the win message if the duel ended. */
	private Event.Win dispatch(DuelSession session, List<CoreMessage> messages) {
		Event.Win win = null;
		List<List<CoreMessage>> perViewer = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		for (CoreMessage message : messages) {
			rotation.observe(message);
			if (message instanceof Event.Win w) {
				win = w;
			}
			for (int viewer = -1; viewer <= 1; viewer++) {
				int slot = viewer + 1;
				Visibility.redact(message, viewer).ifPresent(perViewer.get(slot)::add);
			}
		}
		for (int viewer = -1; viewer <= 1; viewer++) {
			List<CoreMessage> visible = perViewer.get(viewer + 1);
			if (!visible.isEmpty()) {
				listener.onMessages(viewer, List.copyOf(visible));
				if (viewer >= 0) {
					for (int duelist = 0; duelist < rotation.duelists(viewer); duelist++) {
						DuelAgent bot = bots.agent(viewer, duelist);
						if (bot != null) {
							visible.forEach(bot::observe);
						}
					}
				}
			}
		}
		if (win == null && !messages.isEmpty()) {
			FieldState field = FieldStateReader.read(session.nativeDuel());
			for (int viewer = -1; viewer <= 1; viewer++) {
				listener.onField(viewer, Visibility.redact(field, viewer));
			}
		}
		return win;
	}
}
