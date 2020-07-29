package com.r3.conclave.sample.host;

import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.mail.EnclaveMail;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.xml.bind.DatatypeConverter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class HostController {

	private static final String template = "Hello, %s!";
	private final AtomicLong counter = new AtomicLong();

	@GetMapping("/greeting")
	public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
		return new Greeting(counter.incrementAndGet(), String.format(template, name));
	}

	@GetMapping(path="/remote_attestation", produces= "application/json")
	public Map<String, String> getRA() throws EnclaveLoadException {

		try {
			EnclaveHost.checkPlatformSupportsEnclaves(true);
			System.out.println("This platform supports enclaves in simulation, debug and release mode.");
		} catch (EnclaveLoadException e) {
			System.out.println("This platform currently only supports enclaves in simulation mode: " + e.getMessage());
		}


		try (EnclaveHost enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave")) {
			// We need the EPID Service Provider ID (SPID) and attestation key to able to perform remote attestation
			// You can sign-up from Intel's EPID page: https://api.portal.trustedservices.intel.com/EPID-attestation
			// These are not needed if the enclave is in simulation mode (as no actual attestation is done)
//			if (enclave.getEnclaveMode() != EnclaveMode.SIMULATION && !givenSpid.equals("World")) {
//				throw new IllegalArgumentException("You need to provide the SPID and attestation key as arguments for " +
//						enclave.getEnclaveMode() + " mode.");
//			}
			//System.out.println(args[0] + " and " + args[1]);
			OpaqueBytes spid = new OpaqueBytes(new byte[16]);
			String attestationKey = "mock-key";
			// Start it up. In future versions this API will take more parameters, which is why it's explicit.
			AtomicReference<byte[]> mailToSend = new AtomicReference<>();
			enclave.start(spid, attestationKey, new EnclaveHost.MailCallbacks() {
				@Override
				public void postMail(byte[] encryptedBytes, String routingHint) {
					mailToSend.set(encryptedBytes);
				}
			}
			);

			// The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
			final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
			final byte[] attestationBytes = attestation.serialize();
			//System.out.println("This attestation requires " + attestationBytes.length + " bytes.");

			// It has a useful toString method.
			//System.out.println(EnclaveInstanceInfo.deserialize(attestationBytes));

			// Here's how to check it matches the expected code hash but otherwise can be insecure (e.g. simulation mode).
			//
			// EnclaveConstraint constraint = EnclaveConstraint.parse("C:02fbdf9a91773af2eb1c20cdea3823ab62424a03f168d135e78ccc572cfe9190 SEC:INSECURE");
			// constraint.check(attestation);

			// !dlrow olleH      :-)
			//EnclaveInstanceInfo deserializedAttestation = EnclaveInstanceInfo.deserialize(attestationBytes);
			HashMap <String, String> map = new HashMap<>();
			map.put("bytes", Base64.getEncoder().encodeToString(attestationBytes));

			return map;
		}
	}

	@PostMapping(path = "/send_bid", consumes = "application/json", produces = "application/json")
	public String sendBid(@RequestBody String base64EncodedMail) throws EnclaveLoadException {

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
			byte [] mailBytes = DatatypeConverter.parseBase64Binary(base64EncodedMail);
			enclave.deliverMail(1, mailBytes);

			//get the enclave's response from the callback and convert to base64 to return via JSON
			String toSend = DatatypeConverter.printBase64Binary(requestToDeliver.get());
			return callEnclave(enclave, toSend);
		}
	}

	public static String callEnclave(EnclaveHost enclave, String input) {
		// We'll convert strings to bytes and back.
		final byte[] inputBytes = input.getBytes();

		// Enclaves in general don't have to give bytes back if we send data, but in this sample we know it always
		// will so we can just assert it's non-null here.
		final byte[] outputBytes = Objects.requireNonNull(enclave.callEnclave(inputBytes));
		return new String(outputBytes);
	}
}
