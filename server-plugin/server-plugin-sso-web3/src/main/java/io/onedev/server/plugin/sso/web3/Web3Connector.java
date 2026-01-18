package io.onedev.server.plugin.sso.web3;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotEmpty;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.wicket.Session;
import org.apache.wicket.request.cycle.RequestCycle;
import org.web3j.crypto.Keys;

import io.onedev.server.OneDev;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.Multiline;
import io.onedev.server.model.support.administration.sso.SsoAuthenticated;
import io.onedev.server.model.support.administration.sso.SsoConnector;
import io.onedev.server.service.SettingService;

/**
 * SSO Connector for Web3 wallet authentication using Sign-In with Ethereum (SIWE).
 * 
 * This connector allows users to authenticate using their Ethereum wallet (e.g., MetaMask)
 * by signing a message with their private key. No external API keys or services are required.
 * 
 * @see <a href="https://eips.ethereum.org/EIPS/eip-4361">EIP-4361: Sign-In with Ethereum</a>
 */
@Editable(name="Web3 Wallet (SIWE)", order=250, 
	description="Sign in with Ethereum wallet using EIP-4361 (SIWE). " +
				"Supports MetaMask and other Web3 wallets. No API keys required.")
public class Web3Connector extends SsoConnector {

	private static final long serialVersionUID = 1L;

	private static final String SESSION_ATTR_NONCE = "web3_siwe_nonce";
	private static final String SESSION_ATTR_DOMAIN = "web3_siwe_domain";

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private String signInStatement = "Sign in to OneDev";
	
	private int nonceExpirationSeconds = 300;
	
	private String allowedChainIds = "1,137,42161,10,56";
	
	private String buttonImageUrl;
	
	private String whitelistedAddresses;
	
	private boolean disablePasswordLogin = false;

	public Web3Connector() {
		buttonImageUrl = "/wicket/resource/" + Web3Connector.class.getName() + "/wallet.svg";
	}

	/**
	 * Checks if any Web3Connector has disabled password login.
	 * This can be used by the login page to hide the password form.
	 * 
	 * @return true if password login should be disabled
	 */
	public static boolean isPasswordLoginDisabled() {
		try {
			var ssoProviderService = OneDev.getInstance(
				io.onedev.server.service.SsoProviderService.class);
			for (var provider : ssoProviderService.query()) {
				if (provider.getConnector() instanceof Web3Connector) {
					Web3Connector web3 = (Web3Connector) provider.getConnector();
					if (web3.isDisablePasswordLogin()) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			// Service not available, ignore
		}
		return false;
	}

	@Editable(order=100, description="The message shown to users when signing. " +
			"This appears in their wallet's signature request.")
	@NotEmpty
	public String getSignInStatement() {
		return signInStatement;
	}

	public void setSignInStatement(String signInStatement) {
		this.signInStatement = signInStatement;
	}

	@Editable(order=200, description="How long the authentication nonce is valid (in seconds). " +
			"Users must complete signing within this time.")
	public int getNonceExpirationSeconds() {
		return nonceExpirationSeconds;
	}

	public void setNonceExpirationSeconds(int nonceExpirationSeconds) {
		this.nonceExpirationSeconds = nonceExpirationSeconds;
	}

	@Editable(order=300, description="Comma-separated list of allowed EVM chain IDs. " +
			"Common values: 1 (Ethereum), 137 (Polygon), 42161 (Arbitrum), 10 (Optimism), 56 (BSC)")
	@NotEmpty
	public String getAllowedChainIds() {
		return allowedChainIds;
	}

	public void setAllowedChainIds(String allowedChainIds) {
		this.allowedChainIds = allowedChainIds;
	}

	@Editable(order=400, name="Whitelisted Addresses", group="Access Control",
		description="List of EVM addresses allowed to sign in (one per line). " +
			"Leave empty to allow any address. Addresses are case-insensitive.")
	@Multiline
	public String getWhitelistedAddresses() {
		return whitelistedAddresses;
	}

	public void setWhitelistedAddresses(String whitelistedAddresses) {
		this.whitelistedAddresses = whitelistedAddresses;
	}

	@Editable(order=500, name="Disable Password Login", group="Access Control",
		description="When enabled, the password login form will be hidden on the login page. " +
			"Users will only be able to authenticate via Web3 wallet. " +
			"WARNING: Make sure you have at least one admin wallet address whitelisted before enabling this!")
	public boolean isDisablePasswordLogin() {
		return disablePasswordLogin;
	}

	public void setDisablePasswordLogin(boolean disablePasswordLogin) {
		this.disablePasswordLogin = disablePasswordLogin;
	}

	@Editable(order=10000, group="More Settings", description="Image URL for the login button")
	@NotEmpty
	@Override
	public String getButtonImageUrl() {
		return buttonImageUrl;
	}

	public void setButtonImageUrl(String buttonImageUrl) {
		this.buttonImageUrl = buttonImageUrl;
	}

	/**
	 * Parses the allowed chain IDs from the configuration string.
	 */
	public List<Long> getParsedAllowedChainIds() {
		List<Long> chainIds = new ArrayList<>();
		if (allowedChainIds != null) {
			for (String id : allowedChainIds.split(",")) {
				try {
					chainIds.add(Long.parseLong(id.trim()));
				} catch (NumberFormatException e) {
					// Skip invalid chain IDs
				}
			}
		}
		return chainIds;
	}

	/**
	 * Parses the whitelisted addresses into a normalized set (lowercase).
	 * Returns empty set if no whitelist is configured.
	 */
	public Set<String> getParsedWhitelistedAddresses() {
		Set<String> addresses = new HashSet<>();
		if (whitelistedAddresses != null && !whitelistedAddresses.isBlank()) {
			for (String line : whitelistedAddresses.split("\\r?\\n")) {
				String addr = line.trim().toLowerCase();
				if (!addr.isEmpty() && addr.startsWith("0x") && addr.length() == 42) {
					addresses.add(addr);
				}
			}
		}
		return addresses;
	}

	/**
	 * Checks if an address is whitelisted. Returns true if:
	 * - No whitelist is configured (empty = allow all), OR
	 * - The address is in the whitelist
	 */
	public boolean isAddressWhitelisted(String address) {
		Set<String> whitelist = getParsedWhitelistedAddresses();
		if (whitelist.isEmpty()) {
			return true; // No whitelist = allow all
		}
		return whitelist.contains(address.toLowerCase());
	}

	@Override
	public String buildAuthUrl(String providerName) {
		// Generate a cryptographically secure nonce
		String nonce = generateNonce();
		
		// Get the server URL/domain
		String serverUrl = OneDev.getInstance(SettingService.class).getSystemSetting().getServerUrl();
		String domain = extractDomain(serverUrl);
		
		// Store nonce and domain in session for verification later
		Session.get().bind();
		Session.get().setAttribute(SESSION_ATTR_NONCE, nonce);
		Session.get().setAttribute(SESSION_ATTR_DOMAIN, domain);
		
		// Build URL to the Web3 signing page
		return serverUrl + "/~web3-signin/" + providerName 
			+ "?nonce=" + nonce 
			+ "&statement=" + urlEncode(signInStatement)
			+ "&expiration=" + nonceExpirationSeconds;
	}

	@Override
	public SsoAuthenticated handleAuthResponse(String providerName) {
		HttpServletRequest request = (HttpServletRequest) RequestCycle.get()
			.getRequest().getContainerRequest();
		
		// Get message and signature from the request
		String message = request.getParameter("message");
		String signature = request.getParameter("signature");
		
		if (message == null || message.isBlank()) {
			throw new AuthenticationException("Missing SIWE message");
		}
		if (signature == null || signature.isBlank()) {
			throw new AuthenticationException("Missing signature");
		}
		
		// Retrieve stored nonce and domain from session
		String expectedNonce = (String) Session.get().getAttribute(SESSION_ATTR_NONCE);
		String expectedDomain = (String) Session.get().getAttribute(SESSION_ATTR_DOMAIN);
		
		if (expectedNonce == null) {
			throw new AuthenticationException("No pending authentication request. Please try again.");
		}
		
		// Parse the SIWE message
		SiweMessage siwe = SiweMessage.parse(message);
		
		// Validate the message
		siwe.validate(expectedNonce, expectedDomain);
		
		// Validate chain ID
		List<Long> allowed = getParsedAllowedChainIds();
		if (!allowed.isEmpty() && !allowed.contains(siwe.getChainId())) {
			throw new AuthenticationException("Unsupported blockchain network (Chain ID: " + siwe.getChainId() + ")");
		}
		
		// Verify the signature
		if (!SiweVerifier.verify(siwe, signature)) {
			throw new AuthenticationException("Invalid signature");
		}
		
		// Get checksummed address
		String address = Keys.toChecksumAddress(siwe.getAddress());
		
		// Check whitelist
		if (!isAddressWhitelisted(address)) {
			throw new AuthenticationException("Address " + address + " is not authorized to access this system. " +
				"Please contact an administrator to request access.");
		}
		
		// Clear the nonce (single-use)
		Session.get().removeAttribute(SESSION_ATTR_NONCE);
		Session.get().removeAttribute(SESSION_ATTR_DOMAIN);
		
		// Create shortened display name (0x1234...5678)
		String shortAddress = address.substring(0, 6) + "..." + address.substring(38);
		
		// Generate a wallet-based email to satisfy the system's email requirement
		// This email is not usable for actual communication but allows account creation
		// Format: 0x1234abcd@wallet.local (using first 10 chars of address)
		String walletEmail = address.toLowerCase() + "@wallet.local";
		
		// Return the authenticated user info
		// subject = full address (unique identifier)
		// userName = shortened address for display
		// email = wallet-based email (allows direct account creation without prompts)
		// fullName = null
		// groups = null
		// sshKeys = null
		return new SsoAuthenticated(
			address,        // subject
			shortAddress,   // userName  
			walletEmail,    // email - wallet-based placeholder
			null,           // fullName
			null,           // groupNames
			null            // sshKeys
		);
	}

	/**
	 * Generates a cryptographically secure random nonce.
	 */
	private String generateNonce() {
		byte[] bytes = new byte[16];
		SECURE_RANDOM.nextBytes(bytes);
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	/**
	 * Extracts the domain (host) from a URL.
	 */
	private String extractDomain(String url) {
		try {
			URI uri = new URI(url);
			String host = uri.getHost();
			int port = uri.getPort();
			if (port != -1 && port != 80 && port != 443) {
				return host + ":" + port;
			}
			return host;
		} catch (URISyntaxException e) {
			return "localhost";
		}
	}

	/**
	 * URL-encodes a string.
	 */
	private String urlEncode(String value) {
		try {
			return java.net.URLEncoder.encode(value, "UTF-8");
		} catch (Exception e) {
			return value;
		}
	}

}
