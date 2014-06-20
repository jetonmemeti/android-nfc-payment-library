package ch.uzh.csg.paymentlib.messages;



//TODO: javadoc
public class PaymentMessage {

	public static final int HEADER_LENGTH = 1;

	// type, uses 3 bits
	public static final byte EMPTY = 0x00;
	public static final byte DEFAULT = 0x01;
	public static final byte RESUME = 0x02; // if not set, then START //TODO:
	                                        // needed??
	public static final byte START = 0x03;
	public static final byte BUYER = 0x04; // if not set, then SELLER
	public static final byte SELLER = 0x05; // if not set, then SELLER
	public static final byte UNUSED_1 = 0x06;
	public static final byte UNUSED_2 = 0x07;

	// flags
	public static final byte UNUSED_FLAG_1 = 0x08;
	public static final byte UNUSED_FLAG_2 = 0x10;
	public static final byte UNUSED_FLAG_3 = 0x20;
	public static final byte UNUSED_FLAG_4 = 0x40;
	public static final byte ERROR = (byte) 0x80; // if not set, then PROCEED

	// data
	private byte[] payload = new byte[0];
	private int header;

	public PaymentMessage type(byte messageType) {
		if (messageType > UNUSED_2) {
			throw new IllegalArgumentException("largest message type is " + UNUSED_2);
		}
		// preserve only the flags
		header = header & 0xF8;
		header = header | messageType;
		return this;
	}

	public int type() {
		// return the last 3 bits
		return header & 0x7;
	}

	public PaymentMessage payload(byte[] payload) {
		if (payload == null) {
			throw new IllegalArgumentException("data cannot be null");
		}
		this.payload = payload;
		return this;
	}

	public byte[] payload() {
		return payload;
	}

	public boolean isError() {
		return (header & ERROR) != 0;
	}

	public PaymentMessage error(boolean error) {
		if (error) {
			header = header | ERROR;
		} else {
			header = header & ~ERROR;
		}
		return this;
	}

	public PaymentMessage error() {
		error(true);
		return this;
	}

	// serialization
	public byte[] bytes() {
		final int len = payload.length;
		byte[] output = new byte[HEADER_LENGTH + len];
		output[0] = (byte) header;
		System.arraycopy(payload, 0, output, HEADER_LENGTH, len);
		return output;
	}

	public boolean isEmpty() {
		return header == 0 && payload.length == 0;
	}

	public PaymentMessage bytes(byte[] input) {
		final int len = input.length;
		if (!isEmpty() || input == null || len < HEADER_LENGTH) {
			throw new IllegalArgumentException("Message is empty, no input, or not enough data");
		}

		// this is now a custom message
		header = input[0];
		if (len > HEADER_LENGTH) {
			payload = new byte[len - HEADER_LENGTH];
			System.arraycopy(input, HEADER_LENGTH, payload, 0, len - HEADER_LENGTH);
		}
		return this;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("PxMsg: ");
		sb.append("head: ").append(Integer.toHexString(header));
		sb.append(",len:").append(payload.length);
		return sb.toString();
	}
}
