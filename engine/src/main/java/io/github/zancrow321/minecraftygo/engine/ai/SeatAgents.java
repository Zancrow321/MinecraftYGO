package io.github.zancrow321.minecraftygo.engine.ai;

/** Maps duel seats (team 0/1, duelist index) to agents; returns null for seats played by humans. */
@FunctionalInterface
public interface SeatAgents {
	DuelAgent agent(int team, int duelist);

	static SeatAgents of(DuelAgent team0, DuelAgent team1) {
		return (team, _) -> team == 0 ? team0 : team1;
	}
}
