package net.vietfi.thachanh.jpos.util;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.jpos.iso.*;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.ISO87APackager;
import org.jpos.tlv.packager.PackagerErrorHandler;
import org.jpos.util.LogSource;
import org.jpos.util.Logger;
import org.jpos.util.SimpleLogListener;

import java.io.IOException;
import java.util.Properties;

public class MainService extends Service<ISOMsg> {
    private static final Boolean mainLock = new Boolean(true);
    private static MainService mainService = null;

    private static class MyPackagerErrorHandler implements PackagerErrorHandler {
        String data = null;
        String errorMessage = null;

        @Override
        public void handlePackError(ISOComponent m, ISOException e) {
            data = m.toString();
            errorMessage = e.getMessage();
        }

        @Override
        public void handleUnpackError(ISOComponent isoComponent, byte[] msg, ISOException e) {
            data = new String(msg);
            errorMessage = e.getMessage();
        }

        public String getData() {
            return data;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static MainService getInstance() {
        if(mainService == null) {
            synchronized (mainLock) {
                if(mainService == null) {
                    mainService = new MainService();
                    mainService.logger.addListener (new SimpleLogListener(System.out));
                }
            }
        }
        return mainService;
    }

    private ISO87APackager iso1987packer;

    private ISOChannel channel = null;
    private int traceNbr = 0;
    private String host = null;
    private int port = 0;
    Logger logger = new Logger();
    private ISOMsg sendingMsg = null;

    public ISO87APackager getPackager(Properties isoconfig) {
        if(iso1987packer != null)
            return iso1987packer;
        this.iso1987packer = new ISO87APackager();
        for(int c = 2; c <= 255; c++) {
            String f = "field." + c + ".type";
            if (isoconfig.containsKey(f)) {
                switch (isoconfig.getProperty(f)) {
                    case "LL_CHAR":
                        iso1987packer.setFieldPackager(c, new IFA_LLCHAR());
                        ISOFieldPackager fld = iso1987packer.getFieldPackager(c);
                        fld.setLength(99);
                        break;
                    case "LLL_CHAR":
                        iso1987packer.setFieldPackager(c, new IFA_LLLCHAR());
                        break;
                    case "LL_NUM":
                        iso1987packer.setFieldPackager(c, new IFA_LLNUM());
                        break;
                    case "LLL_NUM":
                        iso1987packer.setFieldPackager(c, new IFA_LLLNUM());
                        break;
                    case "LL_ABINARY":
                        iso1987packer.setFieldPackager(c, new IFA_LLABINARY());
                        break;
                    case "LLL_ABINARY":
                        iso1987packer.setFieldPackager(c, new IFA_LLLABINARY());
                        break;
                    case "LL_BINARY":
                        iso1987packer.setFieldPackager(c, new IFA_LLBINARY());
                        break;
                    case "LLL_BINARY":
                        iso1987packer.setFieldPackager(c, new IFA_LLLBINARY());
                        break;
                    default:
                        throw new IllegalArgumentException("Not support config of " + f + "=" + isoconfig.getProperty(f));
                }
            }
        }
        return this.iso1987packer;
    }

    public void startChannel() throws IOException {
        if(channel != null && channel.isConnected()) {
            return;
        }

        channel = new ASCIIChannel(
                host, port, iso1987packer
        );

        ((LogSource)channel).setLogger (logger, "test-channel");
        channel.connect();
    }

    public void stopChannel() {
        if(channel != null) {
            try {
                if(channel.isConnected())
                    channel.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                channel = null;
            }
        }
    }

    public int getTraceNbr() {
        return traceNbr;
    }

    public int nextTraceNbr() {
        if(traceNbr>=999999)
            traceNbr = 0;
        return ++traceNbr;
    }

    public void setTraceNbr(int traceNbr) {
        this.traceNbr = traceNbr;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    protected Task<ISOMsg> createTask() {
        return new Task<ISOMsg>() {
            @Override
            protected ISOMsg call() throws Exception {
                if(!isConnected())
                    throw new IllegalStateException("Not connected");
                if(sendingMsg != null) { //must have a message
                    updateTitle("Work in-progress");
                    try {
                        updateMessage("Sending message");
                        channel.send(sendingMsg);
                        updateMessage("Wait for message");
                        ISOMsg recvMsg = channel.receive();
                        return recvMsg;
                    }
                    finally {
                        updateMessage("Finish");
                    }
                }
                return null;
            }
        };
    }

    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    public void setSendingMessage(ISOMsg m) {
        this.sendingMsg = m;
    }

    public ISOMsg getSendingMsg() {
        return sendingMsg;
    }

    public ISOChannel getChannel() {
        return this.channel;
    }
}
