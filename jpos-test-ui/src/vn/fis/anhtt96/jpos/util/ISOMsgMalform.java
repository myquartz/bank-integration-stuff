package vn.fis.anhtt96.jpos.util;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

public class ISOMsgMalform extends ISOMsg {

    final String data;
    final String errorMsg;

    public ISOMsgMalform(String data, String errorMsg) {
        this.data = data;
        this.errorMsg = errorMsg;
    }

    @Override
    public byte[] pack() throws ISOException {
        return data.getBytes();
    }

    @Override
    public String toString() {
        return "Error parsing:\n"+errorMsg+"\nof message: "+data;
    }
}
