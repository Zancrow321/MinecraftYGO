package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.HeuristicAgent;
import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.ai.SafeDefaultAgent;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import io.github.zancrow321.minecraftygo.engine.view.Visibility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DuelRunnerTest {
	/** Records callbacks; a "human" answers each ticket from another thread like a network player would. */
	private static final class RecordingListener implements DuelRunner.Listener {
		final CompletableFuture<DuelRunner.Outcome> outcome = new CompletableFuture<>();
		final ConcurrentLinkedQueue<DuelRunner.PromptTicket> tickets = new ConcurrentLinkedQueue<>();
		final AtomicInteger spectatorMessages = new AtomicInteger();
		final AtomicInteger promptsSeenByWrongViewer = new AtomicInteger();
		final AtomicInteger timeouts = new AtomicInteger();
		final AtomicInteger fields = new AtomicInteger();
		volatile java.util.function.Consumer<DuelRunner.PromptTicket> onPrompt = _ -> {};

		@Override
		public void onMessages(int viewer, List<CoreMessage> messages) {
			if (viewer == Visibility.SPECTATOR) {
				spectatorMessages.addAndGet(messages.size());
			}
			for (CoreMessage message : messages) {
				if (message instanceof Prompt prompt && prompt.player() != viewer) {
					promptsSeenByWrongViewer.incrementAndGet();
				}
			}
		}

		@Override
		public void onField(int viewer, FieldState field) {
			fields.incrementAndGet();
		}

		@Override
		public void onPrompt(DuelRunner.PromptTicket ticket) {
			tickets.add(ticket);
			onPrompt.accept(ticket);
		}

		@Override
		public void onTimeout(DuelRunner.PromptTicket ticket) {
			timeouts.incrementAndGet();
		}

		@Override
		public void onFinished(DuelRunner.Outcome result) {
			outcome.complete(result);
		}

		@Override
		public void onError(Throwable error) {
			outcome.completeExceptionally(error);
		}
	}

	private static DuelRunner runner(RecordingListener listener, Duration timeout, long seed) {
		HeuristicAgent bot = new HeuristicAgent(seed, TestEnvironment.database());
		return new DuelRunner(TestEnvironment.core(), Duels.single(seed), TestEnvironment.dataSource(),
				TestEnvironment.scripts(), (team, _) -> team == 1 ? bot : null, listener, timeout,
				new SafeDefaultAgent(TestEnvironment.database()));
	}

	@Test
	void humanAgainstBotThroughTickets() throws Exception {
		RecordingListener listener = new RecordingListener();
		DuelRunner runner = runner(listener, Duration.ofSeconds(30), 71);
		RandomLegalAgent human = new RandomLegalAgent(71, TestEnvironment.database());
		listener.onPrompt = ticket -> CompletableFuture.runAsync(() ->
				runner.submit(ticket.id(), ticket.team(), ticket.duelist(), human.respond(ticket.prompt())));
		runner.start();
		DuelRunner.Outcome outcome = listener.outcome.get(60, TimeUnit.SECONDS);
		assertThat(outcome.kind()).isEqualTo(DuelRunner.EndKind.FINISHED);
		assertThat(outcome.winner()).isBetween(0, 2);
		assertThat(listener.tickets).isNotEmpty().allMatch(t -> t.team() == 0);
		assertThat(listener.promptsSeenByWrongViewer).hasValue(0);
		assertThat(listener.spectatorMessages.get()).isPositive();
		assertThat(listener.fields.get()).isPositive();
		assertThat(listener.timeouts).hasValue(0);
		assertThat(outcome.replay().responses()).isNotEmpty();
		assertThat(runner.join(Duration.ofSeconds(5))).isTrue();
	}

	@Test
	void surrenderEndsTheDuel() throws Exception {
		RecordingListener listener = new RecordingListener();
		DuelRunner runner = runner(listener, Duration.ofSeconds(30), 72);
		listener.onPrompt = ticket -> runner.surrender(ticket.team());
		runner.start();
		DuelRunner.Outcome outcome = listener.outcome.get(30, TimeUnit.SECONDS);
		assertThat(outcome.kind()).isEqualTo(DuelRunner.EndKind.SURRENDER);
		assertThat(outcome.winner()).isEqualTo(1);
	}

	@Test
	void timeoutsAreAnsweredBySafeDefaults() throws Exception {
		RecordingListener listener = new RecordingListener();
		DuelRunner runner = runner(listener, Duration.ofMillis(1), 73);
		runner.start();
		DuelRunner.Outcome outcome = listener.outcome.get(60, TimeUnit.SECONDS);
		assertThat(outcome.kind()).isEqualTo(DuelRunner.EndKind.FINISHED);
		assertThat(listener.timeouts.get()).isPositive();
	}

	@Test
	void staleOrForeignTicketsAreIgnoredAndAbortWorks() throws Exception {
		RecordingListener listener = new RecordingListener();
		DuelRunner runner = runner(listener, Duration.ofSeconds(30), 74);
		listener.onPrompt = ticket -> {
			RandomLegalAgent human = new RandomLegalAgent(1, TestEnvironment.database());
			runner.submit(ticket.id() + 1000, ticket.team(), ticket.duelist(), human.respond(ticket.prompt()));
			runner.submit(ticket.id(), 1 - ticket.team(), ticket.duelist(), human.respond(ticket.prompt()));
			runner.abort();
		};
		runner.start();
		DuelRunner.Outcome outcome = listener.outcome.get(30, TimeUnit.SECONDS);
		assertThat(outcome.kind()).isEqualTo(DuelRunner.EndKind.ABORTED);
		assertThat(listener.tickets).hasSize(1);
	}
}
