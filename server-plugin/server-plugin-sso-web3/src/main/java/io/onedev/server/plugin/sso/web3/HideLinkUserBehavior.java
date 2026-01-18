package io.onedev.server.plugin.sso.web3;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;

/**
 * Behavior that injects CSS to hide the "Link Existing User" tab on the SSO process page
 * when the user is signing in via Web3 wallet.
 */
public class HideLinkUserBehavior extends Behavior {

	private static final long serialVersionUID = 1L;

	@Override
	public void renderHead(Component component, IHeaderResponse response) {
		super.renderHead(component, response);
		
		// Hide the "Link Existing User" tab (second tab) on the SSO process page
		String css = """
			/* Hide the Link Existing User tab for Web3 SSO */
			ul.nav-tabs > li:nth-child(2),
			.nav-tabs > li:nth-child(2),
			ul.tabs > li:nth-child(2) {
				display: none !important;
			}
		""";
		response.render(CssHeaderItem.forCSS(css, "web3-hide-link-user"));
	}
}
