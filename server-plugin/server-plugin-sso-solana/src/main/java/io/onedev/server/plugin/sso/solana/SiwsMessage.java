package io.onedev.server.plugin.sso.solana;

import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.shiro.authc.AuthenticationException;

/**
 * Represents a Sign-In with Solana (SIWS) message.
 * 
 * Format follows the SIWS specification modeled after EIP-4361:
 * <pre>
 * ${domain} wants you to sign in with your Solana account:
 * ${address}
 * 
 * ${statement}
 * 
 * URI: ${uri}
 * Version: ${version}
 * Chain ID: ${chainId}
 * Nonce: ${nonce}
 * Issued At: ${issuedAt}
 * [Expiration Time: ${expirationTime}]
 * </pre>
 * 
 * @see <a href="https://github.com/phantom/sign-in-with-solana">Sign-In with Solana</a>
 */
public class SiwsMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	// Regex pattern for parsing SIWS messages
	private static final Pattern SIWS_PATTERN = Pattern.compile(
		"^(?<domain>[^\\s]+) wants you to sign in with your Solana account:\\s*" +
		"(?<address>[1-9A-HJ-NP-Za-km-z]{32,44})\\s+" +
		"(?<statement>.*?)\\s+" +
		"URI: (?<uri>[^\\s]+)\\s+" +
		"Version: (?<version>\\d+)\\s+" +
		"Chain ID: (?<chainId>[a-zA-Z:]+)\\s+" +
		"Nonce: (?<nonce>[a-zA-Z0-9]+)\\s+" +
		"Issued At: (?<issuedAt>[^\\n]+)" +
		"(?:\\s+Expiration Time: (?<expirationTime>[^\\n]+))?\\s*$",
		Pattern.DOTALL
	);

	private String domain;
	private String address;
	private String statement;
	private String uri;
	private String version;
	private String chainId;
	private String nonce;
	private Instant issuedAt;
	private Instant expirationTime;

	public SiwsMessage() {
	}

	/**
	 * Creates a new SIWS message with the given parameters.
	 */
	public SiwsMessage(String domain, String address, String statement, String uri,
					   String version, String chainId, String nonce, 
					   Instant issuedAt, Instant expirationTime) {
		this.domain = domain;
		this.address = address;
		this.statement = statement;
		this.uri = uri;
		this.version = version;
		this.chainId = chainId;
		this.nonce = nonce;
		this.issuedAt = issuedAt;
		this.expirationTime = expirationTime;
	}

	/**
	 * Parses a SIWS message string into a SiwsMessage object.
	 */
	public static SiwsMessage parse(String message) throws AuthenticationException {
		if (message == null || message.isBlank()) {
			throw new AuthenticationException("SIWS message cannot be empty");
		}

		Matcher matcher = SIWS_PATTERN.matcher(message.trim());
		if (!matcher.matches()) {
			throw new AuthenticationException("Invalid SIWS message format");
		}

		try {
			SiwsMessage siws = new SiwsMessage();
			siws.domain = matcher.group("domain");
			siws.address = matcher.group("address");
			siws.statement = matcher.group("statement");
			siws.uri = matcher.group("uri");
			siws.version = matcher.group("version");
			siws.chainId = matcher.group("chainId");
			siws.nonce = matcher.group("nonce");
			siws.issuedAt = parseTimestamp(matcher.group("issuedAt"));
			
			String expTime = matcher.group("expirationTime");
			if (expTime != null && !expTime.isBlank()) {
				siws.expirationTime = parseTimestamp(expTime);
			}

			return siws;
		} catch (Exception e) {
			throw new AuthenticationException("Failed to parse SIWS message: " + e.getMessage());
		}
	}

	private static Instant parseTimestamp(String timestamp) throws AuthenticationException {
		try {
			return Instant.parse(timestamp.trim());
		} catch (DateTimeParseException e) {
			throw new AuthenticationException("Invalid timestamp format in SIWS message: " + timestamp);
		}
	}

	/**
	 * Converts this SIWS message to its string representation for signing.
	 */
	public String toMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(domain).append(" wants you to sign in with your Solana account:\n");
		sb.append(address).append("\n\n");
		sb.append(statement).append("\n\n");
		sb.append("URI: ").append(uri).append("\n");
		sb.append("Version: ").append(version).append("\n");
		sb.append("Chain ID: ").append(chainId).append("\n");
		sb.append("Nonce: ").append(nonce).append("\n");
		sb.append("Issued At: ").append(issuedAt.toString());
		
		if (expirationTime != null) {
			sb.append("\nExpiration Time: ").append(expirationTime.toString());
		}
		
		return sb.toString();
	}

	/**
	 * Validates the SIWS message against expected values.
	 */
	public void validate(String expectedNonce, String expectedDomain) throws AuthenticationException {
		// Validate nonce
		if (expectedNonce == null || !expectedNonce.equals(this.nonce)) {
			throw new AuthenticationException("Invalid nonce");
		}

		// Validate domain
		if (expectedDomain != null && !expectedDomain.equalsIgnoreCase(this.domain)) {
			throw new AuthenticationException("Domain mismatch");
		}

		// Validate version
		if (!"1".equals(this.version)) {
			throw new AuthenticationException("Unsupported SIWS version: " + this.version);
		}

		// Validate address format (Base58, 32-44 chars)
		if (!isValidAddress(this.address)) {
			throw new AuthenticationException("Invalid Solana address format");
		}

		// Validate timestamps
		Instant now = Instant.now();
		
		if (this.issuedAt != null && this.issuedAt.isAfter(now.plusSeconds(60))) {
			throw new AuthenticationException("Message issued in the future");
		}

		if (this.expirationTime != null && now.isAfter(this.expirationTime)) {
			throw new AuthenticationException("Message has expired");
		}
	}

	/**
	 * Validates that the address is a valid Solana address format (Base58, 32-44 chars).
	 */
	private static boolean isValidAddress(String address) {
		if (address == null || address.length() < 32 || address.length() > 44) {
			return false;
		}
		// Base58 character set (no 0, O, I, l)
		return address.matches("^[1-9A-HJ-NP-Za-km-z]+$");
	}

	// Getters

	public String getDomain() {
		return domain;
	}

	public String getAddress() {
		return address;
	}

	public String getStatement() {
		return statement;
	}

	public String getUri() {
		return uri;
	}

	public String getVersion() {
		return version;
	}

	public String getChainId() {
		return chainId;
	}

	public String getNonce() {
		return nonce;
	}

	public Instant getIssuedAt() {
		return issuedAt;
	}

	public Instant getExpirationTime() {
		return expirationTime;
	}

	// Setters

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public void setStatement(String statement) {
		this.statement = statement;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public void setChainId(String chainId) {
		this.chainId = chainId;
	}

	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

	public void setIssuedAt(Instant issuedAt) {
		this.issuedAt = issuedAt;
	}

	public void setExpirationTime(Instant expirationTime) {
		this.expirationTime = expirationTime;
	}

}
