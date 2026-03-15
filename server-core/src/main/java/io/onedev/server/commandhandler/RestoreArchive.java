package io.onedev.server.commandhandler;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.onedev.commons.bootstrap.Bootstrap;
import io.onedev.commons.utils.ExplicitException;
import io.onedev.server.data.InstanceArchiveManager;
import io.onedev.server.persistence.HibernateConfig;
import io.onedev.server.persistence.SessionFactoryService;
import io.onedev.server.security.SecurityUtils;

@Singleton
public class RestoreArchive extends CommandHandler {

	public static final String COMMAND = "restore-archive";

	private static final Logger logger = LoggerFactory.getLogger(RestoreArchive.class);

	private final SessionFactoryService sessionFactoryService;

	private final InstanceArchiveManager instanceArchiveManager;

	private File backupFile;

	@Inject
	public RestoreArchive(SessionFactoryService sessionFactoryService, HibernateConfig hibernateConfig,
			InstanceArchiveManager instanceArchiveManager) {
		super(hibernateConfig);
		this.sessionFactoryService = sessionFactoryService;
		this.instanceArchiveManager = instanceArchiveManager;
	}

	@Override
	public void start() {
		SecurityUtils.bindAsSystem();

		if (Bootstrap.command.getArgs().length == 0) {
			logger.error("Missing backup file parameter. Usage: {} <path to archive backup file>", Bootstrap.command.getScript());
			System.exit(1);
		}

		backupFile = new File(Bootstrap.command.getArgs()[0]);
		if (!backupFile.isAbsolute() && System.getenv("WRAPPER_INIT_DIR") != null)
			backupFile = new File(System.getenv("WRAPPER_INIT_DIR"), backupFile.getPath());

		if (!backupFile.exists()) {
			logger.error("Unable to find file: {}", backupFile.getAbsolutePath());
			System.exit(1);
		} else if (!backupFile.isFile()) {
			logger.error("A file is expected: {}", backupFile.getAbsolutePath());
			System.exit(1);
		} else if (!backupFile.canRead()) {
			logger.error("Backup file is not readable: {}", backupFile.getAbsolutePath());
			System.exit(1);
		}

		logger.info("Restoring full instance archive from {}...", backupFile.getAbsolutePath());

		try {
			doMaintenance(() -> {
				sessionFactoryService.start();
				var manifest = instanceArchiveManager.restoreArchive(backupFile);
				logger.info("Full instance archive is successfully restored from {}", backupFile.getAbsolutePath());
				if (manifest.getVersion() != null)
					logger.info("Restored archive version: {}", manifest.getVersion());
				return null;
			});
			System.exit(0);
		} catch (ExplicitException e) {
			logger.error(e.getMessage());
			System.exit(1);
		}
	}

	@Override
	public void stop() {
		sessionFactoryService.stop();
	}

}