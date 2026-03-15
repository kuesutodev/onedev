package io.onedev.server.commandhandler;

import static io.onedev.server.persistence.PersistenceUtils.callWithTransaction;

import java.io.File;
import java.sql.SQLException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.onedev.commons.bootstrap.Bootstrap;
import io.onedev.commons.utils.ExplicitException;
import io.onedev.server.data.DataService;
import io.onedev.server.data.InstanceArchiveManager;
import io.onedev.server.data.RepositoryArchiveMode;
import io.onedev.server.persistence.HibernateConfig;
import io.onedev.server.persistence.SessionFactoryService;
import io.onedev.server.security.SecurityUtils;

@Singleton
public class BackupArchive extends CommandHandler {

	public static final String COMMAND = "backup-archive";

	private static final Logger logger = LoggerFactory.getLogger(BackupArchive.class);

	private final DataService dataService;

	private final SessionFactoryService sessionFactoryService;

	private final InstanceArchiveManager instanceArchiveManager;

	private RepositoryArchiveMode repositoryMode = RepositoryArchiveMode.FULL;

	private File backupFile;

	@Inject
	public BackupArchive(DataService dataService, SessionFactoryService sessionFactoryService,
			HibernateConfig hibernateConfig, InstanceArchiveManager instanceArchiveManager) {
		super(hibernateConfig);
		this.dataService = dataService;
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
		if (Bootstrap.command.getArgs().length > 1)
			repositoryMode = parseRepositoryMode(Bootstrap.command.getArgs()[1]);

		if (!backupFile.isAbsolute() && System.getenv("WRAPPER_INIT_DIR") != null)
			backupFile = new File(System.getenv("WRAPPER_INIT_DIR"), backupFile.getPath());

		if (backupFile.exists()) {
			logger.error("Backup file already exists: {}", backupFile.getAbsolutePath());
			System.exit(1);
		}

		try {
			doMaintenance(() -> {
				sessionFactoryService.start();

				try (var conn = dataService.openConnection()) {
					callWithTransaction(conn, () -> {
						dataService.checkDataVersion(conn, false);
						return null;
					});
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}

				logger.info("Backing up {} instance archive to {}...", repositoryMode.name().toLowerCase(), backupFile.getAbsolutePath());
				instanceArchiveManager.exportArchive(backupFile, repositoryMode);
				logger.info("{} instance archive is successfully backed up to {}", repositoryMode.name().toLowerCase(), backupFile.getAbsolutePath());
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

	private RepositoryArchiveMode parseRepositoryMode(String value) {
		if ("--shallow".equals(value) || "shallow".equalsIgnoreCase(value))
			return RepositoryArchiveMode.SHALLOW;
		if ("--full".equals(value) || "full".equalsIgnoreCase(value))
			return RepositoryArchiveMode.FULL;
		throw new ExplicitException("Unsupported repository backup mode: " + value);
	}

}