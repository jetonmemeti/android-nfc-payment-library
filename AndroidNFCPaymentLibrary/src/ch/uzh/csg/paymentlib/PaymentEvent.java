package ch.uzh.csg.paymentlib;

//TODO: javadoc
public enum PaymentEvent {
	ERROR,
	SUCCESS,
	FORWARD_TO_SERVER,
	REFUSED_BY_SERVER,
	REFUSED_BY_PAYER;
}
