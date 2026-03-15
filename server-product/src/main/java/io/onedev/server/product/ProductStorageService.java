package io.onedev.server.product;

import static io.onedev.server.model.Build.ARTIFACTS_DIR;
import static io.onedev.server.model.PackBlob.PACKS_DIR;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.onedev.server.StorageService;
import io.onedev.server.model.Build;
import io.onedev.server.service.ProjectService;

@Singleton
public class ProductStorageService implements StorageService {

	private final ProjectService projectService;

	@Inject
	public ProductStorageService(ProjectService projectService) {
		this.projectService = projectService;
	}

	@Override
	public File initLfsDir(Long projectId) {
		return projectService.getSubDir(projectId, "git/lfs/objects");
	}

	@Override
	public File initArtifactsDir(Long projectId, Long buildNumber) {
		return projectService.getSubDir(projectId,
				Build.getProjectRelativeDirPath(buildNumber) + "/" + ARTIFACTS_DIR);
	}

	@Override
	public File initPacksDir(Long projectId) {
		return projectService.getSubDir(projectId, PACKS_DIR);
	}

}