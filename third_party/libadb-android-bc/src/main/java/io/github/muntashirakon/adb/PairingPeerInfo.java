// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0
package io.github.muntashirakon.adb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Parse only the authenticated server GUID, never a peer key or unbounded binary payload. */
final class PairingPeerInfo {
    static String deviceGuid(byte[] payload)throws IOException{
        if(payload==null||payload.length!=8192||payload[0]!=1)throw new IOException("Invalid pairing server identity type or size");
        int end=1;while(end<payload.length&&payload[end]!=0)end++;
        if(end==1||end==payload.length||end-1>255)throw new IOException("Invalid pairing server identity length");
        String guid=new String(payload,1,end-1,StandardCharsets.US_ASCII);
        if(!guid.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))throw new IOException("Invalid pairing server identity");
        return guid;
    }
}
