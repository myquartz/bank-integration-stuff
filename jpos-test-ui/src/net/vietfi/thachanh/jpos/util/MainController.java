package net.vietfi.thachanh.jpos.util;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.event.ActionEvent;
import javafx.stage.FileChooser;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOFieldPackager;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;

import java.io.*;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MainController {
    private static final String inputErrorStyle = "-fx-background-color: yellow";

    private final Properties props = new Properties();

    @FXML
    private TextField channelConnectInput;
    @FXML
    private CheckBox singleRound;
    @FXML
    private Button connectBtn;

    @FXML
    private ComboBox<File> fileList;

    @FXML
    private ComboBox<String> deList;

    @FXML
    private Button removeDEBtn;

    @FXML
    private Label labelFieldNo;
    @FXML
    private Label labelFieldName;
    @FXML
    private TextField inputDeValue;

    @FXML
    private CheckBox inputAutoGen;

    MessageDataElement currSelDE;

    @FXML
    private TextField msgMTI;

    @FXML
    private ListView<MessageDataElement> msgDeList;

    @FXML
    public ProgressBar progressBar;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @FXML
    private Button sendBtn;

    @FXML
    public ListView<ISOMsg> msgRespList;

    @FXML
    private TextArea messageTextArea;

    private ISO87APackager iso1987;

    private boolean generateCharRef = false;
    private boolean saved = true;
    private File lastDirectory = null;

    void populateInitData(File workFile, File isoConfig) {
        MainService mainService = MainService.getInstance();
        if (workFile != null && workFile.exists()) {
            try (InputStream fi = new FileInputStream(workFile)) {
                props.load(fi);
            } catch (IOException e) {
                messageTextArea.setText(e.toString());
                return;
            }
            String lastFiles = props.getProperty("last.messages.files");
            if (lastFiles != null && !lastFiles.isEmpty()) {
                ObservableList<File> items = fileList.getItems();
                items.addAll(Arrays.stream(lastFiles.split("\\s*;\\s*", 11))
                        .limit(10).map(File::new).collect(Collectors.toList()));
            }

            channelConnectInput.setText(props.getProperty("connect.host.port", ""));
            singleRound.setSelected(Boolean.parseBoolean(props.getProperty("connect.on.demand", "false")));
            mainService.setTraceNbr(Integer.parseInt(props.getProperty("last.trace.nbr", "0")));
            lastDirectory = new File(Paths.get(props.getProperty("last.directory", ".")).toAbsolutePath().normalize().toString());
            this.generateCharRef = Boolean.parseBoolean(props.getProperty("iso8583.generate.character", "false"));
        }
        else {
            lastDirectory = new File(".");
        }

        //load ISO8583 fields
        Properties isoprop = new Properties();
        if (isoConfig != null && isoConfig.exists()) {
            try (InputStream fi = new FileInputStream(isoConfig)) {
                isoprop.load(fi);
            } catch (IOException e) {
                messageTextArea.setText(e.toString());
                return;
            }
        }

        this.iso1987 = mainService.getPackager(isoprop);
        ObservableList<String> deItems = deList.getItems();
        for (int i = 2; i <= 128; i++)
            deItems.add(String.format("%03d", i) + ": " + iso1987.getFieldPackager(i).getDescription());

        channelConnectInput.textProperty().addListener((ob,o,n) -> {
            if(n != null)
                updateServiceConnect();
        });
        singleRound.selectedProperty().addListener((ob, o, n) -> {
            if(n != null) {
                connectBtn.setDisable(n);
                if(!n && mainService.isConnected())
                    switchConnectOn(true);
            }
        });

        fileList.getSelectionModel().selectedItemProperty().addListener((ob, o, n) -> {
            if (o != n && n != null) {
                if(!n.exists()) {
                    messageTextArea.setText("File "+n+" is not found on disk, it will be removed from the recent list.");
                    Platform.runLater(() -> fileList.getItems().remove(n));
                    return;
                }
                if (!saved) {
                    Alert a = new Alert(Alert.AlertType.CONFIRMATION);
                    a.setHeaderText("Load confirmation");
                    a.setContentText("Current messages has not saved yet. Do you want to load new file without saving the old?" +
                            " If you choose so the current messages will be lost.");
                    a.showAndWait()
                            .filter(response -> response == ButtonType.OK || response == ButtonType.YES)
                            .ifPresent((b -> runReadMessageTask(n, false)));
                    return;
                }
                runReadMessageTask(n, false);
            }
        });
        msgDeList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            currSelDE = newValue;
            switchDeEdit();
        });
        msgRespList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null)
                messageTextArea.setText(newValue.toString() + msgDetailToString(newValue));
        });
        msgMTI.textProperty().addListener((o, ov, nv) -> {
            if (nv != null && !nv.equals(ov)) {
                switchSendBtnOnData();
            }
        });
        inputDeValue.textProperty().addListener((observable, oldValue, newValue) -> {
            if (currSelDE != null)
                currSelDE.setValue(newValue);
        });
        inputDeValue.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (currSelDE == null || oldValue == null || !oldValue || newValue == null || newValue) {
                return;
            }
            msgDeList.refresh();
        });
        inputAutoGen.selectedProperty().addListener((ob, o, n) -> {
            if (currSelDE != null && n != null) {
                currSelDE.setAutoGen(n);
                switchDeEdit();
            }
        });
        //event handlers
        mainService.setOnRunning((e) -> {
            String msg = mainService.getMessage();
            if (msg != null && !msg.isEmpty())
                messageTextArea.setText(msg);
            else
                messageTextArea.setText("Waiting...");
            progressBar.setProgress(0.6);
        });
        mainService.setOnReady((e) -> {
            String msg = mainService.getMessage();
            if (msg != null && !msg.isEmpty()) {
                messageTextArea.setText(msg);
            } else
                messageTextArea.setText("Ready");
        });

        mainService.setOnSucceeded((e) -> {
            progressBar.setProgress(1);
            delayOffProgressBar();
            switchSendBtn(false);
            ISOMsg recv = (ISOMsg) e.getSource().getValue();
            if (recv == null || singleRound.isSelected()
                    || !mainService.isConnected()) {
                mainService.stopChannel();
                switchConnectOff();
                if (recv == null)
                    messageTextArea.setText(messageTextArea.getText() + "\nStopped");
            }
            if (recv != null) {
                StringBuilder sb = new StringBuilder(recv.toString());
                msgRespList.getItems().add(recv);
                sb.append(msgDetailToString(recv));
                messageTextArea.setText(sb.toString());
            }
            if(singleRound.isSelected())
                mainService.stopChannel();
            saved = false;
        });

        mainService.setOnFailed((e) -> {
            progressBar.setVisible(false);
            switchSendBtn(false);
            Throwable exp = mainService.getException();
            if (exp != null) {
                messageTextArea.setText(messageTextArea.getText() + "\n" + exp.toString());
            }
            if (!(exp instanceof ISOException) || !mainService.isConnected() || singleRound.isSelected()) {
                switchConnectOff();
                mainService.stopChannel();
            }
        });

        //initial state
        if (checkForConnInput())
            updateServiceConnect();
        connectBtn.setDisable(singleRound.isSelected());
        switchConnectOff();
        switchDeEdit();
        switchSendBtnOnData();
    }

    Task<Boolean> createConnectTask(MainService mainService) {
        Task<Boolean> connTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    mainService.startChannel();
                    updateMessage("Connected.");
                    return mainService.isConnected();
                } catch (IOException e) {
                    mainService.stopChannel();
                    throw e;
                }
            }
        };

        connTask.setOnRunning((e) -> {
            messageTextArea.setText("Connecting..");
            switchConnectOn(false);
            sendBtn.setDisable(true);//disable only send
        });
        connTask.setOnFailed((e)-> {
            Throwable exp = connTask.getException();
            if (exp != null)
                messageTextArea.setText(exp.toString());
            switchConnectOff();
            switchSendBtn(false);
            progressBar.setVisible(false);
        });

        connTask.setOnSucceeded((e)-> {
            String msg = connTask.getMessage();
            if (msg != null) {
                messageTextArea.setText(msg);
            }
            if (connTask.getValue() != null && connTask.getValue()) {
                switchConnectOn(true);
                if(!running.get())
                    switchSendBtn(false);
            }
            else {
                switchConnectOff();
                switchSendBtn(false);
            }
        });
        return connTask;
    }

    void saveLastData(File workFile) {
        if(workFile != null) {
            MainService mainService = MainService.getInstance();
            props.setProperty("connect.host.port",channelConnectInput.getText());
            props.setProperty("connect.on.demand", singleRound.isSelected() ? "true":"false");
            if(fileList.getItems().size()>0) {
                String lastFiles = fileList.getItems().stream()
                        .map((f) -> {
                            try {
                                return f.getCanonicalPath();
                            } catch (IOException e) {
                                e.printStackTrace();
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(";"));
                props.setProperty("last.messages.files", lastFiles);
            }
            props.setProperty("last.trace.nbr", String.valueOf(mainService.getTraceNbr()));
            props.setProperty("last.directory", lastDirectory.getAbsolutePath());

            try (OutputStream fo = new FileOutputStream(workFile)) {
                props.store(fo, "JPOS ISO8583 Test Tool, written by AnhTT96 of FIS, Oct 2020.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    boolean checkForConnInput() {
        String connInfo = channelConnectInput.getText();
        if(connInfo.isEmpty() || !connInfo.matches("^[a-zA-Z0-9.\\-]+:\\d+$")) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText("Wrong connection information");
            a.setContentText("Connection to \""+connInfo+"\" is not valid, must be IP:port or hostname:port.");
            final String old = channelConnectInput.getStyle();
            channelConnectInput.setStyle(inputErrorStyle);
            a.setOnCloseRequest((e) -> {
                channelConnectInput.setStyle(old);
                channelConnectInput.requestFocus();
            });
            a.show();
            return false;
        }
        return true;
    }

    void updateServiceConnect() {
        if(running.get())
            return;
        String connInfo = channelConnectInput.getText();
        int pi = connInfo.indexOf(":");
        if(pi<=0)
            return;
        MainService mainService = MainService.getInstance();
        mainService.setSendingMessage(null);
        mainService.setHost(connInfo.substring(0, pi));
        if(pi+1 < connInfo.length())
            try {
                mainService.setPort(Integer.parseInt(connInfo.substring(pi + 1)));
            }
            catch (NumberFormatException e) {
                messageTextArea.setText(e.toString());
            }
    }

    private static String msgDetailToString(ISOMsg msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n")
                .append("---- Begin of data elements ---");
        //show from 2
        for(int c = 2; c<= msg.getMaxField();c++) {
            if(msg.hasField(c))
                sb.append("\n")
                        .append(String.format("DE%03d=", c)).append(msg.getString(c));
        }
        sb.append("\n")
                .append("---- End of data elements -----");
        return sb.toString();
    }

    void switchConnectOn(boolean isConnected) {
        singleRound.setDisable(true);
        channelConnectInput.setDisable(true);
        if(singleRound.isSelected())
            return;
        if(isConnected) {
            connectBtn.setDisable(false);
            connectBtn.setText("Disconnect");
        }
        else { //connecting
            connectBtn.setDisable(true);
            connectBtn.setText("Connecting");
        }
    }

    void delayOffProgressBar() {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).thenRun(() ->
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressBar.setProgress(0);
                })
        );
    }

    void progressBarIncrease() {
        if(!running.get())
            return;
        if(!progressBar.isVisible()) {
            running.set(false);
            return;
        }
        double d = progressBar.getProgress();
        if (d<0.9)
            progressBar.setProgress(d+0.01);
    }

    void switchConnectOff() {
        singleRound.setDisable(false);
        channelConnectInput.setDisable(false);
        if(singleRound.isSelected())
            return;
        connectBtn.setDisable(false);
        connectBtn.setText("Connect");
    }

    @FXML
    protected void connectBtnClick(ActionEvent event) {
        System.out.println(event);
        if(!checkForConnInput())
            return;
        if(running.get())
            return;
        MainService mainService = MainService.getInstance();
        if(mainService.isConnected()) {
            try {
                mainService.stopChannel();
                messageTextArea.setText("Disconnected.");
            }
            finally {
                switchConnectOff();
                switchSendBtn(false);
            }
        }
        else {
            updateServiceConnect();
            if(!singleRound.isSelected()) {
                switchConnectOn(false);
                messageTextArea.setText("Connecting");
                Task<Boolean> connTask = createConnectTask(mainService);
                Main.executor.execute(connTask);
            }
        }
    }

    boolean isNotCorrectMTI(String mti) {
        return !mti.matches("^\\d{4}$");
    }

    void switchSendBtnOnData() {
        if(running.get())
            return;
        sendBtn.setDisable(isNotCorrectMTI(msgMTI.getText())
                || msgDeList.getItems().isEmpty());
    }

    void switchSendBtn(boolean sending) {
        if(sending) {
            running.set(true);
            sendBtn.setDisable(true);
        }
        else {
            running.set(false);
            sendBtn.setDisable(isNotCorrectMTI(msgMTI.getText())
                    || msgDeList.getItems().isEmpty());
        }
    }

    public void doSend(ActionEvent event) {
        if(!checkForConnInput())
            return;
        if(isNotCorrectMTI(msgMTI.getText())) {
            messageTextArea.setText("MTI must be 4 digit (e.g. 0800)");
            return;
        }
        if(running.get())
            return;
        progressBar.setProgress(0.1);
        progressBar.setVisible(true);
        switchSendBtn(true);
        MainService mainService = MainService.getInstance();
        try {
            ISOMsg m = new ISOMsg();
            m.setMTI(msgMTI.getText());

            ObservableList<MessageDataElement> list = msgDeList.getItems();
            //each field
            ListIterator<MessageDataElement> it = list.listIterator();

            LocalDateTime now = LocalDateTime.now();
            while (it.hasNext()) {
                MessageDataElement n = it.next();
                switch (n.getFldNo()) {
                    case 7: //transmission date & time
                        if(n.isAutoGen()) {
                            m.set(n.getFldNo(), String.format("%02d%02d%02d%02d%02d",now.getMonthValue(),now.getDayOfMonth(),
                                    now.getHour(),now.getMinute(),now.getSecond()));
                        }
                        else
                            m.set(n.getFldNo(), n.getValue());
                        break;
                    case 11: //trace number
                        if(n.isAutoGen())
                            m.set(n.getFldNo(), String.format("%06d",mainService.nextTraceNbr()));
                        else
                            m.set(n.getFldNo(), n.getValue());
                        break;
                    case 12: //local time
                        if(n.isAutoGen())
                            m.set(n.getFldNo(), String.format("%02d%02d%02d",now.getHour(),now.getMinute(),now.getSecond()));
                        else
                            m.set(n.getFldNo(), n.getValue());
                        break;
                    case 13: //local date
                        if(n.isAutoGen())
                            m.set(n.getFldNo(), String.format("%02d%02d",now.getMonthValue(),now.getDayOfMonth()));
                        else
                            m.set(n.getFldNo(), n.getValue());
                        break;
                    case 37://
                        if(n.isAutoGen()) {
                            Random rnd = new Random(mainService.getTraceNbr());
                            StringBuilder sb = new StringBuilder();
                            if(generateCharRef) {
                                sb.append(Character.toChars(rnd.nextInt(26) + 'A')[0]);
                                sb.append(Character.toChars(rnd.nextInt(26) + 'A')[0]);
                                sb.append(String.format("%010d", Math.abs(rnd.nextLong() % 1000000000L)));
                            }
                            else {
                                sb.append(String.format("%012d", Math.abs(rnd.nextLong() % 1000000000L)));
                            }
                            m.set(n.getFldNo(), sb.toString());
                        } else
                            m.set(n.getFldNo(), n.getValue());
                        break;
                    default:
                        m.set(n.getFldNo(), n.getValue());
                        break;
                }
            }
            mainService.setSendingMessage(m);
        } catch (RuntimeException | ISOException e) {
            messageTextArea.setText(e.toString());
            progressBar.setVisible(false);
            switchSendBtn(false);
            return;
        }

        mainService.reset();
        msgRespList.getItems().add(mainService.getSendingMsg());
        if(!mainService.isConnected()) {
            Task<Boolean> connTask = createConnectTask(mainService);
            CompletableFuture.runAsync(connTask).thenRun(() -> {
                if(mainService.isConnected()) {
                    Platform.runLater(() -> {
                        progressBar.setProgress(0.5);
                        mainService.start();
                    });
                }
                else
                    Platform.runLater(() -> progressBar.setVisible(false));
            });
        }
        else {
            progressBar.setProgress(0.4);
            mainService.start();
        }
    }

    void switchDeEdit() {
        if(currSelDE != null) {
            labelFieldNo.setText(String.format("DE%03d=", currSelDE.getFldNo()));
            ISOFieldPackager fld = currSelDE.getFld();
            labelFieldName.setText(fld.getDescription()
                +" (max length=" + fld.getMaxPackedLength()+")");
            inputDeValue.setText(currSelDE.getValue());
            removeDEBtn.setDisable(false);
            switch (currSelDE.getFldNo()) {
                case 7://Transmission date & time
                case 11://System trace audit number (STAN)
                case 12://Local transaction time (hhmmss)
                case 13://Local transaction date (MMDD)
                case 37://Retrieval reference number
                    inputAutoGen.setDisable(false);
                    inputAutoGen.setSelected(currSelDE.isAutoGen());
                    inputDeValue.setDisable(currSelDE.isAutoGen());
                    break;
                default:
                    inputDeValue.setDisable(false);
                    inputAutoGen.setDisable(true);
            }
        }
        else {
            labelFieldNo.setText("Field #");
            labelFieldName.setText("Field name");
            inputDeValue.setText("");
            inputDeValue.setDisable(true);
            removeDEBtn.setDisable(true);
            inputAutoGen.setDisable(true);
        }
    }

    public void addDEtoMsg(ActionEvent event) {
        int i = deList.getSelectionModel().getSelectedIndex();
        if(i<0)
            return;
        String selected = deList.getItems().get(i);
        int fldNo = Integer.parseInt(selected.substring(0,3));
        ObservableList<MessageDataElement> list = msgDeList.getItems();

        MessageDataElement.Builder mb = MessageDataElement.newBuilder(this.iso1987);
        mb.setFldNo(fldNo);
        mb.setValue("");

        switch (fldNo) {
            case 11:
            case 37:
                mb.setAutoGen(true);
        }
        //search field no
        ListIterator<MessageDataElement> it = list.listIterator();
        i = 0;
        while (it.hasNext()) {
            MessageDataElement n = it.next();
            if(n.getFldNo() == fldNo) {//already exists
                msgDeList.getSelectionModel().select(i);
                messageTextArea.setText("Already added "+fldNo);
                currSelDE = n;
                switchDeEdit();
                return;
            }
            if (n.getFldNo()>fldNo) { //insert
                saved = false;
                it.previous();
                currSelDE = mb.newInstance();
                it.add(currSelDE);
                msgDeList.getSelectionModel().select(i);
                switchDeEdit();
                inputDeValue.requestFocus();
                return;
            }
            i++;
        }
        saved = false;
        currSelDE = mb.newInstance();
        list.add(currSelDE);
        msgDeList.getSelectionModel().select(i);
        switchDeEdit();
        switchSendBtnOnData();
        inputDeValue.requestFocus();
    }

    public void clickDeList(MouseEvent event) {
        if(event.getClickCount()==2) {
            System.out.println("select for edit "+event);
            MessageDataElement e = msgDeList.getSelectionModel().getSelectedItem();
            if(e != null && currSelDE != null && e.getFldNo() == currSelDE.getFldNo())
                inputDeValue.requestFocus();
        }
    }

    public void removeDEItem(ActionEvent event) {
        int sel = msgDeList.getSelectionModel().getSelectedIndex();
        if(sel>=0) {
            ObservableList<MessageDataElement> list = msgDeList.getItems();
            list.remove(sel);
            currSelDE = msgDeList.getSelectionModel().getSelectedItem();
            msgDeList.refresh();
            switchDeEdit();
            switchSendBtnOnData();
        }
    }

    public void saveMessageFile(File msgFile, boolean log, boolean request, boolean response,
                                ISOMsg... specifics) {
        final List<ISOMsg> list;
        if(specifics.length>0)
            list = Arrays.asList(specifics);
        else
            list = msgRespList.getItems();

        if(log) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(msgFile)))) {
                int savedCount = 0;
                for (ISOMsg msg : list) {
                    try {
                        msg.setPackager(this.iso1987);
                        if (msg.isIncoming())
                            writer.write("In:  ");
                        else if (msg.isOutgoing())
                            writer.write("Out: ");
                        else if (msg.isRetransmission())
                            writer.write("Rtx: ");
                        else
                            writer.write("Msg: ");
                        writer.write(msg.toString());
                        String msgStr = new String(msg.pack());
                        writer.write(String.format("\nPacked (length %4d): \"",msgStr.length()));
                        writer.append(String.format("%04d", msgStr.length())).write(msgStr);
                        writer.write("\"");
                        writer.append(msgDetailToString(msg));
                        //end of message
                        writer.write("\n\n");
                        savedCount++;
                    } catch (ISOException | IOException e) {
                        messageTextArea.setText("Error at msg#" + savedCount + ": " + e.toString());
                        return;
                    }
                }
                messageTextArea.setText("Saved " + savedCount + " log entry/entries.");
                saved = true;
            } catch (RuntimeException | IOException e) {
                messageTextArea.setText(e.toString());
            }
        }
        else {
            try(BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(msgFile))) {
                int savedCount = 0;
                for(ISOMsg msg:list) {
                    try {
                        if ((request & msg.isRequest()) || (response & msg.isResponse())) {
                            byte[] buf = msg.pack();
                            output.write(String.format("%04d", buf.length).getBytes());
                            output.write(buf);
                            if (request && response) //new line for request/response at the same time
                                output.write(Character.LINE_SEPARATOR);
                            savedCount++;
                        }
                    } catch (ISOException | IOException e) {
                        messageTextArea.setText("Error at msg#" + savedCount + ": " + e.toString());
                        return;
                    }
                }
                messageTextArea.setText("Saved " + savedCount + " message(s).");
            } catch (RuntimeException | IOException e) {
                messageTextArea.setText(e.toString());
            }
        }
    }

    void runReadMessageTask(File msgFile, boolean addRecent) {
        Task<List<ISOMsg>> loadTask = new Task<List<ISOMsg>>() {
            @Override
            protected List<ISOMsg> call() throws Exception {
                return readMessageFile(iso1987, msgFile, null);
            }
        };
        loadTask.setOnSucceeded((e) -> {
            List<ISOMsg> list = loadTask.getValue();
            ObservableList<ISOMsg> viewList = msgRespList.getItems();
            viewList.clear();
            if(list != null && !list.isEmpty()) {
                viewList.addAll(list);
                messageTextArea.setText("\nLoaded "+list.size()+" message(s)");
                if(addRecent)
                    this.addToRecent(msgFile);
                try {
                    this.updateToCurrent(list.iterator().next());
                } catch (ISOException isoException) {
                    messageTextArea.setText(
                            messageTextArea.getText()+"\n"+isoException.toString()
                    );
                }
            }
        });
        loadTask.setOnFailed((e) -> messageTextArea.setText("Load error:\n"+loadTask.getException().toString()));
        Main.executor.execute(loadTask);
        saved = true;
    }

    public static List<ISOMsg> readMessageFile(ISO87APackager iso1987, File msgFile, List<String> errorList) throws IOException {
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(msgFile)))) {
            final List<ISOMsg> list = new LinkedList<>();

            int line = 0;
            String l;
            while((l = reader.readLine()) != null) {
                line++;
                byte[] buf = l.getBytes();
                int pos = 0;
                while (pos+4<buf.length) {
                    //first 4 byte is length
                    int len = Integer.parseInt(new String(Arrays.copyOfRange(buf, pos, pos+4)));
                    if(len + pos + 4 > buf.length ) {
                        String errmsg = "Line["+line+"]: Invalid message length at "+pos;
                        if(errorList != null)
                            errorList.add(errmsg);
                        else
                            throw new RuntimeException(errmsg);
                        break;
                    }
                    ISOMsg msg = new ISOMsg();
                    try {
                        msg.setPackager(iso1987);
                        msg.unpack(Arrays.copyOfRange(buf, pos+4,pos+4+len));
                        list.add(msg);
                    } catch (ISOException e) {
                        String errmsg =  "Line["+line+"]: "+e.toString();
                        if(errorList != null)
                            errorList.add(errmsg);
                        else
                            throw new RuntimeException(errmsg);
                        break;
                    }
                    pos += len+4;
                }
            }

            return list;
        }
    }

    void updateToCurrent(ISOMsg msg) throws ISOException {
        ObservableList<MessageDataElement> mylist = msgDeList.getItems();
        mylist.clear();
        msgMTI.setText(msg.getMTI());
        for(int c = 2; c<=msg.getMaxField();c++)
            if(msg.hasField(c)) {
                MessageDataElement.Builder b = MessageDataElement.newBuilder(this.iso1987);
                b.setFldNo(c);
                b.setValue(msg.getString(c));
                mylist.add(b.newInstance());
            }
        switchSendBtnOnData();
    }

    void addToRecent(File msgFile) {
        lastDirectory = msgFile.getParentFile();
        ObservableList<File> recentList = fileList.getItems();
        recentList.remove(msgFile);
        recentList.add(0, msgFile);
        if (recentList.size() > 10)
            recentList.remove(9, recentList.size());
    }

    public void loadMsgFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Message File");
        fileChooser.setInitialDirectory(lastDirectory);
        File msgFile = fileChooser.showOpenDialog(((Button)event.getSource()).getScene().getWindow());
        if(msgFile != null && msgFile.exists() && msgFile.isFile()) {
            runReadMessageTask(msgFile, true);
        }
    }


    public void saveMessages(ActionEvent event) {
        if(msgRespList.getItems().isEmpty()) {
            messageTextArea.setText("Nothing to save");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save all messages");
        fileChooser.setInitialDirectory(lastDirectory);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Message Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File msgFile = fileChooser.showSaveDialog(((Button)event.getSource()).getScene().getWindow());
        if(msgFile != null)
            saveMessageFile(msgFile, false, true, true);
    }

    public void saveRequestMessages(ActionEvent event) {
        if(!msgRespList.getItems().stream().anyMatch(t -> {
            try {
                return t.isRequest();
            } catch (ISOException e) {
                e.printStackTrace();
                return false;
            }
        })) {
            messageTextArea.setText("Nothing to save");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save only request message");
        fileChooser.setInitialDirectory(lastDirectory);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Message Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File msgFile = fileChooser.showSaveDialog(((Button)event.getSource()).getScene().getWindow());
        if(msgFile != null)
            saveMessageFile(msgFile, false, true, false);
    }

    public void saveLog(ActionEvent event) {
        if(msgRespList.getItems().isEmpty()) {
            messageTextArea.setText("Nothing to save");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Log File");
        fileChooser.setInitialDirectory(lastDirectory);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Message Log Files", "*.log"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File msgFile = fileChooser.showSaveDialog(((Button)event.getSource()).getScene().getWindow());
        if(msgFile != null)
            saveMessageFile(msgFile, true, true, true);
    }

    public void clickMsgRespList(MouseEvent event) {
        if(event.getClickCount()==2) {
            System.out.println("save only specific: "+event);
            List<ISOMsg> msgList = msgRespList.getSelectionModel().getSelectedItems();
            if(msgList != null && !msgList.isEmpty()) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle(msgList.size() == 1 ? "Save message \""+msgList.get(0).toString()+"\""
                        : "Save "+msgList.size()+" messages");
                fileChooser.setInitialDirectory(lastDirectory);
                fileChooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Message Text Files", "*.txt"),
                        new FileChooser.ExtensionFilter("All Files", "*.*"));
                File msgFile = fileChooser.showSaveDialog(msgRespList.getScene().getWindow());
                if(msgFile != null)
                    saveMessageFile(msgFile, false, true, true, msgList.toArray(new ISOMsg[msgList.size()]));
            }
        }
    }

}
