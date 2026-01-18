package io.onedev.server.plugin.sso.evm;

import java.util.Collection;

import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.application.IComponentInstantiationListener;

import com.google.common.collect.Sets;

import io.onedev.commons.loader.AbstractPluginModule;
import io.onedev.commons.loader.ImplementationProvider;
import io.onedev.server.model.support.administration.sso.SsoConnector;
import io.onedev.server.web.WebApplicationConfigurator;
import io.onedev.server.web.page.security.LoginPage;
import io.onedev.server.web.page.security.SsoProcessPage;

/**
 * Guice module for EVM wallet authentication plugin.
 * Registers EvmConnector as an SsoConnector implementation and mounts the signing page.
 */
public class EvmModule extends AbstractPluginModule {

	@Override
	protected void configure() {
		super.configure();
		
		// Register EvmConnector as an SSO connector implementation
		contribute(ImplementationProvider.class, new ImplementationProvider() {

			@Override
			public Class<?> getAbstractClass() {
				return SsoConnector.class;
			}

			@Override
			public Collection<Class<?>> getImplementations() {
				return Sets.newHashSet(EvmConnector.class);
			}
			
		});
		
		// Register page mount configurator and add behavior to hide password login
		contribute(WebApplicationConfigurator.class, application -> {
			application.mountPage("/~evm-signin/${provider}", EvmSigningPage.class);
			
			// Add listener to inject behaviors on pages
			application.getComponentInstantiationListeners().add(new IComponentInstantiationListener() {
				@Override
				public void onInstantiation(Component component) {
					// Add the behavior to the LoginPage to hide password form when configured
					if (component instanceof LoginPage) {
						component.add(new HidePasswordLoginBehavior());
					}
					// Add the behavior to SsoProcessPage to hide "Link Existing User" tab
					if (component instanceof SsoProcessPage) {
						component.add(new HideLinkUserBehavior());
					}
				}
			});
		});
	}

}
