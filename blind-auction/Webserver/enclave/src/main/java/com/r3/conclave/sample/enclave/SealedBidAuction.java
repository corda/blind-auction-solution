package com.r3.conclave.sample.enclave;

import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
/**
 * Find the highest bid in a set of 5.
 */

public class SealedBidAuction extends Enclave {
    List<byte[]> allBids = new ArrayList<>();
    List<PublicKey> bidders = new ArrayList<>();
    int winner_index = -1;

    public byte[] invoke(byte[] bid) {
        allBids.add(bid);
        int highestBid = 0;
        if(allBids.size() == 5){
            int currentBid;
            for (int i = 0; i < allBids.size(); i++){
                currentBid = ByteBuffer.wrap(allBids.get(i)).getInt();
                if (currentBid > highestBid){
                    highestBid = currentBid;
                    winner_index = i;
                }
            }
        }

        return ByteBuffer.allocate(4).putInt(highestBid).array();
    }

    @Override
    protected void receiveMail(long id, String routingHint, EnclaveMail mail) {
        byte[] submitBid = invoke(mail.getBodyAsBytes());
        bidders.add(mail.getAuthenticatedSender());
        if(allBids.size() == 5){
            MutableMail reply = createMail(bidders.get(winner_index), submitBid);
            postMail(reply, null);
        }
    }
}