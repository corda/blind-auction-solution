package com.r3.conclave.sample.host;

import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;

@RestController
public class HostController {
	EnclaveHost enclave;
	AtomicReference<byte[]> requestToDeliver = new AtomicReference<>();
	int mailID = 0;
	byte[] winner;

	public HostController() throws EnclaveLoadException {
		System.out.println("Starting Enclave");
		enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.SealedBidAuction");

		OpaqueBytes spid = new OpaqueBytes(new byte[16]);
		String attestationKey = "mock-key";

		// Start the Enclave.
		enclave.start(spid, attestationKey, new EnclaveHost.MailCallbacks() {
			@Override
			public void postMail(byte[] encryptedBytes, String routingHint) {
				requestToDeliver.set(encryptedBytes);
			}
		});
	}

	// A GET endpoint used to check that the server is running.
	@GetMapping(path="/status")
	public String status() {
		return "Up and running";
	}

	// A GET endpoint used to retrieve the remote attestation.
	@GetMapping(path="/sealed_bid_ra")
	public byte[] get_sealed_bid_ra() {
		return enclave.getEnclaveInstanceInfo().serialize();
	}

	// A POST endpoint which accepts raw encrypted bytes sent by a client to deliver to an enclave.
	@PostMapping(path = "/send_bid")
	public void sendBid(@RequestBody byte[] bid){
		enclave.deliverMail(mailID++, bid);

		// The enclave will give us some mail to reply with via the callback we passed to the start() method.
		byte[] reply = requestToDeliver.get();

		//should never receive a reply unless a winner is found
		//if there is a reply then set the winner and close the enclave.
		if(reply != null){
			winner = reply;
			enclave.close();
		}
	}

	// A GET endpoint used to retrieve the remote attestation.
	@GetMapping(path="/reveal_winner")
	public byte[] get_winner() {
		return winner;
	}
}