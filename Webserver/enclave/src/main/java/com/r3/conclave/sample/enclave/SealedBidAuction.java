package com.r3.conclave.sample.enclave;

import com.r3.conclave.common.enclave.EnclaveCall;
import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SealedBidAuction extends Enclave implements EnclaveCall {
    List<byte[]> allBids = new ArrayList<>();

    @Override
    public byte[] invoke(byte[] bid) {
        String result;
        allBids.add(bid);
        int numOfBids = allBids.size();

        if (numOfBids == 5){
            int highestBid = 0;
            int currentBid;
            for (int i = 0; i < allBids.size(); i++){
                currentBid = ByteBuffer.wrap(allBids.get(i)).getInt();
                if (currentBid > highestBid){
                    highestBid = currentBid;
                }
            }
            result = "The winning bid is: " +  highestBid;
        }
        else{
            result = "Number of bids left: " + (5 - numOfBids);
        }

        return result.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void receiveMail(long id, EnclaveMail mail) {
        byte[] submitBid = invoke(mail.getBodyAsBytes());
        if (allBids.size() == 5){
            MutableMail reply = createMail(mail.getAuthenticatedSender(), submitBid);
            postMail(reply, null);
        }

    }
}
