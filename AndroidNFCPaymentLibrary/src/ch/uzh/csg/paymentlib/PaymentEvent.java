package ch.uzh.csg.paymentlib;

//TODO: javadoc
public enum PaymentEvent {
	ERROR,
	SUCCESS,
	FORWARD_TO_SERVER,
	NO_SERVER_RESPONSE; //when no server response received (neither ok nor nok)
}
