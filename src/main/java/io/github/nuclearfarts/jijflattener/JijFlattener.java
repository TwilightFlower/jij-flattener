package io.github.nuclearfarts.jijflattener;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.util.version.VersionDeserializer;
import net.fabricmc.loader.util.version.VersionParsingException;

public class JijFlattener {
	private static final Map<String, String> FS_ENV = Collections.singletonMap("create", "true");
	private final Map<String, ComparableStorage<ModVersionEntry>> jijMods = new HashMap<>();
	private final Map<String, Path> nonJijMods = new HashMap<>();
	private final Set<String> mods = new HashSet<>();

	private final Path output;
	private final Path work;
	private final File input;

	public JijFlattener(File input, Path outDir, Path workDir) {
		output = outDir;
		work = workDir;
		this.input = input;
	}

	public JijFlattener(File input, Path outDir) {
		this(input, outDir, outDir.resolve(".flattenerwork"));
	}

	public JijFlattener(File input) {
		this(input, input.toPath().resolve("flattened"));
	}

	public void exec() throws IOException {
		jijMods.clear();
		nonJijMods.clear();
		mods.clear();
		Files.createDirectories(work);
		Files.createDirectories(output);
		for (File mod : input.listFiles()) {
			if (mod.isFile()) {
				process(mod, false);
			}
		}

		for (String modid : mods) {
			Path p;
			if ((p = nonJijMods.get(modid)) == null) {
				p = jijMods.get(modid).get().mod;
			}
			Files.copy(p, output.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void process(File mod, boolean isJij) throws IOException {
		System.out.println("jar:" + mod.toURI());
		if (!isJij) {
			mod = moveToWork(mod);
		}
		try (FileSystem modFs = FileSystems.newFileSystem(URI.create("jar:" + mod.toURI()), FS_ENV)) {
			JsonObject json = JsonParser.parseReader(Files.newBufferedReader(modFs.getPath("fabric.mod.json"))).getAsJsonObject();
			updateVersionInfo(mod.toPath(), json, isJij);
			extractJars(modFs, isJij);
			stripJars(modFs, json);
		}
	}

	private File moveToWork(File from) throws IOException {
		Path inWork = work.resolve(from.toPath().getFileName().toString());
		Files.copy(from.toPath(), inWork, StandardCopyOption.REPLACE_EXISTING);
		return inWork.toFile();
	}

	private void extractJars(FileSystem modFs, boolean isJij) throws IOException {
		Path jars = modFs.getPath("META-INF", "jars");
		if (Files.exists(jars)) {
			Files.walk(jars).filter(p -> !Files.isDirectory(p)).forEach(p -> {
				try {
					Path out = work.resolve(p.getFileName().toString());
					Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
					process(out.toFile(), true);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	private void updateVersionInfo(Path jar, JsonObject json, boolean isJij) throws IOException {
		String modid = json.get("id").getAsString();
		mods.add(modid);
		if (!isJij) {
			nonJijMods.put(modid, jar);
			return;
		} else if (nonJijMods.containsKey(modid)) {
			return; // don't bother with the rest, we have an override
		}

		Version v;
		try {
			v = VersionDeserializer.deserialize(json.get("version").getAsString());
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
		jijMods.computeIfAbsent(modid, s -> new ComparableStorage<>()).set(new ModVersionEntry(jar, v));
	}

	private void stripJars(FileSystem modFs, JsonObject json) throws IOException {
		json.remove("jars");
		try (Writer w = Files.newBufferedWriter(modFs.getPath("fabric.mod.json"), StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(json.toString());
		}
		Path jars = modFs.getPath("META-INF", "jars");
		if (Files.exists(jars)) {
			Files.walk(jars).filter(p -> !Files.isDirectory(p)).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	private static class ModVersionEntry implements Comparable<ModVersionEntry> {
		final Path mod;
		final Version version;

		ModVersionEntry(Path mod, Version version) {
			this.mod = mod;
			this.version = version;
		}

		@Override
		public int compareTo(ModVersionEntry o) {
			if (!(version instanceof SemanticVersion) || !(o.version instanceof SemanticVersion)) {
				// One or both is not semver. This sucks.
				if(version instanceof SemanticVersion) {
					// we're semver compliant, use this
					// TODO check loader's behavior here.
					return 1;
				} else if(o.version instanceof SemanticVersion) {
					// we aren't semver compliant, use other.
					return -1;
				} else {
					System.out.println("Both " + mod + " and " + o.mod + " are not semver compliant. Version resolution may not match that of fabric loader.");
					return version.getFriendlyString().hashCode() - o.version.getFriendlyString().hashCode();
				}
			}
			return ((SemanticVersion) version).compareTo((SemanticVersion) o.version);
		}
	}
}
