package ch.uzh.csg.paymentlib;

import android.app.Activity;
import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.InitMessagePayee;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventHandler;
import ch.uzh.csg.nfclib.exceptions.NfcNotEnabledException;
import ch.uzh.csg.nfclib.exceptions.NoNfcException;
import ch.uzh.csg.nfclib.transceiver.ExternalNfcTransceiver;
import ch.uzh.csg.nfclib.transceiver.InternalNfcTransceiver;
import ch.uzh.csg.nfclib.transceiver.NfcTransceiver;
import ch.uzh.csg.paymentlib.container.PaymentInfos;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.UnknownPaymentErrorException;
import ch.uzh.csg.paymentlib.messages.PaymentError;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;

//TODO: javadoc
public class PaymentRequestInitializer {
	
	/*
	 * seller/payee inits the payment --> type: request_payment
	 * buyer/payer inits the payment --> type : send_payment
	 */
	public enum PaymentType {
		REQUEST_PAYMENT,
		SEND_PAYMENT;
	}
	
	private PaymentType paymentType;
	
	private Activity activity;
	private PaymentEventHandler paymentEventHandler;
	
	private UserInfos userInfos;
	private ServerInfos serverInfos;
	private PaymentInfos paymentInfos;
	
	private NfcTransceiver nfcTransceiver;
	private int nofMessages;
	private boolean aborted;
	private boolean disabled;
	
	public PaymentRequestInitializer(Activity activity, PaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, PaymentType type) throws IllegalArgumentException, NoNfcException, NfcNotEnabledException {
		this(activity, null, paymentEventHandler, userInfos, paymentInfos, serverInfos, type);
	}
	
	/*
	 * This constructor is only for test purposes, in order to mock the
	 * NfcTransceiver. For productive use the public constructor, otherwise the
	 * NFC will not work.
	 */
	protected PaymentRequestInitializer(Activity activity, NfcTransceiver nfcTransceiver, PaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, PaymentType type) throws IllegalArgumentException, NoNfcException, NfcNotEnabledException {
		checkParameters(activity, paymentEventHandler, userInfos, paymentInfos, serverInfos, type);
		
		this.paymentType = type;
		this.activity = activity;
		this.paymentEventHandler = paymentEventHandler;
		this.userInfos = userInfos;
		this.serverInfos = serverInfos;
		this.paymentInfos = paymentInfos;
		
		this.nofMessages = 0;
		this.aborted = false;
		this.disabled = false;
		
		initPayment(nfcTransceiver);
	}

	private void checkParameters(Activity activity, PaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, PaymentType type) throws IllegalArgumentException {
		if (activity == null)
			throw new IllegalArgumentException("The activity cannot be null.");
		
		if (paymentEventHandler == null)
			throw new IllegalArgumentException("The payment event handler cannot be null.");
		
		if (userInfos == null)
			throw new IllegalArgumentException("The user infos cannot be null.");
		
		if (paymentInfos == null)
			throw new IllegalArgumentException("The payment infos cannot be null.");
		
		if (serverInfos == null)
			throw new IllegalArgumentException("The server infos cannot be null.");
		
		if (type == null)
			throw new IllegalArgumentException("The payment type cannot be null.");
	}
	
	private void initPayment(NfcTransceiver nfcTransceiver) throws NoNfcException, NfcNotEnabledException {
		NfcEventHandler nfcEventHandler;
		if (this.paymentType == PaymentType.REQUEST_PAYMENT)
			nfcEventHandler = nfcEventHandlerRequest;
		else
			nfcEventHandler = nfcEventHandlerSend;
		
		if (nfcTransceiver != null) {
			this.nfcTransceiver = nfcTransceiver;
		} else {
			if (ExternalNfcTransceiver.isExternalReaderAttached(activity))
				this.nfcTransceiver = new ExternalNfcTransceiver(nfcEventHandler, userInfos.getUserId());
			else
				this.nfcTransceiver = new InternalNfcTransceiver(nfcEventHandler, userInfos.getUserId());
			
			this.nfcTransceiver.enable(activity);
		}
	}
	
	private void sendError(PaymentError err) {
		aborted = true;
		nfcTransceiver.transceive(new PaymentMessage(PaymentMessage.ERROR, new byte[] { err.getCode() }).getData());
		paymentEventHandler.handleMessage(PaymentEvent.ERROR, err);
	}
	
	/*
	 * only for test purposes
	 */
	protected NfcEventHandler getNfcEventHandlerRequest() {
		return nfcEventHandlerRequest;
	}
	
	private NfcEventHandler nfcEventHandlerRequest = new NfcEventHandler() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {
			if (aborted) {
				if (!disabled) {
					nfcTransceiver.disable(activity);
					disabled = true;
				}
				return;
			}
			
			switch (event) {
			case INIT_FAILED:
			case COMMUNICATION_ERROR:
			case ERROR_REPORTED:
				aborted = true;
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, null);
				nfcTransceiver.disable(activity);
				break;
			case CONNECTION_LOST: // do nothing, because new session can be initiated automatically!
			case MESSAGE_RETURNED: // do nothing, concerns only the HCE
				break;
			case MESSAGE_SENT:
				nofMessages++;
				break;
			case INITIALIZED:
				//TODO: store current payment session, in order to be able to detect a resume!
				try {
					InitMessagePayee initMessage = new InitMessagePayee(userInfos.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
					nfcTransceiver.transceive(new PaymentMessage(PaymentMessage.DEFAULT, initMessage.encode()).getData());
				} catch (Exception e) {
					sendError(PaymentError.UNEXPECTED_ERROR);
				}
				break;
			case MESSAGE_RECEIVED:
				//TODO: send next message
				
				if (object == null || !(object instanceof byte[])) {
					sendError(PaymentError.UNEXPECTED_ERROR);
					break;
				}
				
				PaymentMessage response = new PaymentMessage((byte[]) object);
				if (response.isError()) {
					PaymentError paymentError = null;
					if (response.getPayload() != null && response.getPayloadLength() > 0) {
						try {
							paymentError = PaymentError.getPaymentError(response.getPayload()[0]);
						} catch (UnknownPaymentErrorException e) {
						}
					}
					
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, paymentError);
					nfcTransceiver.disable(activity);
					break;
				}
				
				//TODO: implement
//				pm.isResume();
				
				switch (nofMessages) {
				case 1:
					try {
						PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, response.getPayload());
						PaymentRequest paymentRequestPayee = new PaymentRequest(userInfos.getSignatureAlgorithm(), userInfos.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfos.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentRequestPayer.getTimestamp());
						if (!paymentRequestPayer.requestsIdentic(paymentRequestPayee)) {
							sendError(PaymentError.REQUESTS_NOT_IDENTIC);
						} else {
							paymentRequestPayee.sign(userInfos.getPrivateKey());
							ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
							paymentEventHandler.handleMessage(PaymentEvent.FORWARD_TO_SERVER, spr.encode());
						}
					} catch (Exception e) {
						sendError(PaymentError.UNEXPECTED_ERROR);
					}
					break;
				case 2:
					//TODO: check ACK?
					
					nfcTransceiver.disable(activity);
					break;
				}
				break;
			}
		}
	};
	
	public void onServerResponse(byte[] bytes) {
		try {
			ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, bytes);
			PaymentResponse paymentResponse;
			if (serverPaymentResponse.getPaymentResponsePayee() != null)
				paymentResponse = serverPaymentResponse.getPaymentResponsePayee();
			else
				paymentResponse = serverPaymentResponse.getPaymentResponsePayer();
			
			boolean signatureValid = paymentResponse.verify(serverInfos.getPublicKey());
			if (!signatureValid) {
				sendError(PaymentError.UNEXPECTED_ERROR);
			} else {
				switch (paymentResponse.getStatus()) {
				case FAILURE:
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.SERVER_REFUSED);
					break;
				case SUCCESS:
					paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, paymentResponse);
					break;
				}
				
				byte[] encode = serverPaymentResponse.getPaymentResponsePayer().encode();
				nfcTransceiver.transceive(new PaymentMessage(PaymentMessage.DEFAULT, encode).getData());
			}
		} catch (Exception e) {
			sendError(PaymentError.UNEXPECTED_ERROR);
		}
	}

	private NfcEventHandler nfcEventHandlerSend = new NfcEventHandler() {
		
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
			case INITIALIZED:
				break;
			case INIT_FAILED:
				break;
			case MESSAGE_RECEIVED:
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
	
}
