package ch.uzh.csg.paymentlib.payment;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;
import ch.uzh.csg.paymentlib.exceptions.UnknownCurrencyException;
import ch.uzh.csg.paymentlib.exceptions.UnknownSignatureAlgorithmException;
import ch.uzh.csg.paymentlib.paymentrequest.Currency;
import ch.uzh.csg.paymentlib.paymentrequest.SignatureAlgorithm;

//TODO: javadoc
public abstract class SerializableObject {
	
	private int version;
	
	//this constructor is needed for the DecoderFactory
	protected SerializableObject() {
	}
	
	public SerializableObject(int version) throws IllegalArgumentException {
		if (version <= 0 || version > 255)
			throw new IllegalArgumentException("The version number must be between 1 and 255.");
		
		this.version = version;
	}
	
	public int getVersion() {
		return version;
	}
	
	/**
	 * Returns the raw payload of this object. If it is a
	 * {@link SignedSerializableObject} the raw signature is attached to the
	 * payload.
	 * 
	 * @throws NotSignedException
	 *             if this is a subclass of {@link SignedSerializableObject} and
	 *             was not signed before
	 */
	public abstract byte[] encode() throws NotSignedException;
	
	/**
	 * Deserializes a SerializableObject based on the given bytes.
	 * 
	 * @param bytes
	 *            the raw data
	 * @throws IllegalArgumentException
	 *             if bytes is null or does not contain enough information to
	 *             deserialize
	 * @throws NotSignedException
	 *             if a {@link SignedSerializableObject} is being deserialized
	 *             but there is no signature attached
	 * @throws UnknownSignatureAlgorithmException
	 *             if the {@link SignatureAlgorithm} given in the bytes is not
	 *             known
	 * @throws UnknownCurrencyException
	 *             if the {@link Currency} given in the bytes is not known
	 */
	public abstract SerializableObject decode(byte[] bytes) throws IllegalArgumentException, NotSignedException, UnknownSignatureAlgorithmException, UnknownCurrencyException;
	
}
