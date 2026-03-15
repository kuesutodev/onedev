package io.onedev.server.data;

import static io.onedev.server.persistence.PersistenceUtils.callWithTransaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.onedev.commons.bootstrap.Bootstrap;
import io.onedev.commons.utils.ExplicitException;
import io.onedev.commons.utils.FileUtils;
import io.onedev.commons.utils.ZipUtils;
import io.onedev.server.OneDev;
import io.onedev.server.git.CommandUtils;
import io.onedev.commons.utils.command.LineConsumer;

@Singleton
public class InstanceArchiveManager {

	private static final Logger logger = LoggerFactory.getLogger(InstanceArchiveManager.class);

	private static final String MANIFEST = "manifest.json";

	private static final String DB_DIR = "db";

	private static final String SITE_DIR = "site";

	private static final String PROJECTS_DIR = "projects";

	private static final String ASSETS_DIR = "assets";

	private static final String LIB_DIR = "lib";

	private final DataService dataService;

	private final ObjectMapper objectMapper;

	@Inject
	public InstanceArchiveManager(DataService dataService, ObjectMapper objectMapper) {
		this.dataService = dataService;
		this.objectMapper = objectMapper;
	}

	public void exportArchive(File archiveFile) {
		exportArchive(archiveFile, RepositoryArchiveMode.FULL);
	}

	public void exportArchive(File archiveFile, RepositoryArchiveMode repositoryMode) {
		var tempDir = FileUtils.createTempDir("instance-archive");
		try {
			var dbDir = new File(tempDir, DB_DIR);
			var siteDir = new File(tempDir, SITE_DIR);
			var projectsDir = new File(siteDir, PROJECTS_DIR);
			var assetsDir = new File(siteDir, ASSETS_DIR);
			var libDir = new File(siteDir, LIB_DIR);

			dataService.exportData(dbDir);
			copyProjects(new File(Bootstrap.getSiteDir(), PROJECTS_DIR), projectsDir, repositoryMode);
			copyIfExists(OneDev.getAssetsDir(), assetsDir);
			copyIfExists(new File(Bootstrap.installDir, "site/" + LIB_DIR), libDir);

			writeManifest(tempDir, dbDir, projectsDir, assetsDir, libDir, repositoryMode);
			ZipUtils.zip(tempDir, archiveFile, null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			FileUtils.deleteDir(tempDir);
		}
	}

	public InstanceArchiveManifest restoreArchive(File archiveFile) {
		var tempDir = FileUtils.createTempDir("restore-archive");
		try {
			ZipUtils.unzip(archiveFile, tempDir);
			var manifest = readManifest(tempDir);
			validateVersion(manifest);
			validateChecksums(tempDir, manifest);
			restoreDatabase(new File(tempDir, DB_DIR));
			restoreSite(new File(tempDir, SITE_DIR));
			return manifest;
		} finally {
			FileUtils.deleteDir(tempDir);
		}
	}

	public InstanceArchiveManifest verifyArchive(File archiveFile) {
		var tempDir = FileUtils.createTempDir("verify-archive");
		try {
			ZipUtils.unzip(archiveFile, tempDir);
			var manifest = readManifest(tempDir);
			validateVersion(manifest);
			validateChecksums(tempDir, manifest);
			return manifest;
		} finally {
			FileUtils.deleteDir(tempDir);
		}
	}

	private void restoreDatabase(File dataDir) {
		dataService.migrateData(dataDir);

		try (var conn = dataService.openConnection()) {
			callWithTransaction(conn, () -> {
				var dbDataVersion = dataService.checkDataVersion(conn, true);

				if (dbDataVersion != null)
					dataService.cleanDatabase(conn);

				dataService.createTables(conn);
				return null;
			});
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		dataService.importData(dataDir);

		try (var conn = dataService.openConnection()) {
			callWithTransaction(conn, () -> {
				dataService.applyConstraints(conn);
				return null;
			});
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private void restoreSite(File archivedSiteDir) {
		if (!archivedSiteDir.exists())
			throw new ExplicitException("Archive does not contain site data");

		var siteDir = Bootstrap.getSiteDir();
		FileUtils.deleteDir(new File(siteDir, PROJECTS_DIR));
		FileUtils.deleteDir(new File(siteDir, ASSETS_DIR));
		FileUtils.deleteDir(new File(siteDir, LIB_DIR));
		try {
			copyIfExists(new File(archivedSiteDir, PROJECTS_DIR), new File(siteDir, PROJECTS_DIR));
			copyIfExists(new File(archivedSiteDir, ASSETS_DIR), new File(siteDir, ASSETS_DIR));
			copyIfExists(new File(archivedSiteDir, LIB_DIR), new File(siteDir, LIB_DIR));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void writeManifest(File tempDir, File dbDir, File projectsDir, File assetsDir, File libDir,
			RepositoryArchiveMode repositoryMode) throws IOException {
		var manifest = new InstanceArchiveManifest();
		manifest.setCreatedAt(System.currentTimeMillis());
		manifest.setVersion(loadVersion());
		manifest.setRepositoryMode(repositoryMode);
		manifest.setSegmentChecksums(new LinkedHashMap<>());
		manifest.getSegmentChecksums().put(DB_DIR, checksumOf(dbDir));
		manifest.getSegmentChecksums().put(SITE_DIR + "/" + PROJECTS_DIR, checksumOf(projectsDir));
		manifest.getSegmentChecksums().put(SITE_DIR + "/" + ASSETS_DIR, checksumOf(assetsDir));
		manifest.getSegmentChecksums().put(SITE_DIR + "/" + LIB_DIR, checksumOf(libDir));
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(tempDir, MANIFEST), manifest);
	}

	private InstanceArchiveManifest readManifest(File tempDir) {
		var manifestFile = new File(tempDir, MANIFEST);
		if (!manifestFile.exists())
			throw new ExplicitException("Archive manifest not found");

		try {
			var manifest = objectMapper.readValue(manifestFile, InstanceArchiveManifest.class);
			if (!"full-instance-archive".equals(manifest.getType()))
				throw new ExplicitException("Unsupported archive type: " + manifest.getType());
			if (manifest.getFormatVersion() != InstanceArchiveManifest.CURRENT_FORMAT_VERSION) {
				throw new ExplicitException("Unsupported archive format version: " + manifest.getFormatVersion());
			}
			if (!manifest.isDatabaseIncluded())
				throw new ExplicitException("Archive does not include database data");
			if (!manifest.isSiteIncluded())
				throw new ExplicitException("Archive does not include site data");
			if (!new File(tempDir, DB_DIR).exists())
				throw new ExplicitException("Archive database segment not found");
			if (!new File(tempDir, SITE_DIR).exists())
				throw new ExplicitException("Archive site segment not found");
			if (!new File(tempDir, SITE_DIR + "/" + PROJECTS_DIR).exists())
				throw new ExplicitException("Archive projects segment not found");
			if (!new File(tempDir, SITE_DIR + "/" + ASSETS_DIR).exists())
				throw new ExplicitException("Archive assets segment not found");
			if (!new File(tempDir, SITE_DIR + "/" + LIB_DIR).exists())
				throw new ExplicitException("Archive lib segment not found");
			if (manifest.getSegmentChecksums() == null || manifest.getSegmentChecksums().isEmpty())
				throw new ExplicitException("Archive segment checksums not found");
			return manifest;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void validateVersion(InstanceArchiveManifest manifest) {
		var currentVersion = loadVersion();
		if (manifest.getVersion() != null && currentVersion != null && !manifest.getVersion().equals(currentVersion)) {
			throw new ExplicitException("Archive version " + manifest.getVersion() + " does not match current instance version " + currentVersion);
		}
	}

	private void validateChecksums(File tempDir, InstanceArchiveManifest manifest) {
		validateChecksum(tempDir, manifest, DB_DIR, new File(tempDir, DB_DIR));
		validateChecksum(tempDir, manifest, SITE_DIR + "/" + PROJECTS_DIR, new File(tempDir, SITE_DIR + "/" + PROJECTS_DIR));
		validateChecksum(tempDir, manifest, SITE_DIR + "/" + ASSETS_DIR, new File(tempDir, SITE_DIR + "/" + ASSETS_DIR));
		validateChecksum(tempDir, manifest, SITE_DIR + "/" + LIB_DIR, new File(tempDir, SITE_DIR + "/" + LIB_DIR));
	}

	private void validateChecksum(File tempDir, InstanceArchiveManifest manifest, String key, File segmentDir) {
		var expected = manifest.getSegmentChecksums().get(key);
		if (expected == null)
			throw new ExplicitException("Archive checksum missing for segment: " + key);
		var actual = checksumOf(segmentDir);
		if (!expected.equals(actual))
			throw new ExplicitException("Archive checksum mismatch for segment: " + key);
	}

	private String checksumOf(File directory) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			updateDigest(digest, directory, "");
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private void updateDigest(MessageDigest digest, File file, String relativePath) throws IOException {
		if (file.isDirectory()) {
			digest.update(("D:" + relativePath + "\n").getBytes(StandardCharsets.UTF_8));
			var children = file.listFiles();
			if (children == null)
				throw new ExplicitException("Unable to list archive segment: " + file.getAbsolutePath());
			Arrays.sort(children, (left, right) -> left.getName().compareTo(right.getName()));
			for (var child: children) {
				var childPath = relativePath.isEmpty() ? child.getName() : relativePath + "/" + child.getName();
				updateDigest(digest, child, childPath);
			}
		} else {
			digest.update(("F:" + relativePath + ":" + file.length() + "\n").getBytes(StandardCharsets.UTF_8));
			try (var input = new FileInputStream(file)) {
				var buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) != -1)
					digest.update(buffer, 0, count);
			}
		}
	}

	private void copyProjects(File sourceProjectsDir, File targetProjectsDir, RepositoryArchiveMode repositoryMode)
			throws IOException {
		if (repositoryMode == RepositoryArchiveMode.FULL) {
			copyIfExists(sourceProjectsDir, targetProjectsDir);
			return;
		}

		FileUtils.deleteDir(targetProjectsDir);
		FileUtils.createDir(targetProjectsDir);
		if (!sourceProjectsDir.exists())
			return;

		var projectDirs = sourceProjectsDir.listFiles();
		if (projectDirs == null)
			throw new ExplicitException("Unable to list projects segment: " + sourceProjectsDir.getAbsolutePath());
		Arrays.sort(projectDirs, (left, right) -> left.getName().compareTo(right.getName()));
		for (var sourceProjectDir: projectDirs) {
			var targetProjectDir = new File(targetProjectsDir, sourceProjectDir.getName());
			if (sourceProjectDir.isDirectory())
				copyProjectDir(sourceProjectDir, targetProjectDir, repositoryMode);
			else
				copyPath(sourceProjectDir, targetProjectDir);
		}
	}

	private void copyProjectDir(File sourceProjectDir, File targetProjectDir, RepositoryArchiveMode repositoryMode)
			throws IOException {
		FileUtils.createDir(targetProjectDir);
		var children = sourceProjectDir.listFiles();
		if (children == null)
			throw new ExplicitException("Unable to list project storage: " + sourceProjectDir.getAbsolutePath());
		Arrays.sort(children, (left, right) -> left.getName().compareTo(right.getName()));
		for (var child: children) {
			var targetChild = new File(targetProjectDir, child.getName());
			if (repositoryMode == RepositoryArchiveMode.SHALLOW && child.isDirectory() && child.getName().equals("git"))
				copyShallowGitDir(child, targetChild);
			else
				copyPath(child, targetChild);
		}
	}

	private void copyShallowGitDir(File sourceGitDir, File targetGitDir) throws IOException {
		if (!isGitDir(sourceGitDir)) {
			copyPath(sourceGitDir, targetGitDir);
			return;
		}

		FileUtils.deleteDir(targetGitDir);
		FileUtils.createDir(targetGitDir.getParentFile());
		var git = CommandUtils.newGit().workingDir(targetGitDir.getParentFile());
		git.addArgs("clone", "--bare", "--depth", "1", "--no-single-branch", "--no-local",
				sourceGitDir.toURI().toString(), targetGitDir.getName());
		git.execute(new LineConsumer() {

			@Override
			public void consume(String line) {
				logger.trace(line);
			}

		}, new LineConsumer() {

			@Override
			public void consume(String line) {
				if (line.startsWith("Cloning into ") || line.equals("done."))
					logger.trace(line);
				else if (line.contains("empty repository"))
					logger.warn(line);
				else
					logger.error(line);
			}

		}).checkReturnCode();

		copyIfExists(new File(sourceGitDir, "hooks"), new File(targetGitDir, "hooks"));
		copyIfExists(new File(sourceGitDir, "lfs"), new File(targetGitDir, "lfs"));
	}

	private boolean isGitDir(File gitDir) {
		return new File(gitDir, "HEAD").exists() && new File(gitDir, "objects").exists();
	}

	private void copyPath(File source, File target) throws IOException {
		if (source.isDirectory())
			FileUtils.copyDirectory(source, target);
		else
			FileUtils.copyFile(source, target);
	}

	private void copyIfExists(File source, File target) throws IOException {
		FileUtils.deleteDir(target);
		if (source.exists()) {
			copyPath(source, target);
		} else {
			FileUtils.createDir(target);
		}
	}

	private String loadVersion() {
		var releasePropsFile = new File(Bootstrap.installDir, "release.properties");
		if (!releasePropsFile.exists())
			return null;

		Properties props = FileUtils.loadProperties(releasePropsFile);
		return props.getProperty("version");
	}

}