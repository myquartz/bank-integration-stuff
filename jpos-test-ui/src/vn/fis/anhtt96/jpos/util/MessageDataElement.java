package vn.fis.anhtt96.jpos.util;

import org.jpos.iso.ISOFieldPackager;
import org.jpos.iso.packager.ISO87APackager;

public class MessageDataElement {
    private final ISOFieldPackager fld;
    private final int fldNo;
    private String value;
    private boolean autoGen;

    private MessageDataElement(int fldNo, ISOFieldPackager fld) {
        this.fldNo = fldNo;
        this.fld = fld;
    }

    public static Builder newBuilder(ISO87APackager packager) {
        return new Builder(packager);
    }

    public static class Builder {
        private final ISO87APackager packager;
        int fldNo = 0;
        private String value;
        private boolean autoGen;

        public MessageDataElement newInstance() {
            if(fldNo < 2)
                throw new IllegalStateException("No FldNo set");
            MessageDataElement m = new MessageDataElement(fldNo, packager.getFieldPackager(fldNo));
            if(autoGen)
                m.setAutoGen(autoGen);
            m.setValue(value);
            return m;
        }

        public Builder(ISO87APackager packager) {
            this.packager = packager;
        }

        public int getFldNo() {
            return fldNo;
        }

        public void setFldNo(int fldNo) {
            this.fldNo = fldNo;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isAutoGen() {
            return autoGen;
        }

        public void setAutoGen(boolean autoGen) {
            this.autoGen = autoGen;
        }
    }

    public int getFldNo() {
        return fldNo;
    }

    public ISOFieldPackager getFld() {
        return fld;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isAutoGen() {
        return autoGen;
    }

    public void setAutoGen(boolean autoGen) {
        this.autoGen = autoGen;
    }

    @Override
    public String toString() {
        return String.format("%03d", fldNo) +
                "='" + value + "'";//+" ("+fld.getDescription()+")";
    }
}
