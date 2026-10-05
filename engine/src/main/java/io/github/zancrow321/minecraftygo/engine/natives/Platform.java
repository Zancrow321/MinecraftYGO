package io.github.zancrow321.minecraftygo.engine.natives;

import java.util.Locale;

/** Native platforms the ocgcore library is built for; the id is the resource folder under {@code natives/}. */
public enum Platform {
	WINDOWS_X64("windows-x64", "ocgcore.dll"),
	WINDOWS_ARM64("windows-arm64", "ocgcore.dll"),
	LINUX_X64("linux-x64", "libocgcore.so"),
	LINUX_ARM64("linux-arm64", "libocgcore.so"),
	MACOS_UNIVERSAL("macos-universal", "libocgcore.dylib");

	private final String id;
	private final String libraryFileName;

	Platform(String id, String libraryFileName) {
		this.id = id;
		this.libraryFileName = libraryFileName;
	}

	public String id() {
		return id;
	}

	public String libraryFileName() {
		return libraryFileName;
	}

	/** Resource path of the library inside the mod jar. */
	public String resourcePath() {
		return "natives/" + id + "/" + libraryFileName;
	}

	public static Platform current() {
		return detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
	}

	static Platform detect(String osName, String osArch) {
		String os = osName.toLowerCase(Locale.ROOT);
		String arch = osArch.toLowerCase(Locale.ROOT);
		boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
		boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
		if (os.startsWith("mac") || os.startsWith("darwin")) {
			if (arm64 || x64) {
				return MACOS_UNIVERSAL;
			}
		} else if (os.startsWith("windows")) {
			if (x64) {
				return WINDOWS_X64;
			}
			if (arm64) {
				return WINDOWS_ARM64;
			}
		} else if (os.contains("linux")) {
			if (x64) {
				return LINUX_X64;
			}
			if (arm64) {
				return LINUX_ARM64;
			}
		}
		throw new UnsupportedOperationException("ocgcore is not available for " + osName + " / " + osArch);
	}
}
