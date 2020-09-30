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
import java.net.MalformedURLException;
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

        // Generate our own Curve25519 keypair so we can receive a response.
        KeyPair myKey = new Curve25519KeyPairGenerator().generateKeyPair();

        // Send a GET request to retrieve the remote attestation
        EnclaveInstanceInfo receivedRA = getRa(
                "http://localhost:8080/sealed_bid_ra",
                "S:80F5583339078DF2C5DA345785D9D50A4ED54F8859639D8D82F005E7F2BCE7BC PROD:1 SEC:INSECURE");

        //Send 5 bids with random numbers
        while(sequenceNumber < 5) {
            int currentBid = rand.nextInt(100);
            bids[sequenceNumber] = currentBid;
            sendBid(currentBid,
                    receivedRA,
                    "http://localhost:8080/send_bid",
                    myKey,
                    "auction-1",
                    sequenceNumber++);
        }


        System.out.println("All the bids were: " + Arrays.toString(bids));
        System.out.println(getWinner(
                "http://localhost:8080/reveal_winner",
                receivedRA,
                myKey
        ));
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
    public static void sendBid( int bid, EnclaveInstanceInfo receivedRA, String postEndpoint, KeyPair myKey, String topic, int sequenceNumber) throws IOException {

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
            os.flush();
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            postConn.getResponseCode();
            postConn.disconnect();
        }
    }

    //Retrieve the remote attestation for the Enclave
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

    //Retrieve the winning bid
    public static String getWinner(String winnerEndpoint, EnclaveInstanceInfo receivedRA, KeyPair myKey){
        String response = "The winning bid was: ";
        try{
            URL url = new URL(winnerEndpoint);
            getConn = (HttpURLConnection) url.openConnection();
            getConn.setRequestMethod("GET");

            //check attestation
            byte[] buf = new byte[getConn.getInputStream().available()];
            getConn.getInputStream().read(buf);
            // Send a GET request to retrieve the remote attestation
            EnclaveMail replyMail = receivedRA.decryptMail(buf, myKey.getPrivate());
            response += ByteBuffer.wrap(replyMail.getBodyAsBytes()).getInt();

        }catch(IOException e){
            e.printStackTrace();
        }finally {
            getConn.disconnect();
        }
        return response;
    }
}
