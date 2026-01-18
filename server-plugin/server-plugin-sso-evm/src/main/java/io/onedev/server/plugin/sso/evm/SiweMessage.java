package io.onedev.server.plugin.sso.evm;

import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.shiro.authc.AuthenticationException;

/**
 * Represents a Sign-In with Ethereum (SIWE) message following EIP-4361.
 * 
 * @see <a href="https://eips.ethereum.org/EIPS/eip-4361">EIP-4361: Sign-In with Ethereum</a>
 */
public class SiweMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	// Regex pattern for parsing SIWE messages
	// Made more flexible to handle different line endings and optional fields
	private static final Pattern SIWE_PATTERN = Pattern.compile(
		"^(?<domain>[^\\s]+) wants you to sign in with your Ethereum account:\\s*" +
		"(?<address>0x[a-fA-F0-9]{40})\\s+" +
		"(?<statement>.*?)\\s+" +
		"URI: (?<uri>[^\\s]+)\\s+" +
		"Version: (?<version>\\d+)\\s+" +
		"Chain ID: (?<chainId>\\d+)\\s+" +
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
	private long chainId;
	private String nonce;
	private Instant issuedAt;
	private Instant expirationTime;

	public SiweMessage() {
	}

	/**
	 * Creates a new SIWE message with the given parameters.
	 */
	public SiweMessage(String domain, String address, String statement, String uri,
					   String version, long chainId, String nonce, 
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
	 * Parses a SIWE message string into a SiweMessage object.
	 * 
	 * @param message The raw SIWE message string
	 * @return Parsed SiweMessage object
	 * @throws AuthenticationException if the message format is invalid
	 */
	public static SiweMessage parse(String message) throws AuthenticationException {
		if (message == null || message.isBlank()) {
			throw new AuthenticationException("SIWE message cannot be empty");
		}

		Matcher matcher = SIWE_PATTERN.matcher(message.trim());
		if (!matcher.matches()) {
			throw new AuthenticationException("Invalid SIWE message format");
		}

		try {
			SiweMessage siwe = new SiweMessage();
			siwe.domain = matcher.group("domain");
			siwe.address = matcher.group("address");
			siwe.statement = matcher.group("statement");
			siwe.uri = matcher.group("uri");
			siwe.version = matcher.group("version");
			siwe.chainId = Long.parseLong(matcher.group("chainId"));
			siwe.nonce = matcher.group("nonce");
			siwe.issuedAt = parseTimestamp(matcher.group("issuedAt"));
			
			String expTime = matcher.group("expirationTime");
			if (expTime != null && !expTime.isBlank()) {
				siwe.expirationTime = parseTimestamp(expTime);
			}

			return siwe;
		} catch (NumberFormatException e) {
			throw new AuthenticationException("Invalid chain ID in SIWE message");
		}
	}

	private static Instant parseTimestamp(String timestamp) throws AuthenticationException {
		try {
			return Instant.parse(timestamp.trim());
		} catch (DateTimeParseException e) {
			throw new AuthenticationException("Invalid timestamp format in SIWE message: " + timestamp);
		}
	}

	/**
	 * Converts this SIWE message to its string representation for signing.
	 */
	public String toMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(domain).append(" wants you to sign in with your Ethereum account:\n");
		sb.append(address).append("\n\n");
		sb.append(statement).append("\n\n");
		sb.append("URI: ").append(uri).append("\n");
		sb.append("Version: ").append(version).append("\n");
		sb.append("Chain ID: ").append(chainId).append("\n");
		sb.append("Nonce: ").append(nonce).append("\n");
		sb.append("Issued At: ").append(DateTimeFormatter.ISO_INSTANT.format(issuedAt));
		
		if (expirationTime != null) {
			sb.append("\nExpiration Time: ").append(DateTimeFormatter.ISO_INSTANT.format(expirationTime));
		}
		
		return sb.toString();
	}

	/**
	 * Validates the SIWE message against expected values.
	 * 
	 * @param expectedNonce The nonce that was issued by the server
	 * @param expectedDomain The expected domain (server hostname)
	 * @throws AuthenticationException if validation fails
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
			throw new AuthenticationException("Unsupported SIWE version: " + this.version);
		}

		// Validate address format
		if (!isValidAddress(this.address)) {
			throw new AuthenticationException("Invalid Ethereum address format");
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
	 * Validates that the address is a valid Ethereum address format.
	 */
	private static boolean isValidAddress(String address) {
		return address != null && address.matches("^0x[a-fA-F0-9]{40}$");
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

	public long getChainId() {
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

	public void setChainId(long chainId) {
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
