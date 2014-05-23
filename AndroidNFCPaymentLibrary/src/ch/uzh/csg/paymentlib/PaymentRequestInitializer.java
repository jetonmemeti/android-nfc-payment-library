package ch.uzh.csg.paymentlib;

import java.nio.charset.Charset;
import java.security.Signature;

import android.app.Activity;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventHandler;
import ch.uzh.csg.nfclib.exceptions.NfcNotEnabledException;
import ch.uzh.csg.nfclib.exceptions.NoNfcException;
import ch.uzh.csg.nfclib.transceiver.ExternalNfcTransceiver;
import ch.uzh.csg.nfclib.transceiver.InternalNfcTransceiver;
import ch.uzh.csg.nfclib.transceiver.NfcTransceiver;
import ch.uzh.csg.nfclib.util.Utils;
import ch.uzh.csg.paymentlib.container.PaymentInfos;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;
import ch.uzh.csg.paymentlib.payment.DecoderFactory;
import ch.uzh.csg.paymentlib.payment.PaymentRequest;
import ch.uzh.csg.paymentlib.payment.PaymentResponse;
import ch.uzh.csg.paymentlib.payment.ServerPaymentRequest;
import ch.uzh.csg.paymentlib.payment.ServerPaymentResponse;

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
	private int nofMessage;
	
	public PaymentRequestInitializer(Activity activity, PaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, PaymentType type) throws IllegalArgumentException, NoNfcException, NfcNotEnabledException {
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
		
		this.paymentType = type;
		
		this.activity = activity;
		this.paymentEventHandler = paymentEventHandler;
		this.userInfos = userInfos;
		this.serverInfos = serverInfos;
		this.paymentInfos = paymentInfos;
		
		this.nofMessage = 0;
		
		initPayment();
	}
	
	private void initPayment() throws NoNfcException, NfcNotEnabledException {
		NfcEventHandler nfcEventHandler;
		if (this.paymentType == PaymentType.REQUEST_PAYMENT)
			nfcEventHandler = nfcEventHandlerRequest;
		else
			nfcEventHandler = nfcEventHandlerSend;
		
		
		if (ExternalNfcTransceiver.isExternalReaderAttached(activity)) {
			nfcTransceiver = new ExternalNfcTransceiver(nfcEventHandler, userInfos.getUserId());
		} else {
			nfcTransceiver = new InternalNfcTransceiver(nfcEventHandler, userInfos.getUserId());
		}
		
		nfcTransceiver.enable(activity);
		//TODO: implement
	}
	
	private NfcEventHandler nfcEventHandlerRequest = new NfcEventHandler() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {
			switch (event) {
			case INIT_FAILED:
			case COMMUNICATION_ERROR:
			case ERROR_REPORTED:
				// TODO: abort everything
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, null);
				nfcTransceiver.disable(activity);
				break;
			case CONNECTION_LOST: // do nothing, because new session can be initiated automatically!
			case MESSAGE_RETURNED: // do nothing, concerns only the HCE
			case MESSAGE_SENT:
				nofMessage++;
				break;
			case INITIALIZED:
				nfcTransceiver.transceive(new PaymentMessage(PaymentMessage.DEFAULT, getInitMessage()).getData());
				break;
			case MESSAGE_RECEIVED:
				// TODO: send next message
				PaymentMessage response = (PaymentMessage) object;;
				if (response.isError()) {
					//TODO: implement
				}
				
				switch (nofMessage) {
				case 1:
					try {
						PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, response.getData());
						PaymentRequest paymentRequestPayee = new PaymentRequest(userInfos.getSignatureAlgorithm(), userInfos.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfos.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentRequestPayer.getTimestamp());
						if (!paymentRequestPayer.requestsIdentic(paymentRequestPayee)) {
							//TODO: implement
						}
						
						paymentRequestPayee.sign(userInfos.getPrivateKey());
						
						ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
						paymentEventHandler.handleMessage(PaymentEvent.FORWARD_TO_SERVER, spr.encode());
					} catch (Exception e) {
						//TODO: implement
					}
					break;
				case 2:
					//TODO: wait for ack
					nfcTransceiver.disable(activity);
					//TODO: differentiate between success/failure?
					paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, null);
					break;
				}
				break;
			}
		}
		
		private byte[] getInitMessage() {
			int index = 0;
			byte[] result = new byte[userInfos.getUsername().length()+1+8];
			
			byte[] username = userInfos.getUsername().getBytes(Charset.forName("UTF-8"));
			
			result[index++] = (byte) userInfos.getUsername().length();
			for (byte b : username) {
				result[index++] = b;
			}
			result[index++] = paymentInfos.getCurrency().getCode();
			byte[] longAsBytes = Utils.getLongAsBytes(paymentInfos.getAmount());
			for (byte b : longAsBytes) {
				result[index++] = b;
			}
			
			return result;
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
			
			Signature sig = Signature.getInstance(paymentResponse.getSignatureAlgorithm().getSignatureAlgorithm());
			sig.initVerify(serverInfos.getPublicKey());
			sig.update(paymentResponse.getPayload());
			
			boolean signatureValid = sig.verify(paymentResponse.getSignature());
			if (!signatureValid) {
				//TODO: handle this
			} else {
				byte[] encode = serverPaymentResponse.getPaymentResponsePayer().encode();
				nfcTransceiver.transceive(new PaymentMessage(PaymentMessage.DEFAULT, encode).getData());
			}
		} catch (Exception e) {
			//TODO: implement
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
