package com.r3.conclave.sample.enclave;

import com.r3.conclave.common.enclave.EnclaveCall;
import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
/**
 * Find the highest bid in a set of 5.
 */

public class SealedBidAuction extends Enclave implements EnclaveCall {
    List<byte[]> allBids = new ArrayList<>();

    @Override
    public byte[] invoke(byte[] bid) {
        allBids.add(bid);
        int highestBid = 0;
        int currentBid;
        for (int i = 0; i < allBids.size(); i++){
            currentBid = ByteBuffer.wrap(allBids.get(i)).getInt();
            if (currentBid > highestBid){
                highestBid = currentBid;
            }
        }
        return ByteBuffer.allocate(4).putInt(highestBid).array();
    }

    @Override
    protected void receiveMail(long id, EnclaveMail mail) {
        byte[] submitBid = invoke(mail.getBodyAsBytes());
        MutableMail reply = createMail(mail.getAuthenticatedSender(), submitBid);
        postMail(reply, null);
    }
}
