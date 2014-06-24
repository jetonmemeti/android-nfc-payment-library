package ch.uzh.csg.paymentlib;

import android.os.Handler;

//TODO: javadoc
public abstract class PaymentEventHandler extends Handler {
	
	public abstract void handleMessage(PaymentEvent event, Object object);

}
