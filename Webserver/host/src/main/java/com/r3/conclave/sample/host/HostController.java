package com.r3.conclave.sample.host;

import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class HostController {
	EnclaveHost enclave;
	AtomicReference<byte[]> requestToDeliver = new AtomicReference<>();
	List<byte[]> allBids = new ArrayList<>();

	@GetMapping(path="/status", produces= "application/json")
	public String greeting() {
		return "Up and running!";
	}

	// A GET endpoint used to retrieve the remote attestation.
	@GetMapping(path="/sealed_bid_ra")
	public byte[] get_sealed_bid_ra() throws EnclaveLoadException {

			// Load our enclave
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

		    return enclave.getEnclaveInstanceInfo().serialize();
	}

	// A POST endpoint which accepts raw encrypted bytes sent by a client to deliever to an enclave.
	@PostMapping(path = "/send_bid")
	public byte[] sendBid(@RequestBody byte[] bid){

			allBids.add(bid);

			// Don't run the enclave until we have all 5 bids.
			if (allBids.size() == 5){
				for (int i = 0; i < allBids.size(); i++){

					// Deliver each MAIL that the host has collected to the enclave.
					enclave.deliverMail(1, allBids.get(i));
				}

				// The enclave will give us some mail to reply with via the callback we passed to the start() method.
				byte[] reply = requestToDeliver.get();
				enclave.close();

				return reply;
			}else{
				// Return -1 to show that the auction is still running.
				return ByteBuffer.allocate(4).putInt(-1).array();
			}
	}
}