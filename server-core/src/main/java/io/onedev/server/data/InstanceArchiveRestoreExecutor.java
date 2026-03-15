package io.onedev.server.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.SystemUtils;

import io.onedev.commons.bootstrap.Bootstrap;
import io.onedev.commons.utils.ExplicitException;
import io.onedev.commons.utils.FileUtils;

@Singleton
public class InstanceArchiveRestoreExecutor {

	private final InstanceArchiveManager instanceArchiveManager;

	@Inject
	public InstanceArchiveRestoreExecutor(InstanceArchiveManager instanceArchiveManager) {
		this.instanceArchiveManager = instanceArchiveManager;
	}

	public File launch(File uploadedArchive) {
		instanceArchiveManager.verifyArchive(uploadedArchive);

		var restoreDir = new File(Bootstrap.installDir, "restore");
		var logDir = new File(Bootstrap.installDir, "logs");
		FileUtils.createDir(restoreDir);
		FileUtils.createDir(logDir);

		var stagedArchive = new File(restoreDir, "instance-restore-" + UUID.randomUUID() + ".zip");
		var logFile = new File(logDir, "restore-archive.log");
		try {
			Files.move(uploadedArchive.toPath(), stagedArchive.toPath(), StandardCopyOption.REPLACE_EXISTING);
			var builder = buildProcess(stagedArchive, logFile);
			builder.start();
			return logFile;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private ProcessBuilder buildProcess(File archiveFile, File logFile) {
		FileUtils.deleteFile(logFile);
		var script = getScriptFile();
		if (!script.exists())
			throw new ExplicitException("Restore script not found: " + script.getAbsolutePath());

		ProcessBuilder builder;
		if (SystemUtils.IS_OS_WINDOWS)
			builder = new ProcessBuilder("cmd", "/c", script.getAbsolutePath(), archiveFile.getAbsolutePath());
		else
			builder = new ProcessBuilder("sh", script.getAbsolutePath(), archiveFile.getAbsolutePath());

		builder.directory(Bootstrap.installDir);
		builder.redirectErrorStream(true);
		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
		return builder;
	}

	private File getScriptFile() {
		var extension = SystemUtils.IS_OS_WINDOWS ? ".bat" : ".sh";
		return new File(Bootstrap.installDir, "bin/restore-archive" + extension);
	}

}