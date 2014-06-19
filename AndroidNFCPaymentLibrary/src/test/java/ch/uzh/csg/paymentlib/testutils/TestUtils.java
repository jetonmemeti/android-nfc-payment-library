package ch.uzh.csg.paymentlib.testutils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class TestUtils {
	
	public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
		keyGen.initialize(256, new SecureRandom());
		return keyGen.generateKeyPair();
	}

}
