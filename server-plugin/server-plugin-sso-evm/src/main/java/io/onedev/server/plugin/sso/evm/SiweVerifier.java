package io.onedev.server.plugin.sso.evm;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.shiro.authc.AuthenticationException;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.utils.Numeric;

/**
 * Verifies Ethereum signatures using the EIP-191 personal sign standard.
 * This implementation uses web3j for cryptographic operations.
 */
public class SiweVerifier {

	/**
	 * The Ethereum personal sign prefix as per EIP-191.
	 */
	private static final String PERSONAL_SIGN_PREFIX = "\u0019Ethereum Signed Message:\n";

	/**
	 * Verifies that the signature was produced by signing the given message
	 * with the private key corresponding to the claimed address.
	 * 
	 * @param message The SIWE message that was signed
	 * @param signature The hex-encoded signature (with 0x prefix)
	 * @return true if the signature is valid, false otherwise
	 */
	public static boolean verify(SiweMessage message, String signature) {
		try {
			String recoveredAddress = recoverAddress(message.toMessage(), signature);
			return recoveredAddress.equalsIgnoreCase(message.getAddress());
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Recovers the Ethereum address that produced the given signature.
	 * Uses EIP-191 personal sign format.
	 * 
	 * @param message The original message that was signed
	 * @param signature The hex-encoded signature (with or without 0x prefix)
	 * @return The checksummed Ethereum address that signed the message
	 * @throws AuthenticationException if signature recovery fails
	 */
	public static String recoverAddress(String message, String signature) throws AuthenticationException {
		try {
			// Parse signature bytes
			byte[] signatureBytes = Numeric.hexStringToByteArray(signature);
			
			if (signatureBytes.length != 65) {
				throw new AuthenticationException("Invalid signature length: expected 65 bytes");
			}

			// Split signature into r, s, v components
			byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
			byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);
			byte v = signatureBytes[64];

			// Handle different v value formats
			// Some wallets return v as 0/1, others as 27/28
			if (v < 27) {
				v += 27;
			}

			// Create the prefixed message hash (EIP-191)
			byte[] messageHash = hashPersonalMessage(message);

			// Try to recover the public key
			SignatureData signatureData = new SignatureData(v, r, s);
			BigInteger publicKey = signPublicKeyFromMessage(messageHash, signatureData);

			if (publicKey == null) {
				throw new AuthenticationException("Failed to recover public key from signature");
			}

			// Derive address from public key and return checksummed version
			String address = "0x" + Keys.getAddress(publicKey);
			return Keys.toChecksumAddress(address);

		} catch (AuthenticationException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthenticationException("Signature verification failed: " + e.getMessage());
		}
	}

	/**
	 * Creates the EIP-191 personal message hash.
	 * Format: keccak256("\x19Ethereum Signed Message:\n" + len(message) + message)
	 */
	private static byte[] hashPersonalMessage(String message) {
		byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
		String prefix = PERSONAL_SIGN_PREFIX + messageBytes.length;
		byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
		
		byte[] combined = new byte[prefixBytes.length + messageBytes.length];
		System.arraycopy(prefixBytes, 0, combined, 0, prefixBytes.length);
		System.arraycopy(messageBytes, 0, combined, prefixBytes.length, messageBytes.length);
		
		return Hash.sha3(combined);
	}

	/**
	 * Recovers the public key from a message hash and signature.
	 * Tries both recovery IDs (0 and 1) to find the valid one.
	 */
	private static BigInteger signPublicKeyFromMessage(byte[] messageHash, SignatureData signatureData) {
		BigInteger r = new BigInteger(1, signatureData.getR());
		BigInteger s = new BigInteger(1, signatureData.getS());
		
		// Calculate recovery ID from v
		int recId = signatureData.getV()[0];
		if (recId >= 27) {
			recId -= 27;
		}

		ECDSASignature sig = new ECDSASignature(r, s);
		
		try {
			return Sign.recoverFromSignature(recId, sig, messageHash);
		} catch (Exception e) {
			// Try the other recovery ID
			try {
				return Sign.recoverFromSignature(1 - recId, sig, messageHash);
			} catch (Exception e2) {
				return null;
			}
		}
	}

	/**
	 * Converts an Ethereum address to its checksummed format (EIP-55).
	 */
	public static String toChecksumAddress(String address) {
		return Keys.toChecksumAddress(address);
	}

	/**
	 * Validates that the given string is a valid Ethereum address.
	 */
	public static boolean isValidAddress(String address) {
		if (address == null || !address.matches("^0x[a-fA-F0-9]{40}$")) {
			return false;
		}
		return true;
	}

}
