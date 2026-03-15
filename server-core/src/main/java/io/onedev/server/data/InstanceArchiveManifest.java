package io.onedev.server.data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class InstanceArchiveManifest implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final int CURRENT_FORMAT_VERSION = 1;

	private int formatVersion = CURRENT_FORMAT_VERSION;

	private String type = "full-instance-archive";

	private String version;

	private long createdAt;

	private boolean databaseIncluded = true;

	private boolean siteIncluded = true;

	private RepositoryArchiveMode repositoryMode = RepositoryArchiveMode.FULL;

	private Map<String, String> segmentChecksums = new LinkedHashMap<>();

	public int getFormatVersion() {
		return formatVersion;
	}

	public void setFormatVersion(int formatVersion) {
		this.formatVersion = formatVersion;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}

	public boolean isDatabaseIncluded() {
		return databaseIncluded;
	}

	public void setDatabaseIncluded(boolean databaseIncluded) {
		this.databaseIncluded = databaseIncluded;
	}

	public boolean isSiteIncluded() {
		return siteIncluded;
	}

	public void setSiteIncluded(boolean siteIncluded) {
		this.siteIncluded = siteIncluded;
	}

	public RepositoryArchiveMode getRepositoryMode() {
		return repositoryMode;
	}

	public void setRepositoryMode(RepositoryArchiveMode repositoryMode) {
		this.repositoryMode = repositoryMode;
	}

	public Map<String, String> getSegmentChecksums() {
		return segmentChecksums;
	}

	public void setSegmentChecksums(Map<String, String> segmentChecksums) {
		this.segmentChecksums = segmentChecksums;
	}

}