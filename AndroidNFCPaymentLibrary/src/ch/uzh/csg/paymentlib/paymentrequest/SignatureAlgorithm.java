package ch.uzh.csg.paymentlib.paymentrequest;

import java.util.HashMap;
import java.util.Map;

import ch.uzh.csg.paymentlib.exceptions.UnknownSignatureAlgorithmException;

/**
 * This class contains the supported signature algorithms and the corresponding
 * asymmetric key algorithm.
 * 
 * @author Jeton Memeti
 * 
 */
public enum SignatureAlgorithm {
	SHA256withECDSA((byte) 0x01, "brainpoolp256r1", "SHA256withECDSA");
	
	private byte code;
	private String keyGenAlgorithm;
	private String signatureAlgorithm;
	
	private SignatureAlgorithm(byte code, String keyGenAlgorithm, String signatureAlgorithm) {
		this.code = code;
		this.keyGenAlgorithm = keyGenAlgorithm;
		this.signatureAlgorithm = signatureAlgorithm;
	}
	
	/**
	 * Returns the code of the SignatureAlgorithm.
	 */
	public byte getCode() {
		return code;
	}
	
	/**
	 * Returns the algorithm to be used for the asymmetric keys.
	 */
	public String getKeyGenAlgorithm() {
		return keyGenAlgorithm;
	}
	
	/**
	 * Returns the algorithm to be used for the digital signature.
	 */
	public String getSignatureAlgorithm() {
		return signatureAlgorithm;
	}
	
	private static Map<Byte, SignatureAlgorithm> codeAlgorithmMap = null;
	
	/**
	 * Returns the SignatureAlgorithm object based on the code.
	 * 
	 * @param b
	 *            the code of the algorithm
	 * @return
	 * @throws UnknownSignatureAlgorithmException
	 *             if the given code is not known
	 */
	public static SignatureAlgorithm getSignatureAlgorithm(byte b) throws UnknownSignatureAlgorithmException {
		if (codeAlgorithmMap == null)
			initMap();
		
		SignatureAlgorithm signatureAlgorithm = codeAlgorithmMap.get(b);
		if (signatureAlgorithm == null)
			throw new UnknownSignatureAlgorithmException();
		else
			return signatureAlgorithm;
	}

	private static void initMap() {
		codeAlgorithmMap = new HashMap<Byte, SignatureAlgorithm>();
		for (SignatureAlgorithm s : values()) {
			codeAlgorithmMap.put(s.getCode(), s);
		}
	}
	
}
