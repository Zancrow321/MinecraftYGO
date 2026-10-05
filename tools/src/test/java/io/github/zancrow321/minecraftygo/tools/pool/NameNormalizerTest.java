package io.github.zancrow321.minecraftygo.tools.pool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameNormalizerTest {
	@Test
	void folderNamesMatchCardNames() {
		assertThat(NameNormalizer.normalize("Jinzo7")).isEqualTo(NameNormalizer.normalize("Jinzo #7"));
		assertThat(NameNormalizer.normalize("MWarrior1")).isEqualTo(NameNormalizer.normalize("M-Warrior #1"));
		assertThat(NameNormalizer.normalize("LaJinnTheMysticalGenieOfTheLamp"))
				.isEqualTo(NameNormalizer.normalize("La Jinn the Mystical Genie of the Lamp"));
		assertThat(NameNormalizer.normalize("RayTemparature")).isNotEqualTo(NameNormalizer.normalize("Ray & Temperature"));
		assertThat(NameNormalizer.levenshtein("skullgaurdian", "skullguardian")).isEqualTo(2);
	}
}
