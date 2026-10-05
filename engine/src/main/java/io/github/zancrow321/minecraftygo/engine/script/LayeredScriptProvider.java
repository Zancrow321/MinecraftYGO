package io.github.zancrow321.minecraftygo.engine.script;

import java.util.List;
import java.util.Optional;

/** Asks several providers in order (e.g. a server override folder before the bundled scripts). */
public final class LayeredScriptProvider implements ScriptProvider {
	private final List<ScriptProvider> layers;

	public LayeredScriptProvider(List<ScriptProvider> layers) {
		this.layers = List.copyOf(layers);
	}

	@Override
	public Optional<byte[]> read(String name) {
		for (ScriptProvider layer : layers) {
			Optional<byte[]> script = layer.read(name);
			if (script.isPresent()) {
				return script;
			}
		}
		return Optional.empty();
	}
}
