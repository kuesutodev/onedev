package io.onedev.server.plugin.sso.solana;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotEmpty;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.wicket.Session;
import org.apache.wicket.request.cycle.RequestCycle;

import io.onedev.server.OneDev;
import io.onedev.server.annotation.ClassValidating;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.Multiline;
import io.onedev.server.model.User;
import io.onedev.server.model.support.administration.sso.SsoAccountHelper;
import io.onedev.server.model.support.administration.sso.SsoAuthenticated;
import io.onedev.server.model.support.administration.sso.SsoConnector;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.service.SettingService;
import io.onedev.server.validation.Validatable;

/**
 * SSO Connector for Solana wallet authentication using Sign-In with Solana (SIWS).
 * 
 * This connector allows users to authenticate using their Solana wallet (e.g., Phantom, Solflare)
 * by signing a message with their private key. No external API keys or services are required.
 * 
 * @see <a href="https://github.com/phantom/sign-in-with-solana">Sign-In with Solana</a>
 */
@Editable(name="Solana Wallet (SIWS)", order=260, 
	description="Sign in with Solana wallet using Sign-In with Solana (SIWS). " +
				"Supports Phantom, Solflare, and other Solana wallets. No API keys required.")
@ClassValidating
public class SolanaConnector extends SsoConnector implements Validatable {

	private static final long serialVersionUID = 1L;

	private static final String SESSION_ATTR_NONCE = "solana_siws_nonce";
	private static final String SESSION_ATTR_DOMAIN = "solana_siws_domain";

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private String signInStatement = "Sign in to GSG";
	
	private int nonceExpirationSeconds = 300;
	
	private String allowedChainIds = "mainnet";
	
	private String buttonImageUrl;
	
	private boolean enableWhitelist = false;
	
	private String whitelistedAddresses;
	
	private boolean disablePasswordLogin = false;

	public SolanaConnector() {
		buttonImageUrl = "/wicket/resource/" + SolanaConnector.class.getName() + "/solana.svg";
	}

	/**
	 * Checks if any SolanaConnector has disabled password login.
	 * This can be used by the login page to hide the password form.
	 * 
	 * @return true if password login should be disabled
	 */
	public static boolean isPasswordLoginDisabled() {
		try {
			var ssoProviderService = OneDev.getInstance(
				io.onedev.server.service.SsoProviderService.class);
			for (var provider : ssoProviderService.query()) {
				if (provider.getConnector() instanceof SolanaConnector) {
					SolanaConnector solana = (SolanaConnector) provider.getConnector();
					if (solana.isDisablePasswordLogin()) {
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

	@Editable(order=300, description="Comma-separated list of allowed Solana chain IDs, or '*' to allow any. " +
			"Valid values: mainnet, testnet, devnet, localnet")
	@NotEmpty
	public String getAllowedChainIds() {
		return allowedChainIds;
	}

	public void setAllowedChainIds(String allowedChainIds) {
		this.allowedChainIds = allowedChainIds;
	}

	@Editable(order=400, name="Enable Address Whitelist", group="Access Control",
		description="When enabled, only addresses in the whitelist below will be allowed to sign in. " +
			"When disabled, any wallet address can authenticate.")
	public boolean isEnableWhitelist() {
		return enableWhitelist;
	}

	public void setEnableWhitelist(boolean enableWhitelist) {
		this.enableWhitelist = enableWhitelist;
	}

	@Editable(order=450, name="Whitelisted Addresses", group="Access Control",
		description="List of Solana addresses allowed to sign in (one per line). " +
			"Only used when 'Enable Address Whitelist' is checked.")
	@Multiline
	public String getWhitelistedAddresses() {
		return whitelistedAddresses;
	}

	public void setWhitelistedAddresses(String whitelistedAddresses) {
		this.whitelistedAddresses = whitelistedAddresses;
	}

	@Editable(order=500, name="Disable Password Login", group="Access Control",
		description="When enabled, the password login form will be hidden on the login page. " +
			"Users will only be able to authenticate via Solana wallet. " +
			"WARNING: Make sure you have at least one admin wallet address whitelisted before enabling this!")
	public boolean isDisablePasswordLogin() {
		return disablePasswordLogin;
	}

	public void setDisablePasswordLogin(boolean disablePasswordLogin) {
		this.disablePasswordLogin = disablePasswordLogin;
	}

	@Override
	public boolean isWalletConnector() {
		return true;
	}

	@Override
	public boolean isPasswordAuthenticationDisabled() {
		return disablePasswordLogin;
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
	 * Checks if all chains are allowed (wildcard '*' is used).
	 */
	public boolean isAllChainsAllowed() {
		return allowedChainIds != null && allowedChainIds.trim().equals("*");
	}

	/**
	 * Parses the allowed chain IDs from the configuration string.
	 */
	public Set<String> getParsedAllowedChainIds() {
		Set<String> chainIds = new HashSet<>();
		if (allowedChainIds != null && !isAllChainsAllowed()) {
			for (String id : allowedChainIds.split(",")) {
				String trimmed = id.trim().toLowerCase();
				if (!trimmed.isEmpty()) {
					chainIds.add(trimmed);
				}
			}
		}
		return chainIds;
	}

	/**
	 * Parses the whitelisted addresses into a normalized set.
	 * Returns empty set if no whitelist is configured.
	 */
	public Set<String> getParsedWhitelistedAddresses() {
		Set<String> addresses = new HashSet<>();
		if (whitelistedAddresses != null && !whitelistedAddresses.isBlank()) {
			for (String line : whitelistedAddresses.split("\\r?\\n")) {
				String addr = line.trim();
				// Solana addresses are Base58, 32-44 characters
				if (!addr.isEmpty() && addr.length() >= 32 && addr.length() <= 44) {
					addresses.add(addr);
				}
			}
		}
		return addresses;
	}

	/**
	 * Checks if an address is whitelisted. Returns true if:
	 * - Whitelist is disabled (enableWhitelist = false), OR
	 * - Whitelist is enabled but empty (allow all), OR
	 * - The address is in the whitelist
	 */
	public boolean isAddressWhitelisted(String address) {
		// If whitelist is disabled, allow all addresses
		if (!enableWhitelist) {
			return true;
		}
		Set<String> whitelist = getParsedWhitelistedAddresses();
		if (whitelist.isEmpty()) {
			return true; // Empty whitelist = allow all
		}
		return whitelist.contains(address);
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
		
		// Build URL to the Solana signing page
		return serverUrl + "/~solana-signin/" + providerName 
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
		String address = request.getParameter("address");
		
		if (message == null || message.isBlank()) {
			throw new AuthenticationException("Missing SIWS message");
		}
		if (signature == null || signature.isBlank()) {
			throw new AuthenticationException("Missing signature");
		}
		if (address == null || address.isBlank()) {
			throw new AuthenticationException("Missing wallet address");
		}
		
		// Retrieve stored nonce and domain from session
		String expectedNonce = (String) Session.get().getAttribute(SESSION_ATTR_NONCE);
		String expectedDomain = (String) Session.get().getAttribute(SESSION_ATTR_DOMAIN);
		
		if (expectedNonce == null) {
			throw new AuthenticationException("No pending authentication request. Please try again.");
		}
		
		// Parse the SIWS message
		SiwsMessage siws = SiwsMessage.parse(message);
		
		// Validate the message
		siws.validate(expectedNonce, expectedDomain);
		
		// Validate chain ID (skip if wildcard '*' is used)
		if (!isAllChainsAllowed()) {
			Set<String> allowed = getParsedAllowedChainIds();
			if (!allowed.isEmpty() && !allowed.contains(siws.getChainId().toLowerCase())) {
				throw new AuthenticationException("Unsupported Solana network (Chain ID: " + siws.getChainId() + ")");
			}
		}
		
		// Verify the signature using Ed25519
		if (!SiwsVerifier.verify(message, signature, address)) {
			throw new AuthenticationException("Invalid signature");
		}
		
		// Verify that the address in message matches the claimed address
		if (!address.equals(siws.getAddress())) {
			throw new AuthenticationException("Address mismatch between message and request");
		}
		
		// Check whitelist
		if (!isAddressWhitelisted(address)) {
			throw new AuthenticationException("Address " + address + " is not authorized to access this system. " +
				"Please contact an administrator to request access.");
		}
		
		// Clear the nonce (single-use)
		Session.get().removeAttribute(SESSION_ATTR_NONCE);
		Session.get().removeAttribute(SESSION_ATTR_DOMAIN);
		
		// Create shortened display name
		String shortAddress = address.length() > 8 
			? address.substring(0, 4) + "..." + address.substring(address.length() - 4)
			: address;
		
		// Generate a wallet-based email to satisfy the system's email requirement
		// Format: address@solana.local
		String walletEmail = address + "@solana.local";
		
		// Return the authenticated user info
		return new SsoAuthenticated(
			address,        // subject (unique identifier)
			shortAddress,   // userName (display name)
			walletEmail,    // email (placeholder)
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

	/**
	 * Validates that an admin user has linked a Solana account before disabling password login.
	 */
	@Override
	public boolean isValid(ConstraintValidatorContext context) {
		if (disablePasswordLogin) {
			User currentUser = SecurityUtils.getAuthUser();
			if (currentUser == null) {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(
					"You must be logged in to enable 'Disable Password Login'")
					.addPropertyNode("disablePasswordLogin")
					.addConstraintViolation();
				return false;
			}
			
			// Check if user has a Solana SSO account linked
			boolean hasSolanaAccount = SsoAccountHelper.hasLinkedWallet(currentUser);
			
			if (!hasSolanaAccount) {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(
					"You must link your account to at least one wallet before enabling 'Disable Password Login'. " +
					"First, save this connector without the option enabled, then log in using your wallet " +
					"and link it to your account. After that, you can enable this option.")
					.addPropertyNode("disablePasswordLogin")
					.addConstraintViolation();
				return false;
			}
		}
		return true;
	}

}
