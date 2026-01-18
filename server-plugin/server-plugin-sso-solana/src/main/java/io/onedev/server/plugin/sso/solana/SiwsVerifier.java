package io.onedev.server.plugin.sso.solana;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

/**
 * Verifies Solana Ed25519 signatures for Sign-In with Solana (SIWS).
 * 
 * Solana uses Ed25519 signatures, unlike Ethereum which uses ECDSA.
 * This implementation uses BouncyCastle for cryptographic operations.
 */
public class SiwsVerifier {

	// Base58 alphabet used by Solana
	private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
	private static final int[] BASE58_INDEXES = new int[128];
	
	static {
		Arrays.fill(BASE58_INDEXES, -1);
		for (int i = 0; i < BASE58_ALPHABET.length(); i++) {
			BASE58_INDEXES[BASE58_ALPHABET.charAt(i)] = i;
		}
	}

	/**
	 * Verifies that the signature was produced by signing the given message
	 * with the private key corresponding to the claimed Solana address.
	 * 
	 * @param message The raw message that was signed
	 * @param signatureBase58 The Base58-encoded Ed25519 signature
	 * @param addressBase58 The Base58-encoded Solana public key (address)
	 * @return true if the signature is valid, false otherwise
	 */
	public static boolean verify(String message, String signatureBase58, String addressBase58) {
		try {
			// Normalize line endings - browser form submission may convert \n to \r\n
			// The wallet signed the message with \n, so we need to convert back
			String normalizedMessage = message.replace("\r\n", "\n");
			
			// Decode the Base58-encoded public key
			byte[] publicKeyBytes = decodeBase58(addressBase58);
			if (publicKeyBytes == null || publicKeyBytes.length != 32) {
				return false;
			}
			
			// Decode the Base58-encoded signature
			byte[] signatureBytes = decodeBase58(signatureBase58);
			if (signatureBytes == null || signatureBytes.length != 64) {
				return false;
			}
			
			// Get the message bytes (use normalized message)
			byte[] messageBytes = normalizedMessage.getBytes(StandardCharsets.UTF_8);
			
			// Create Ed25519 verifier
			Ed25519PublicKeyParameters publicKey = new Ed25519PublicKeyParameters(publicKeyBytes, 0);
			Ed25519Signer verifier = new Ed25519Signer();
			verifier.init(false, publicKey);
			
			// Verify the signature
			verifier.update(messageBytes, 0, messageBytes.length);
			return verifier.verifySignature(signatureBytes);
			
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Decodes a Base58-encoded string to bytes.
	 * This is the format used by Solana for addresses and signatures.
	 * 
	 * @param input Base58-encoded string
	 * @return Decoded bytes, or null if invalid
	 */
	public static byte[] decodeBase58(String input) {
		if (input == null || input.isEmpty()) {
			return null;
		}
		
		// Count leading zeros
		int leadingZeros = 0;
		for (int i = 0; i < input.length() && input.charAt(i) == '1'; i++) {
			leadingZeros++;
		}
		
		// Allocate enough space for decoded bytes
		byte[] decoded = new byte[input.length()];
		int outputStart = decoded.length;
		
		for (int i = leadingZeros; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c >= 128 || BASE58_INDEXES[c] == -1) {
				return null; // Invalid character
			}
			
			int digit = BASE58_INDEXES[c];
			int carry = digit;
			
			for (int j = decoded.length - 1; j >= outputStart || carry != 0; j--) {
				if (j < 0) {
					return null; // Number too large
				}
				carry += 58 * (decoded[j] & 0xFF);
				decoded[j] = (byte) (carry & 0xFF);
				carry >>= 8;
				if (j < outputStart) {
					outputStart = j;
				}
			}
		}
		
		// Skip leading zeros in decoded
		while (outputStart < decoded.length && decoded[outputStart] == 0) {
			outputStart++;
		}
		
		// Build result with leading zeros
		byte[] result = new byte[leadingZeros + (decoded.length - outputStart)];
		Arrays.fill(result, 0, leadingZeros, (byte) 0);
		System.arraycopy(decoded, outputStart, result, leadingZeros, decoded.length - outputStart);
		
		return result;
	}

	/**
	 * Encodes bytes to a Base58 string.
	 * 
	 * @param input Bytes to encode
	 * @return Base58-encoded string
	 */
	public static String encodeBase58(byte[] input) {
		if (input == null || input.length == 0) {
			return "";
		}
		
		// Count leading zeros
		int leadingZeros = 0;
		for (int i = 0; i < input.length && input[i] == 0; i++) {
			leadingZeros++;
		}
		
		// Allocate enough space for encoded string
		byte[] encoded = new byte[input.length * 2];
		int outputStart = encoded.length;
		
		for (int i = leadingZeros; i < input.length; i++) {
			int carry = input[i] & 0xFF;
			
			for (int j = encoded.length - 1; j >= outputStart || carry != 0; j--) {
				if (j < 0) {
					break;
				}
				carry += 256 * (encoded[j] & 0xFF);
				encoded[j] = (byte) (carry % 58);
				carry /= 58;
				if (j < outputStart) {
					outputStart = j;
				}
			}
		}
		
		// Build result string
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < leadingZeros; i++) {
			sb.append('1');
		}
		for (int i = outputStart; i < encoded.length; i++) {
			sb.append(BASE58_ALPHABET.charAt(encoded[i]));
		}
		
		return sb.toString();
	}

	/**
	 * Validates that the given string is a valid Solana address.
	 * Solana addresses are Base58-encoded 32-byte Ed25519 public keys.
	 */
	public static boolean isValidAddress(String address) {
		if (address == null || address.length() < 32 || address.length() > 44) {
			return false;
		}
		try {
			byte[] decoded = decodeBase58(address);
			return decoded != null && decoded.length == 32;
		} catch (Exception e) {
			return false;
		}
	}

}
