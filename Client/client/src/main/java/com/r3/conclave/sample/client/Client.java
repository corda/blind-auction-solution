package com.r3.conclave.sample.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.r3.conclave.client.EnclaveConstraint;
import com.r3.conclave.client.InvalidEnclaveException;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.mail.Curve25519KeyPairGenerator;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.util.*;

public class Client {
    static HttpURLConnection getConn;
    static HttpURLConnection postConn;
    public static void main(String[] args) throws Exception {

        Random rand = new Random();
        List<Integer> bids = new ArrayList<>();
        int sequenceNumber = 0;
        while(sequenceNumber < 5) {
            int currentBid = rand.nextInt(100);
            bids.add(currentBid);
            System.out.println(
                    sendBid(currentBid,
                            "http://localhost:8080/sealed_bid_ra",
                            "http://localhost:8080/send_bid",
                            "S:0CE5DB6D03AD076F59884B9E6A3A0690AF81B88775744BBFFE06B03C3A4A2C5F PROD:1 SEC:INSECURE",
                            "auction-1",
                            sequenceNumber++)
            );
        }
        System.out.println("All the bids were: " + bids);
    }

    public static String sendBid( int bid, String raEndpoint, String postEndpoint,  String attestationConstraint, String topic, int sequenceNumber) throws IOException {
        // Generate our own Curve25519 keypair so we can receive a response.
        KeyPair myKey = new Curve25519KeyPairGenerator().generateKeyPair();
        EnclaveInstanceInfo receivedRA = getRa(raEndpoint, attestationConstraint);
        MutableMail mail = receivedRA.createMail(ByteBuffer.allocate(4).putInt(bid).array());
        mail.setSequenceNumber(sequenceNumber);
        mail.setPrivateKey(myKey.getPrivate());
        mail.setTopic(topic);
        byte[] encryptedMail = mail.encrypt();

        System.out.println("Sending the encrypted mail to the host.");

        URL url = new URL(postEndpoint);
        postConn = (HttpURLConnection) url.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setRequestProperty("Content-Type", "application/json; utf-8");
        postConn.setRequestProperty("Accept", "application/json");
        postConn.setDoOutput(true);

        HashMap<String, String> postMap = new HashMap<>();
        postMap.put("mail", Base64.getEncoder().encodeToString(encryptedMail));
        String jsonInputString = new Gson().toJson(postMap);
        System.out.println(jsonInputString);

        try(OutputStream os = postConn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(postConn.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println("RESPONSE: " + response);
            HashMap<String, String> responseMap = parse(response.toString());
            byte[] encryptedReply = Base64.getDecoder().decode(responseMap.get("bytes"));
            String reply;
            try{
                EnclaveMail replyMail = receivedRA.decryptMail(encryptedReply, myKey.getPrivate());
                reply = new String(replyMail.getBodyAsBytes());
            }catch (Exception e) {
                reply = new String(encryptedReply);
            }finally {
                postConn.disconnect();
            }
            return reply;
        }
    }


    public static EnclaveInstanceInfo getRa(String endpoint, String attestationConstraint){
        EnclaveInstanceInfo attestation = null;
        try{
            BufferedReader reader;
            String line;
            StringBuffer responseContent = new StringBuffer();
            URL url = new URL(endpoint);
            getConn = (HttpURLConnection) url.openConnection();
            getConn.setRequestMethod("GET");

            int status = getConn.getResponseCode();
            if(status > 299){
                reader = new BufferedReader(new InputStreamReader(getConn.getErrorStream()));
            }else{
                reader = new BufferedReader(new InputStreamReader(getConn.getInputStream()));
            }
            while((line = reader.readLine()) != null){
                responseContent.append(line);
            }
            reader.close();
            HashMap<String, String> jsonMap = parse(responseContent.toString());
            byte[] attestationBytes = Base64.getDecoder().decode(jsonMap.get("bytes"));

            //check attestation
            attestation = EnclaveInstanceInfo.deserialize(attestationBytes);
            EnclaveConstraint.parse(attestationConstraint).check(attestation);

        }catch(IOException | InvalidEnclaveException e){
            e.printStackTrace();
        }finally {
            getConn.disconnect();
        }
        return attestation;
    }

    public static HashMap<String,String> parse(String responseBody){
        HashMap<String,String> map = new Gson().fromJson(responseBody, new TypeToken<HashMap<String, String>>(){}.getType());
        return map;
    }
}
