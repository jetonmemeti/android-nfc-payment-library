package ch.uzh.csg.paymentlib;

import java.nio.charset.Charset;
import java.security.Signature;

import android.app.Activity;
import ch.uzh.csg.nfclib.CustomHostApduService;
import ch.uzh.csg.nfclib.IMessageHandler;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventHandler;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.messages.PaymentError;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;
import ch.uzh.csg.paymentlib.payment.DecoderFactory;
import ch.uzh.csg.paymentlib.payment.InitMessagePayee;
import ch.uzh.csg.paymentlib.payment.PaymentRequest;
import ch.uzh.csg.paymentlib.payment.PaymentResponse;

//TODO: javadoc
public class PaymentRequestHandler {
	
	public static final byte[] ACK = new String("ACK").getBytes(Charset.forName("UTF-8"));
	
	private PaymentEventHandler paymentEventHandler;
	private UserInfos userInfos;
	private ServerInfos serverInfos;
	private IUserPromptPaymentRequest userPrompt;
	private MessageHandler messageHandler;
	
	private int nofMessages;
	private boolean aborted;
	
	//TODO: store current payment session, in order to be able to detect a resume!
	
	private PaymentRequestHandler(Activity activity, PaymentEventHandler paymentEventHandler, UserInfos userInfos, ServerInfos serverInfos, IUserPromptPaymentRequest userPrompt) throws IllegalArgumentException {
		checkParameters(activity, paymentEventHandler, userInfos, serverInfos, userPrompt);
		
		this.paymentEventHandler = paymentEventHandler;
		this.userInfos = userInfos;
		this.serverInfos = serverInfos;
		this.userPrompt = userPrompt;
		this.messageHandler = new MessageHandler();
		
		nofMessages = 0;
		aborted = false;
		
		CustomHostApduService.init(activity, nfcEventHandler, messageHandler);
	}

	private void checkParameters(Activity activity, PaymentEventHandler paymentEventHandler, UserInfos userInfos, ServerInfos serverInfos, IUserPromptPaymentRequest userPrompt) throws IllegalArgumentException {
		if (activity == null)
			throw new IllegalArgumentException("The activity cannot be null.");
		
		if (paymentEventHandler == null)
			throw new IllegalArgumentException("The payment event handler cannot be null.");
		
		if (userInfos == null)
			throw new IllegalArgumentException("The user infos cannot be null.");
		
		if (serverInfos == null)
			throw new IllegalArgumentException("The server infos cannot be null.");
		
		if (userPrompt == null)
			throw new IllegalArgumentException("The user prompt cannot be null.");
	}
	
	private NfcEventHandler nfcEventHandler = new NfcEventHandler() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {
			if (aborted)
				return;
			
			switch (event) {
			case INIT_FAILED:
			case COMMUNICATION_ERROR:
			case ERROR_REPORTED:
				aborted = true;
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, null);
				break;
			case CONNECTION_LOST: // do nothing, because new session can be initiated automatically!
				break;
			case INITIALIZED: //do nothing
				break;
			case MESSAGE_RECEIVED: //do nothing, handle in IMessageHandler
				break;
			case MESSAGE_RETURNED: //do nothing
				break;
			case MESSAGE_SENT:// do nothing, concerns only the NfcTransceiver
				break;
			default:
				break;
			}
		}
		
	};
	
	private byte[] getError(PaymentError err) {
		aborted = true;
		paymentEventHandler.handleMessage(PaymentEvent.ERROR, err);
		return new PaymentMessage(PaymentMessage.ERROR, new byte[] { err.getCode() }).getData();
	}
	
	private class MessageHandler implements IMessageHandler {

		public byte[] handleMessage(byte[] message) {
			if (aborted)
				return null;
			
			nofMessages++;
			PaymentMessage pm = new PaymentMessage(message);
			if (pm.isError()) {
				try {
					PaymentError paymentError = PaymentError.getPaymentError(pm.getPayload()[0]);
					return getError(paymentError);
				} catch (Exception e) {
					return getError(PaymentError.UNEXPECTED_ERROR);
				}
			}
			
			//TODO: implement
//			pm.isResume();
			
			if (pm.isBuyer()) {
				//TODO: implement
				
				
			} else {
				switch (nofMessages) {
				case 1:
					try {
						InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.getData());
						boolean accepted = userPrompt.handlePaymentRequest(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount());
						if (accepted) {
							//TODO: handle timestamp!!
							long timestamp = System.currentTimeMillis();
							PaymentRequest pr = new PaymentRequest(userInfos.getSignatureAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), timestamp);
							pr.sign(userInfos.getPrivateKey());
							return new PaymentMessage(PaymentMessage.DEFAULT, pr.encode()).getData();
						} else {
							return getError(PaymentError.PAYER_REFUSED);
						}
					} catch (Exception e) {
						return getError(PaymentError.UNEXPECTED_ERROR);
					}
				case 2:
					try {
						PaymentResponse paymentResponse = DecoderFactory.decode(PaymentResponse.class, message);
						
						Signature sig = Signature.getInstance(paymentResponse.getSignatureAlgorithm().getSignatureAlgorithm());
						sig.initVerify(serverInfos.getPublicKey());
						sig.update(paymentResponse.getPayload());
						
						boolean signatureValid = sig.verify(paymentResponse.getSignature());
						if (!signatureValid) {
							return getError(PaymentError.UNEXPECTED_ERROR);
						} else {
							paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, null);
							return new PaymentMessage(PaymentMessage.DEFAULT, ACK).getData();
						}
					} catch (Exception e) {
						return getError(PaymentError.UNEXPECTED_ERROR);
					}
				}
			}
			
			return getError(PaymentError.UNEXPECTED_ERROR);
		}
		
	}

}
