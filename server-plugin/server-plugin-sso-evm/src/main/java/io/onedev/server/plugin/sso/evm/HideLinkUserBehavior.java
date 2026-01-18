package io.onedev.server.plugin.sso.evm;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;

import io.onedev.server.OneDev;
import io.onedev.server.service.SsoProviderService;
import io.onedev.server.web.page.security.SsoProcessPage;

/**
 * Behavior that injects CSS to hide the "Link Existing User" tab on the SSO process page
 * when the user is signing in via EVM wallet.
 */
public class HideLinkUserBehavior extends Behavior {

	private static final long serialVersionUID = 1L;

	@Override
	public void renderHead(Component component, IHeaderResponse response) {
		super.renderHead(component, response);
		
		// Check if this is a EVM SSO provider by examining the page parameters
		if (component instanceof SsoProcessPage) {
			SsoProcessPage page = (SsoProcessPage) component;
			String providerName = page.getPageParameters().get("provider").toOptionalString();
			
			if (providerName != null) {
				try {
					var provider = OneDev.getInstance(SsoProviderService.class).find(providerName);
					if (provider != null && provider.getConnector() instanceof EvmConnector) {
						// Hide the entire tabs section since we only have one option
						String css = """
							/* Hide entire tabs for EVM SSO - only one option */
							ul.tabs.nav.nav-tabs,
							ul.tabs {
								display: none !important;
							}
						""";
						response.render(CssHeaderItem.forCSS(css, "web3-hide-link-user-css"));
					}
				} catch (Exception e) {
					// Provider not found or error, skip hiding
				}
			}
		}
	}
}
