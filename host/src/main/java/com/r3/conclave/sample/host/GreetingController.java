package com.r3.conclave.sample.host;

import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class GreetingController {

	private static final String template = "Hello, %s!";
	private final AtomicLong counter = new AtomicLong();

	@GetMapping("/greeting")
	public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
		return new Greeting(counter.incrementAndGet(), String.format(template, name));
	}

	@GetMapping("/remoteAttestation")
	public String getRA() throws EnclaveLoadException {

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
			enclave.start(spid, attestationKey, null);

			// The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
			final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
			final byte[] attestationBytes = attestation.serialize();
			System.out.println("This attestation requires " + attestationBytes.length + " bytes.");

			// It has a useful toString method.
			System.out.println(EnclaveInstanceInfo.deserialize(attestationBytes));

			// Here's how to check it matches the expected code hash but otherwise can be insecure (e.g. simulation mode).
			//
			// EnclaveConstraint constraint = EnclaveConstraint.parse("C:02fbdf9a91773af2eb1c20cdea3823ab62424a03f168d135e78ccc572cfe9190 SEC:INSECURE");
			// constraint.check(attestation);

			// !dlrow olleH      :-)

			return callEnclave(enclave, "Hello world!").toString();
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
