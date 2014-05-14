package ch.uzh.csg.paymentlib;

import java.util.HashMap;
import java.util.Map;

/**
 * This class contains the supported signature algorithms.
 * 
 * @author Jeton
 * 
 */
public enum SignatureAlgorithm {
	SHA256withECDSA("SHA256withECDSA", (byte) 0x01);
	
	private String description;
	private byte code;
	
	private SignatureAlgorithm(String description, byte code) {
		this.description = description;
		this.code = code;
	}
	
	/**
	 * Returns the code of the SignatureAlgorithm.
	 */
	public byte getCode() {
		return code;
	}
	
	/**
	 * Returns the description, which has to be passed to
	 * Signature.getInstance().
	 */
	public String getDescription() {
		return description;
	}
	
	private static Map<Byte, SignatureAlgorithm> codeAlgorithmMap = null;
	
	/**
	 * Returns the SignatureAlgorithm object based on the code.
	 * 
	 * @param b
	 *            the code of the algorithm
	 * @return
	 */
	public static SignatureAlgorithm getSignatureAlgorithm(byte b) {
		if (codeAlgorithmMap == null)
			initMap();
		
		return codeAlgorithmMap.get(b);
	}

	private static void initMap() {
		codeAlgorithmMap = new HashMap<Byte, SignatureAlgorithm>();
		for (SignatureAlgorithm s : values()) {
			codeAlgorithmMap.put(s.getCode(), s);
		}
	}
	
}
