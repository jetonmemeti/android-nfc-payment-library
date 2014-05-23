package ch.uzh.csg.paymentlib;

import android.app.Activity;
import ch.uzh.csg.nfclib.CustomHostApduService;
import ch.uzh.csg.nfclib.IMessageHandler;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventHandler;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;

//TODO: javadoc
public class PaymentRequestHandler {
	
	private MessageHandler messageHandler;
	
	private int nofMessages = 0;
	
	private PaymentRequestHandler(Activity activity, UserInfos userInfos) {
		this.messageHandler = new MessageHandler();
		CustomHostApduService.init(activity, nfcEventHandler, messageHandler);
	}
	
	private NfcEventHandler nfcEventHandler = new NfcEventHandler() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {
			// TODO Auto-generated method stub
			switch (event) {
			case COMMUNICATION_ERROR:
				break;
			case CONNECTION_LOST:
				break;
			case ERROR_REPORTED:
				break;
			case INITIALIZED: //do nothing
				break;
			case INIT_FAILED:
				break;
			case MESSAGE_RECEIVED:
				nofMessages++;
				
				PaymentMessage pm = (PaymentMessage) object;
				
				switch (nofMessages) {
				case 1:
					byte[] initBytes = pm.getData();
					
					
					
					
					break;
				}
				
				break;
			case MESSAGE_RETURNED:
				break;
			case MESSAGE_SENT:
				break;
			default:
				break;
			
			}
		}
		
	};
	
	private class MessageHandler implements IMessageHandler {

		public byte[] handleMessage(byte[] message) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

}
