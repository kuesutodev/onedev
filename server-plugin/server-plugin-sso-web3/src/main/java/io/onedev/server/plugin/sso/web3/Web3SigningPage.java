package io.onedev.server.plugin.sso.web3;

import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.OneDev;
import io.onedev.server.service.SettingService;
import io.onedev.server.web.page.security.SsoProcessPage;
import io.onedev.server.web.page.simple.SimplePage;

/**
 * Wicket page that handles Web3 wallet connection and message signing.
 * 
 * This page is displayed when a user initiates Web3 authentication.
 * It uses JavaScript to connect to the user's wallet and sign the SIWE message.
 */
public class Web3SigningPage extends SimplePage {

	private static final long serialVersionUID = 1L;

	private final String providerName;
	private final String nonce;
	private final String statement;
	private final int expiration;
	private final String callbackUrl;
	private final String domain;
	private final String uri;

	public Web3SigningPage(PageParameters params) {
		super(params);
		
		this.providerName = params.get("provider").toString();
		this.nonce = params.get("nonce").toString();
		this.statement = params.get("statement").toOptionalString();
		this.expiration = params.get("expiration").toInt(300);
		
		// Build callback URL
		String serverUrl = OneDev.getInstance(SettingService.class).getSystemSetting().getServerUrl();
		this.callbackUrl = serverUrl + "/" + SsoProcessPage.MOUNT_PATH + "/" 
			+ SsoProcessPage.STAGE_CALLBACK + "/" + providerName;
		
		// Extract domain from server URL
		this.uri = serverUrl;
		this.domain = extractDomain(serverUrl);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		// Add Wicket components for the template
		add(new Label("nonce", getNonce()));
		add(new Label("statement", getStatement()));
		add(new Label("domain", getDomain()));
		add(new Label("uri", getUri()));
		add(new Label("callbackUrl", getCallbackUrl()));
		add(new Label("expiration", String.valueOf(getExpiration())));
		
		// Add the callback form (action will be set by JavaScript)
		Form<Void> callbackForm = new Form<>("callbackForm");
		add(callbackForm);
	}

	private String extractDomain(String url) {
		try {
			java.net.URI uri = new java.net.URI(url);
			String host = uri.getHost();
			int port = uri.getPort();
			if (port != -1 && port != 80 && port != 443) {
				return host + ":" + port;
			}
			return host;
		} catch (Exception e) {
			return "localhost";
		}
	}

	/**
	 * Returns the nonce for use in the HTML template.
	 */
	public String getNonce() {
		return nonce;
	}

	/**
	 * Returns the statement for use in the HTML template.
	 */
	public String getStatement() {
		return statement != null ? statement : "Sign in to OneDev";
	}

	/**
	 * Returns the expiration time in seconds.
	 */
	public int getExpiration() {
		return expiration;
	}

	/**
	 * Returns the callback URL for form submission.
	 */
	public String getCallbackUrl() {
		return callbackUrl;
	}

	/**
	 * Returns the domain for the SIWE message.
	 */
	public String getDomain() {
		return domain;
	}

	/**
	 * Returns the URI for the SIWE message.
	 */
	public String getUri() {
		return uri;
	}

	@Override
	protected String getTitle() {
		return "Sign In with Ethereum";
	}

	@Override
	protected String getSubTitle() {
		return "Connect your wallet to authenticate";
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		
		// Add ethers.js from unpkg CDN (more reliable)
		response.render(JavaScriptHeaderItem.forUrl(
			"https://unpkg.com/ethers@5.7.2/dist/ethers.umd.min.js", "ethers-js"));
		
		// Add basic styles
		response.render(CssHeaderItem.forCSS(getStyles(), "web3-signin-styles"));
	}

	private String getStyles() {
		return """
			body {
				font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
				background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
				min-height: 100vh;
				margin: 0;
				display: flex;
				align-items: center;
				justify-content: center;
			}
			.signin-container {
				background: #fff;
				padding: 40px;
				border-radius: 16px;
				box-shadow: 0 10px 40px rgba(0,0,0,0.3);
				max-width: 420px;
				width: 90%;
				text-align: center;
			}
			.logo {
				font-size: 48px;
				margin-bottom: 20px;
			}
			h2 {
				color: #1a1a2e;
				margin: 0 0 10px 0;
				font-size: 24px;
			}
			.subtitle {
				color: #666;
				margin-bottom: 30px;
			}
			.wallet-btn {
				display: inline-flex;
				align-items: center;
				justify-content: center;
				gap: 10px;
				padding: 14px 28px;
				font-size: 16px;
				font-weight: 600;
				cursor: pointer;
				background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
				color: white;
				border: none;
				border-radius: 12px;
				transition: transform 0.2s, box-shadow 0.2s;
				width: 100%;
			}
			.wallet-btn:hover {
				transform: translateY(-2px);
				box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
			}
			.wallet-btn:disabled {
				background: #ccc;
				cursor: not-allowed;
				transform: none;
				box-shadow: none;
			}
			.wallet-btn .icon {
				font-size: 24px;
			}
			.status {
				margin-top: 20px;
				padding: 12px;
				border-radius: 8px;
				font-size: 14px;
			}
			.status.info {
				background: #e3f2fd;
				color: #1565c0;
			}
			.status.error {
				background: #ffebee;
				color: #c62828;
			}
			.status.success {
				background: #e8f5e9;
				color: #2e7d32;
			}
			.hidden {
				display: none;
			}
			.address-display {
				margin-top: 15px;
				padding: 10px;
				background: #f5f5f5;
				border-radius: 8px;
				font-family: monospace;
				font-size: 14px;
				word-break: break-all;
			}
			.cancel-link {
				display: block;
				margin-top: 20px;
				color: #666;
				text-decoration: none;
			}
			.cancel-link:hover {
				text-decoration: underline;
			}
		""";
	}

}
