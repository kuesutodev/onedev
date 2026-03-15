package io.onedev.server.product;

import static io.onedev.server.web.translation.Translation._T;

import java.util.List;

import javax.inject.Singleton;

import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.web.page.layout.MainMenuCustomization;
import io.onedev.server.web.page.layout.SidebarMenuItem;
import io.onedev.server.web.page.project.ProjectListPage;

@Singleton
public class ProductMainMenuCustomization implements MainMenuCustomization {

	private static final long serialVersionUID = 1L;

	@Override
	public PageProvider getHomePage(boolean failsafe) {
		return new PageProvider(ProjectListPage.class, new PageParameters());
	}

	@Override
	public List<SidebarMenuItem> getMainMenuItems() {
		return List.of(new SidebarMenuItem.Page(null, _T("Projects"), ProjectListPage.class, new PageParameters()));
	}

}