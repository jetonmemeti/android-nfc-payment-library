package ch.uzh.csg.paymentlib.container;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;

//TODO: javadoc
public class PayeeInfos {
	
	private String username;
	
	public PayeeInfos(String username) throws IllegalArgumentException {
		if (username == null || username.isEmpty() || username.length() > 255)
			throw new IllegalArgumentException("The username cannot be null, empty, or longer than 255 characters.");
		
		this.username = username;
	}
	
	public String getUsername() {
		return username;
	}

}
