package ch.uzh.csg.paymentlib.messages;

/**
 * This class represents a protocol message. This and only this message is send
 * between the two devices in order to accomplish a payment.
 * 
 * The message consists of a header containing a flag and the arbitrary long
 * payload.
 * 
 * If the header is set to ERROR, then the payload contains a code corresponding
 * to an {@link PaymentError}.
 * 
 * @author Jeton Memeti
 * 
 */
public class PaymentMessage {
	
	/*
	 * The number of the first version is 0. Future versions might be 1, 2, or
	 * 3. Afterwards, a new byte has to be allocated for to contain the version
	 * number.
	 */
	private static final int VERSION = 0;

	public static final int HEADER_LENGTH = 1;
	
	public static final byte ERROR = 0x01; // if not set, then PROCEED
	public static final byte PAYER = 0x02; // if not set, then PAYEE
	public static final byte UNUSED_1 = 0x04;
	public static final byte UNUSED_2 = 0x08;
	public static final byte UNUSED_3 = 0x10;
	public static final byte UNUSED_4 = 0x20;

	private byte[] payload = new byte[0];
	private int header = VERSION << 6;
	
	/**
	 * Returns the version of this message.
	 */
	public int version() {
		// the version number is encoded in the first two bits
		return ((header) & 0xC0) >>> 6;
	}
	
	/**
	 * Returns the highest supported version of Payment Messages. If version()
	 * returns an higher version that this method, we cannot process that
	 * message.
	 */
	public static int getSupportedVersion() {
		return VERSION;
	}
	
	/**
	 * Sets the header of this message to error (other flags are preserved!).
	 * 
	 * @return this object with the new flag
	 */
	public PaymentMessage error() {
		header = header | ERROR;
		return this;
	}
	
	/**
	 * Returns if the flag in the header is ERROR. This means that an error has
	 * occured and the communication can be aborted.
	 */
	public boolean isError() {
		return (header & ERROR) == ERROR;
	}
	
	/**
	 * Sets the header of this message to payer (other flags are preserved!). If
	 * the payee flag is set, it will be ovwrwritten. This has to be used only
	 * if the payer is sending this message to the payee.
	 * 
	 * @return this object with the new flag
	 */
	public PaymentMessage payer() {
		header = header | PAYER;
		return this;
	}
	
	/**
	 * Returns it the flag in the header is PAYER, this means if the payer has
	 * sent this message.
	 */
	public boolean isPayer() {
		return (header & PAYER) == PAYER;
	}
	
	/**
	 * Sets the header of this message to payee (other flags are preserved!). If
	 * the payer flag is set, it will be overwritten. This has to be used only
	 * if the payee is sending this message to the payer.
	 * 
	 * @return this object with the new flag
	 */
	public PaymentMessage payee() {
		header = header & ~PAYER;
		return this;
	}
	
	/**
	 * Returns it the flag in the header is PAYEE, this means if the payee has
	 * sent this message.
	 */
	public boolean isPayee() {
		return (header & PAYER) != PAYER;
	}

	/**
	 * Sets the payload of this message. The header flags are preserved.
	 * 
	 * @param payload
	 *            to be set
	 * @return this object with the new payload
	 */
	public PaymentMessage payload(byte[] payload) {
		if (payload == null || payload.length == 0)
			throw new IllegalArgumentException("payload cannot be null or empty");
		
		this.payload = payload;
		return this;
	}

	/**
	 * Returns the payload of this message.
	 */
	public byte[] payload() {
		return payload;
	}
	
	/**
	 * Serializes this message and returns the byte array.
	 */
	public byte[] bytes() {
		final int len = payload.length;
		byte[] output = new byte[HEADER_LENGTH + len];
		output[0] = (byte) header;
		System.arraycopy(payload, 0, output, HEADER_LENGTH, len);
		return output;
	}

	/**
	 * Instantiates a new message from the input. Input must contain a valid
	 * header as well as an optional payload.
	 * 
	 * If this message is not empty (e.g., edited before) an
	 * IllegalArgumentException is thrown. Instantiate a new object before
	 * calling this method.
	 * 
	 * @param input
	 *            the serialized data to be deserialized into a payment message
	 */
	public PaymentMessage bytes(byte[] input) {
		final int len = input.length;
		if (!isEmpty())
			throw new IllegalArgumentException("This message is not empty. You cannot overwrite the content. Instantiate a new object.");
		
		if (input == null || len < HEADER_LENGTH)
			throw new IllegalArgumentException("The input is null or does not contain enough data.");

		header = input[0];
		if (len > HEADER_LENGTH) {
			payload = new byte[len - HEADER_LENGTH];
			System.arraycopy(input, HEADER_LENGTH, payload, 0, len - HEADER_LENGTH);
		}
		return this;
	}
	
	private boolean isEmpty() {
		return (header << 2) == 0 && payload.length == 0;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("PaymentMsg: ");
		sb.append("head: ").append(Integer.toHexString(header));
		sb.append(", len:").append(payload.length);
		return sb.toString();
	}
	
}
