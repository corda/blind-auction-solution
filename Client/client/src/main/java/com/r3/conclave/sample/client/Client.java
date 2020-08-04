package com.r3.conclave.sample.client;

import com.r3.conclave.client.EnclaveConstraint;
import com.r3.conclave.client.InvalidEnclaveException;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.mail.Curve25519KeyPairGenerator;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Random;

public class Client {

    static HttpURLConnection getConn;
    static HttpURLConnection postConn;

    public static void main(String[] args) throws Exception {

        Random rand = new Random();
        int[] bids = new int[5];
        int sequenceNumber = 0;

        //Send 5 bids with random numbers
        while(sequenceNumber < 5) {
            int currentBid = rand.nextInt(100);
            bids[sequenceNumber] = currentBid;
            System.out.println(
                    sendBid(currentBid,
                            "http://localhost:8080/sealed_bid_ra",
                            "http://localhost:8080/send_bid",
                            "S:4C8DA17C2264817E8380DB8CD7CA79145EED849BD33B442C88921418FBFB9B51 PROD:1 SEC:INSECURE",
                            "auction-1",
                            sequenceNumber++)
            );
        }

        System.out.println("All the bids were: " + Arrays.toString(bids));
    }

    /**
    * A method used to POST raw encrypted bytes to an enclave.
    *
    * @PARAM bid - the undisclosed amount the user is willing to pay
    * @PARAM raEndpoint - the path used to retrieve the remote attestation from the server
    * @PARAM postEndpoint - the path used to send a bid to the server
    * @PARAM attestationConstraint - constrain to a signing key along with the product ID
    * @PARAM sequenceNumber - increment for each bid sent to the server
    */
    public static String sendBid( int bid, String raEndpoint, String postEndpoint,  String attestationConstraint, String topic, int sequenceNumber) throws IOException {
        // Generate our own Curve25519 keypair so we can receive a response.
        KeyPair myKey = new Curve25519KeyPairGenerator().generateKeyPair();

        // Send a GET request to retrieve the remote attestation
        EnclaveInstanceInfo receivedRA = getRa(raEndpoint, attestationConstraint);

        // Create a mail object with the bid as a byte[]
        MutableMail mail = receivedRA.createMail(ByteBuffer.allocate(4).putInt(bid).array());
        mail.setSequenceNumber(sequenceNumber);
        mail.setPrivateKey(myKey.getPrivate());
        mail.setTopic(topic);

        // Encrypt the mail
        byte[] encryptedMail = mail.encrypt();

        System.out.println("Sending the encrypted mail to the host.");

        // Create a POST request to send the encrypted byte[] to Host server
        URL url = new URL(postEndpoint);
        postConn = (HttpURLConnection) url.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setRequestProperty("Content-Type", "image/jpeg");
        postConn.setDoOutput(true);

        try(OutputStream os = postConn.getOutputStream()) {
            os.write(encryptedMail, 0, encryptedMail.length);
        }

        String response;
        try {
            // Read the enclave's response given by the server
            byte[] encryptedReply = new byte[postConn.getInputStream().available()];
            postConn.getInputStream().read(encryptedReply);

            // Try to decrypt the response. If it is a proper MAIL object then it should work
            // else, we simply let the client know in a catch block that their bid was received.
            EnclaveMail replyMail = receivedRA.decryptMail(encryptedReply, myKey.getPrivate());
            response = "Received the last bid. \nThe winning bid was: " + ByteBuffer.wrap(replyMail.getBodyAsBytes()).getInt();

        }catch(Exception e){
            response = "Bid " + (sequenceNumber + 1) + " was received.";
        }finally {
            postConn.disconnect();
        }

        return response;
    }


    public static EnclaveInstanceInfo getRa(String raEndpoint, String attestationConstraint){
        EnclaveInstanceInfo attestation = null;
        try{
            URL url = new URL(raEndpoint);
            getConn = (HttpURLConnection) url.openConnection();
            getConn.setRequestMethod("GET");

            //check attestation
            byte[] buf = new byte[getConn.getInputStream().available()];
            getConn.getInputStream().read(buf);
            attestation = EnclaveInstanceInfo.deserialize(buf);
            EnclaveConstraint.parse(attestationConstraint).check(attestation);

        }catch(IOException | InvalidEnclaveException e){
            e.printStackTrace();
        }finally {
            getConn.disconnect();
        }
        return attestation;
    }
}
