package io.onedev.server.plugin.sso.solana;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;

/**
 * Behavior that injects CSS to hide the password login form when Solana connector
 * has the "Disable Password Login" option enabled.
 */
public class HidePasswordLoginBehavior extends Behavior {

	private static final long serialVersionUID = 1L;

	@Override
	public void renderHead(Component component, IHeaderResponse response) {
		super.renderHead(component, response);
		
		if (SolanaConnector.isPasswordLoginDisabled()) {
			// Hide the password form and related elements on the login page
			String css = """
				/* Hide password login form when Solana-only mode is enabled */
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
					content: "Password login is disabled. Please use Solana wallet to sign in.";
					display: block;
					padding: 12px 16px;
					margin-bottom: 20px;
					background: linear-gradient(135deg, rgba(153,69,255,0.1) 0%, rgba(20,241,149,0.1) 100%);
					border: 1px solid #9945FF;
					border-radius: 6px;
					color: #9945FF;
					text-align: center;
					font-size: 14px;
				}
			""";
			response.render(CssHeaderItem.forCSS(css, "solana-hide-password-login"));
		}
	}
}
