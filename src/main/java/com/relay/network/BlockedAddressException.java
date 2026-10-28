package com.relay.network;

import java.net.UnknownHostException;

public class BlockedAddressException extends UnknownHostException {

    public BlockedAddressException() {
        super("destination address is blocked");
    }
}