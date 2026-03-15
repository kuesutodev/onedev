package io.onedev.server.web.component.brandlogo;

import io.onedev.commons.bootstrap.Bootstrap;
import io.onedev.server.web.component.svg.SpriteImage;
import io.onedev.server.web.page.base.BasePage;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.image.ExternalImage;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.LoadableDetachableModel;

import org.jspecify.annotations.Nullable;
import java.io.File;
import java.io.Serializable;

public class BrandLogoPanel extends Panel {
	
	private final Boolean darkMode;
	
	public BrandLogoPanel(String id, @Nullable Boolean darkMode) {
		super(id);
		this.darkMode = darkMode;
	}

	public BrandLogoPanel(String id) {
		this(id, null);
	}
	
	private File getCustomLogoFile() {
		var assetsDir = new File(Bootstrap.getSiteDir(), "assets");
		if (isDarkMode()) {
			var svgFile = new File(assetsDir, "logo-dark.svg");
			if (svgFile.exists())
				return svgFile;
			return new File(assetsDir, "logo-dark.png");
		} else {
			var svgFile = new File(assetsDir, "logo.svg");
			if (svgFile.exists())
				return svgFile;
			return new File(assetsDir, "logo.png");
		}
	}
	
	private boolean isDarkMode() {
		if (darkMode != null)
			return darkMode;
		else
			return ((BasePage) getPage()).isDarkMode();
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		add(new ExternalImage("custom", new LoadableDetachableModel<>() {
			@Override
			protected Serializable load() {
				return "/" + getCustomLogoFile().getName() + "?v=" + getCustomLogoFile().lastModified();
			}

		}) {
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(getCustomLogoFile().exists());
			}
			
		});
		add(new SpriteImage("default", "logo") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!getCustomLogoFile().exists());
			}
			
		});

		setRenderBodyOnly(true);
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(CssHeaderItem.forReference(new BrandLogoCssResourceReference()));
	}

}
