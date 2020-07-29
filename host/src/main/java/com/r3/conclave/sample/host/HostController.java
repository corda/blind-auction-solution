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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class HostController {

	@GetMapping(path="/status", produces= "application/json")
	public String greeting() {
		return "Up and running!";
	}

	@GetMapping(path="/remote_attestation", produces= "application/json")
	public Map<String, String> getRA() throws EnclaveLoadException {

//		try {
//			EnclaveHost.checkPlatformSupportsEnclaves(true);
//			System.out.println("This platform supports enclaves in simulation, debug and release mode.");
//		} catch (EnclaveLoadException e) {
//			System.out.println("This platform currently only supports enclaves in simulation mode: " + e.getMessage());
//		}

		try (EnclaveHost enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave")) {

			OpaqueBytes spid = new OpaqueBytes(new byte[16]);
			String attestationKey = "mock-key";

			// Start it up.
			enclave.start(spid, attestationKey, null);

			// The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
			final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
			final byte[] attestationBytes = attestation.serialize();

			// Here's how to check it matches the expected code hash but otherwise can be insecure (e.g. simulation mode).
			//
			// EnclaveConstraint constraint = EnclaveConstraint.parse("C:02fbdf9a91773af2eb1c20cdea3823ab62424a03f168d135e78ccc572cfe9190 SEC:INSECURE");
			// constraint.check(attestation);

			HashMap <String, String> map = new HashMap<>();
			map.put("bytes", Base64.getEncoder().encodeToString(attestationBytes));

			return map;
		}
	}

	@PostMapping(path = "/send_bid", consumes = "application/json", produces = "application/json")
	public HashMap <String, String> sendBid(@RequestBody String jsonString) throws EnclaveLoadException {
//		int mailID = 1;
		HashMap<String,String> map = new Gson().fromJson(jsonString, new TypeToken<HashMap<String, String>>(){}.getType());

		try (EnclaveHost enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave")) {

			OpaqueBytes spid = new OpaqueBytes(new byte[16]);
			String attestationKey = "mock-key";
			// Start it up.
			AtomicReference<byte[]> requestToDeliver = new AtomicReference<>();
			enclave.start(spid, attestationKey, new EnclaveHost.MailCallbacks() {
				@Override
				public void postMail(byte[] encryptedBytes, String routingHint) {
					requestToDeliver.set(encryptedBytes);
				}
			});

			// Deliver it. The enclave will give us some mail to reply with via the callback we passed in
			// to the start() method.
			byte [] mailBytes = Base64.getDecoder().decode(map.get("mail"));
			enclave.deliverMail(1, mailBytes);
			byte[] toSend = requestToDeliver.get();
//			enclave.deliverMail(++mailID, mailBytes);
//			byte[] toSend = requestToDeliver.getAndSet(null);
			HashMap <String, String> returnMap = new HashMap<>();
			returnMap.put("bytes", Base64.getEncoder().encodeToString(toSend));
			return returnMap;
		}
	}
}
