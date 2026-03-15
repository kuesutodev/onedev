package io.onedev.server.product;

import com.google.inject.multibindings.Multibinder;
import io.onedev.commons.loader.AbstractPluginModule;
import io.onedev.server.ai.ChatService;
import io.onedev.server.ServerConfig;
import io.onedev.server.StorageService;
import io.onedev.server.SubscriptionService;
import io.onedev.server.cluster.ClusterService;
import io.onedev.server.jetty.ServerConfigurator;
import io.onedev.server.jetty.ServletConfigurator;
import io.onedev.server.persistence.HibernateConfig;
import io.onedev.server.service.AuditService;
import io.onedev.server.util.ProjectNameReservation;
import io.onedev.server.web.page.layout.AdministrationMenuContribution;
import io.onedev.server.web.page.layout.MainMenuCustomization;

import java.util.HashSet;
import java.util.Set;

import static io.onedev.commons.bootstrap.Bootstrap.installDir;
import static io.onedev.server.OneDev.getAssetsDir;

public class ProductModule extends AbstractPluginModule {

    @Override
	protected void configure() {
		super.configure();
		
		bind(HibernateConfig.class).toInstance(new HibernateConfig(installDir));
		bind(ServerConfig.class).toInstance(new ServerConfig(installDir));
		bind(ClusterService.class).to(ProductClusterService.class);
		bind(StorageService.class).to(ProductStorageService.class);
		bind(SubscriptionService.class).to(ProductSubscriptionService.class);
		bind(AuditService.class).to(ProductAuditService.class);
		bind(ChatService.class).to(ProductChatService.class);
		bind(MainMenuCustomization.class).to(ProductMainMenuCustomization.class);
		Multibinder.newSetBinder(binder(), AdministrationMenuContribution.class);

		contribute(ServerConfigurator.class, ProductConfigurator.class);
		contribute(ServletConfigurator.class, ProductServletConfigurator.class);
		
		contribute(ProjectNameReservation.class, () -> {
			Set<String> reserved = new HashSet<>();
			for (var file : getAssetsDir().listFiles())
				reserved.add(file.getName());
			return reserved;
		});
		
	}

}
