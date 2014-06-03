package ch.uzh.csg.paymentlib;

import android.app.Activity;
import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.InitMessagePayee;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.nfclib.CustomHostApduService;
import ch.uzh.csg.nfclib.IMessageHandler;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventHandler;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.messages.PaymentError;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;
import ch.uzh.csg.paymentlib.persistency.IPersistencyHandler;
import ch.uzh.csg.paymentlib.persistency.PersistedPaymentRequest;

//TODO: javadoc
public class PaymentRequestHandler {
	
	public static final byte[] ACK = new byte[] { (byte) 0xAC };
	
	private PaymentEventHandler paymentEventHandler;
	private UserInfos userInfos;
	private ServerInfos serverInfos;
	private IUserPromptPaymentRequest userPrompt;
	private IPersistencyHandler persistencyHandler;
	private MessageHandler messageHandler;
	
	private int nofMessages = 0;
	private boolean aborted = false;
	
	public PaymentRequestHandler(Activity activity, PaymentEventHandler paymentEventHandler, UserInfos userInfos, ServerInfos serverInfos, IUserPromptPaymentRequest userPrompt, IPersistencyHandler persistencyHandler) throws IllegalArgumentException {
		checkParameters(activity, paymentEventHandler, userInfos, serverInfos, userPrompt);
		
		this.paymentEventHandler = paymentEventHandler;
		this.userInfos = userInfos;
		this.serverInfos = serverInfos;
		this.userPrompt = userPrompt;
		this.persistencyHandler = persistencyHandler;
		this.messageHandler = new MessageHandler();
		
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
			case FATAL_ERROR:
				aborted = true;
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, null);
				break;
			case CONNECTION_LOST: // do nothing, because new session can be initiated automatically! //TODO: really?
				break;
			case INITIALIZED: //do nothing
				
				//TODO: read out userid to distinguish between new/resume!! probably not, only used for nfc layer communication!!
				
				
				
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
	
	/*
	 * only for test purposes
	 */
	protected MessageHandler getMessageHandler() {
		return new MessageHandler();
	}
	
	protected class MessageHandler implements IMessageHandler {
		
		private PersistedPaymentRequest storedPaymentRequest;

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
			
			if (pm.isBuyer()) {
				//TODO: implement
				
				
			} else {
				switch (nofMessages) {
				case 1:
					try {
						InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.getPayload());
						long timestamp = System.currentTimeMillis();
						
						//TODO: store current payment session, in order to be able to detect a resume!
						PersistedPaymentRequest xmlPaymentRequest = new PersistedPaymentRequest(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), timestamp);
						if (persistencyHandler.exists(xmlPaymentRequest)) {
							timestamp = xmlPaymentRequest.getTimestamp();
						} else {
							persistencyHandler.add(xmlPaymentRequest);
						}
						
						boolean accepted = userPrompt.handlePaymentRequest(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount());
						if (accepted) {
							PaymentRequest pr = new PaymentRequest(userInfos.getSignatureAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), timestamp);
							pr.sign(userInfos.getPrivateKey());
							
							//TODO: add thread to detect timeout
//							Thread t = new Thread();
//							t.start();
							
							return new PaymentMessage(PaymentMessage.DEFAULT, pr.encode()).getData();
						} else {
							return getError(PaymentError.PAYER_REFUSED);
						}
					} catch (Exception e) {
						return getError(PaymentError.UNEXPECTED_ERROR);
					}
				case 2:
					
					//TODO: add timer if no response arrives --> abort event
					//TODO: if this message does not arrive, we can't be sure if the payment was accepted or rejected! show on gui!! PaymentEvent.NO_SERVER_RESPONSE!
					//TODO: if no response arrives, write to xml!
					
					
					try {
						PaymentResponse paymentResponse = DecoderFactory.decode(PaymentResponse.class, message);
						boolean signatureValid = paymentResponse.verify(serverInfos.getPublicKey());
						if (!signatureValid) {
							return getError(PaymentError.UNEXPECTED_ERROR);
						} else {
							persistencyHandler.delete(storedPaymentRequest);
							switch (paymentResponse.getStatus()) {
							case FAILURE:
								paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.SERVER_REFUSED);
								break;
							case SUCCESS:
								paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, paymentResponse);
								break;
							}
							
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
	
	private class TimeoutHandlerTask implements Runnable {
		
		public void run() {
			//TODO: implement
			
			
		}
	}
}
