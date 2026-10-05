package io.github.zancrow321.minecraftygo.engine.script;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipScriptProviderTest {
	private static byte[] zip(String... pathsAndContents) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes)) {
			for (int i = 0; i < pathsAndContents.length; i += 2) {
				out.putNextEntry(new ZipEntry(pathsAndContents[i]));
				out.write(pathsAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	@Test
	void precedenceIsRootThenFolderOrderThenRest() throws IOException {
		byte[] archive = zip(
				"unofficial/c1.lua", "unofficial",
				"official/c1.lua", "official",
				"goat/c1.lua", "goat",
				"official/c2.lua", "﻿official2",
				"utility.lua", "root",
				"official/utility.lua", "nested",
				"readme.txt", "ignored");
		ZipScriptProvider provider = ZipScriptProvider.read(new ByteArrayInputStream(archive), List.of("goat", "official"));
		assertThat(provider.size()).isEqualTo(3);
		assertThat(new String(provider.read("c1.lua").orElseThrow(), StandardCharsets.UTF_8)).isEqualTo("goat");
		assertThat(new String(provider.read("c2.lua").orElseThrow(), StandardCharsets.UTF_8)).isEqualTo("official2");
		assertThat(new String(provider.read("utility.lua").orElseThrow(), StandardCharsets.UTF_8)).isEqualTo("root");
		assertThat(provider.read("missing.lua")).isEmpty();

		ScriptProvider layered = new LayeredScriptProvider(List.of(name -> name.equals("c2.lua")
				? java.util.Optional.of("override".getBytes(StandardCharsets.UTF_8)) : java.util.Optional.empty(), provider));
		assertThat(new String(layered.read("c2.lua").orElseThrow(), StandardCharsets.UTF_8)).isEqualTo("override");
		assertThat(layered.read("c1.lua")).isPresent();
	}
}
