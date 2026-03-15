package io.onedev.server.product;

import static java.util.Collections.emptyList;

import java.util.List;

import javax.inject.Singleton;

import org.jspecify.annotations.Nullable;

import io.onedev.server.model.Audit;
import io.onedev.server.model.Project;
import io.onedev.server.persistence.dao.EntityCriteria;
import io.onedev.server.service.AuditService;
import io.onedev.server.service.support.AuditQuery;

@Singleton
public class ProductAuditService implements AuditService {

	@Override
	@Nullable
	public Audit get(Long entityId) {
		return null;
	}

	@Override
	public Audit load(Long entityId) {
		throw new UnsupportedOperationException("Audit entries are not available in this package build");
	}

	@Override
	public void delete(Audit entity) {
	}

	@Override
	public List<Audit> query(EntityCriteria<Audit> criteria, int firstResult, int maxResults) {
		return emptyList();
	}

	@Override
	public List<Audit> query(EntityCriteria<Audit> criteria) {
		return emptyList();
	}

	@Override
	public List<Audit> query(boolean cacheable) {
		return emptyList();
	}

	@Override
	public List<Audit> query() {
		return emptyList();
	}

	@Override
	public int count(boolean cacheable) {
		return 0;
	}

	@Override
	public int count() {
		return 0;
	}

	@Override
	public EntityCriteria<Audit> newCriteria() {
		return EntityCriteria.of(Audit.class);
	}

	@Override
	@Nullable
	public Audit find(EntityCriteria<Audit> entityCriteria) {
		return null;
	}

	@Override
	public int count(EntityCriteria<Audit> entityCriteria) {
		return 0;
	}

	@Override
	public void audit(@Nullable Project project, String action, @Nullable String oldContent, @Nullable String newContent) {
	}

	@Override
	public List<Audit> query(@Nullable Project project, AuditQuery query, int firstResult, int maxResults) {
		return emptyList();
	}

	@Override
	public int count(@Nullable Project project, AuditQuery query) {
		return 0;
	}

}