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
import ch.uzh.csg.paymentlib.util.Config;

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
		checkParameters(activity, paymentEventHandler, userInfos, serverInfos, userPrompt, persistencyHandler);
		
		this.paymentEventHandler = paymentEventHandler;
		this.userInfos = userInfos;
		this.serverInfos = serverInfos;
		this.userPrompt = userPrompt;
		this.persistencyHandler = persistencyHandler;
		this.messageHandler = new MessageHandler();
		
		CustomHostApduService.init(activity, nfcEventHandler, messageHandler);
	}
	
	private void checkParameters(Activity activity, PaymentEventHandler paymentEventHandler, UserInfos userInfos, ServerInfos serverInfos, IUserPromptPaymentRequest userPrompt, IPersistencyHandler persistencyHandler) throws IllegalArgumentException {
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
		
		if (persistencyHandler == null)
			throw new IllegalArgumentException("The persistency handler cannot be null.");
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
			case CONNECTION_LOST:
				nofMessages = 0;
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
	
	/*
	 * only for test purposes
	 */
	protected MessageHandler getMessageHandler() {
		return new MessageHandler();
	}
	
	protected class MessageHandler implements IMessageHandler {
		
		private PersistedPaymentRequest persistedPaymentRequest;
		private volatile boolean serverResponseArrived = false;


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
						
						//TODO: how long is a timestamp valid? add to PaymentError.TIMESTAMP_INVALID
						
						boolean paymentAccepted;
						
						if (persistedPaymentRequest != null
								&& persistedPaymentRequest.getUsername().equals(initMessage.getUsername())
								&& persistedPaymentRequest.getCurrency().getCode() == initMessage.getCurrency().getCode()
								&& persistedPaymentRequest.getAmount() == initMessage.getAmount()) {
							// this is a payment resume (the user took his device away to accept/reject the payment
							
							paymentAccepted = userPrompt.isPaymentAccepted();
						} else {
							// this is a new session
							persistedPaymentRequest = persistencyHandler.getPersistedPaymentRequest(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount());
							if (persistedPaymentRequest == null) {
								// this is a new payment request (not a payment request with a lost server response)
								persistedPaymentRequest = new PersistedPaymentRequest(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), System.currentTimeMillis());
							}
							
							paymentAccepted = userPrompt.getPaymentRequestAnswer(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount());
						}
						
						if (paymentAccepted) {
							PaymentRequest pr = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), persistedPaymentRequest.getTimestamp());
							pr.sign(userInfos.getPrivateKey());
							byte[] encoded = pr.encode();
							
							Thread t = new Thread(new TimeoutHandler());
							t.start();
							
							persistencyHandler.add(persistedPaymentRequest);
							return new PaymentMessage(PaymentMessage.DEFAULT, encoded).getData();
						} else {
							return getError(PaymentError.PAYER_REFUSED);
						}
					} catch (Exception e) {
						return getError(PaymentError.UNEXPECTED_ERROR);
					}
				case 2:
					serverResponseArrived = true;
					
					try {
						PaymentResponse paymentResponse = DecoderFactory.decode(PaymentResponse.class, message);
						boolean signatureValid = paymentResponse.verify(serverInfos.getPublicKey());
						if (!signatureValid) {
							return getError(PaymentError.UNEXPECTED_ERROR);
						} else {
							persistencyHandler.delete(persistedPaymentRequest);
							
							switch (paymentResponse.getStatus()) {
							case FAILURE:
								paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.SERVER_REFUSED);
								break;
							case SUCCESS:
								paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, paymentResponse);
								break;
							case DUPLICATE_REQUEST:
								paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.DUPLICATE_REQUEST);
								break;
							}
							
							return new PaymentMessage(PaymentMessage.DEFAULT, ACK).getData();
						}
					} catch (Exception e) {
						return getError(PaymentError.UNEXPECTED_ERROR);
					}
				}
			}

			//TODO: move this up!
			return getError(PaymentError.UNEXPECTED_ERROR);
		}
		
		private class TimeoutHandler implements Runnable {
			
			public void run() {
				long startTime = System.currentTimeMillis();
				
				while (!serverResponseArrived) {
					long now = System.currentTimeMillis();
					if (now - startTime > Config.SERVER_RESPONSE_TIMEOUT) {
						paymentEventHandler.handleMessage(PaymentEvent.NO_SERVER_RESPONSE, null);
						break;
					}
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		}
	}
	
}
