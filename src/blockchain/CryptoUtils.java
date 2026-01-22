package blockchain;

import java.security.*;
import java.util.Base64;

public class CryptoUtils {
	
	// Sign data using a private key
	public static String signData(String data, PrivateKey privateKey) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privateKey);
		signature.update(data.getBytes());
		byte[] signedBytes = signature.sign();
		return Base64.getEncoder().encodeToString(signedBytes);
	}
	
	// Verifying a signature using a public key
	public static boolean verifySignature(String data, String signatureStr, PublicKey publicKey) throws Exception{
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initVerify(publicKey);
		signature.update(data.getBytes());
		byte[] signatureBytes =  Base64.getDecoder().decode(signatureStr);
		return signature.verify(signatureBytes);
	}
	
	// Hashing Data
	public static String hashData(String data) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hashBytes = digest.digest(data.getBytes());
		StringBuilder sb = new StringBuilder();
		for (byte b : hashBytes) sb.append(String.format("%02x", b)); // Convert bytes to hex
		return sb.toString();
	}
	
	// Key Pair Generation
	public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(512);
		return keyGen.generateKeyPair();
	}
}
