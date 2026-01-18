package io.onedev.server.plugin.sso.evm;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;

/**
 * Behavior that injects CSS to hide the password login form when Web3 connector
 * has the "Disable Password Login" option enabled.
 */
public class HidePasswordLoginBehavior extends Behavior {

	private static final long serialVersionUID = 1L;

	@Override
	public void renderHead(Component component, IHeaderResponse response) {
		super.renderHead(component, response);
		
		if (EvmConnector.isPasswordLoginDisabled()) {
			// Hide the password form and related elements on the login page
			// Based on LoginPage.html structure: form with password input, signup div, forgetPassword link
			String css = """
				/* Hide password login form when Web3-only mode is enabled */
				/* Hide the form containing password input */
				form:has(input[type="password"]) {
					display: none !important;
				}
				
				/* Hide the signup section */
				.signup {
					display: none !important;
				}
				
				/* Move SSO section up and add a message */
				.sso {
					margin-top: 0 !important;
				}
				
				.sso::before {
					content: "Password login is disabled. Please use EVM wallet to sign in.";
					display: block;
					padding: 12px 16px;
					margin-bottom: 20px;
					background: #e8f4fc;
					border: 1px solid #3699ff;
					border-radius: 6px;
					color: #0d6efd;
					text-align: center;
					font-size: 14px;
				}
			""";
			response.render(CssHeaderItem.forCSS(css, "web3-hide-password-login"));
		}
	}
}
