package io.github.zancrow321.minecraftygo.engine.data;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.script.ZipScriptProvider;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BundledDataTest {
	@Test
	void bundledCardDataMatchesBabelCdb() {
		JsonCardDatabase cards = BundledData.cards();
		assertThat(cards.size()).isGreaterThan(14_000);
		for (int code : TestEnvironment.database().codes()) {
			assertThat(cards.find(code)).as("card %d", code).contains(TestEnvironment.database().find(code).orElseThrow());
		}
	}

	@Test
	void poolIsConsistent() {
		CardPool pool = BundledData.pool();
		CardTexts texts = BundledData.texts();
		JsonCardDatabase cards = BundledData.cards();
		ZipScriptProvider scripts = BundledData.scripts();
		assertThat(pool.size()).isGreaterThan(1000);
		assertThat(pool.entries().stream().filter(CardPool.Entry::hasModel).count()).isEqualTo(622);
		for (CardPool.Entry entry : pool.entries()) {
			CardData data = cards.find(entry.code()).orElseThrow(() -> new AssertionError("no data for " + entry));
			assertThat(texts.name(entry.code())).as("name of %s", entry).contains(entry.name());
			assertThat(entry.extraDeck()).as("extra deck flag of %s", entry).isEqualTo(data.isExtraDeckCard());
			boolean needsScript = !(data.isType(OcgConstants.TYPE_NORMAL) && data.isMonster());
			if (needsScript) {
				assertThat(scripts.read("c" + entry.code() + ".lua")).as("script of %s", entry).isPresent();
			}
			if ("monster".equals(entry.kind())) {
				assertThat(data.isMonster()).isTrue();
			}
		}
		assertThat(scripts.read("constant.lua")).isPresent();
		assertThat(scripts.read("utility.lua")).isPresent();
		assertThat(scripts.read("proc_unofficial.lua")).isPresent();
	}

	@Test
	void effectStringsResolve() {
		CardTexts texts = BundledData.texts();
		// Monster Reborn has no strings, Change of Heart neither - find any pool card with strings.
		int code = BundledData.pool().entries().stream().map(CardPool.Entry::code)
				.filter(c -> !texts.get(c).orElseThrow().strings().isEmpty()).findFirst().orElseThrow();
		assertThat(texts.effectString(((long) code << 20))).isPresent();
		assertThat(texts.effectString(0)).isEmpty();
	}
}
