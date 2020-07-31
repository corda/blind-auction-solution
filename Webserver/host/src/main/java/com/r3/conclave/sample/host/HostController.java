package com.r3.conclave.sample.host;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class HostController {
	EnclaveHost enclave;
	AtomicReference<byte[]> requestToDeliver = new AtomicReference<>();
	List<byte[]> allBids = new ArrayList<>();
	int numOfBids;

	@GetMapping(path="/status", produces= "application/json")
	public String greeting() {
		return "Up and running!";
	}

	@GetMapping(path="/sealed_bid_ra", produces= "application/json")
	public Map<String, String> get_sealed_bid_ra() throws EnclaveLoadException {

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

			// The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
			final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
			final byte[] attestationBytes = attestation.serialize();

			HashMap <String, String> returnMap = new HashMap<>();
			returnMap.put("bytes", Base64.getEncoder().encodeToString(attestationBytes));

			return returnMap;
	}

	@PostMapping(path = "/send_bid", consumes = "application/json", produces = "application/json")
	public HashMap <String, String> sendBid(@RequestBody String jsonString){
		HashMap<String,String> map = new Gson().fromJson(jsonString, new TypeToken<HashMap<String, String>>(){}.getType());

			// Deliver it. The enclave will give us some mail to reply with via the callback we passed in
			// to the start() method.
			byte [] mailBytes = Base64.getDecoder().decode(map.get("mail"));
			HashMap <String, String> returnMap = new HashMap<>();
			allBids.add(mailBytes);
			numOfBids = allBids.size();
			if (allBids.size() == 5){
				for (int i = 0; i < allBids.size(); i++){
					enclave.deliverMail(1, allBids.get(i));
				}
				byte[] toSend = requestToDeliver.get();
				returnMap.put("bytes", Base64.getEncoder().encodeToString(toSend));
				enclave.close();
			}else{
				returnMap.put("bytes", Base64.getEncoder().encodeToString(
						("Number of bids left: " + (5 - numOfBids)).getBytes(StandardCharsets.UTF_8)));
			}
			return returnMap;
	}
}