package vn.fis.anhtt96.jpos.util;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Application {

    static ExecutorService executor = null;
    MainController myController = null;

    @Override
    public void init() throws Exception {
        super.init();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        executor.shutdown();
        myController.saveLastData(new File(System.getProperty("user.home")+"/jpostest.properties"));
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        boolean defaultConf = true;
        Parameters params = this.getParameters();
        File ISOConf = null;
        if(params.getUnnamed().size() > 0) {
            ISOConf = new File(params.getUnnamed().get(0));
            if(ISOConf.exists() && ISOConf.isFile() && ISOConf.canRead()) {
                defaultConf = false;
            }
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("mainWindow.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("JPOS ISO8583 Test Tool");
        primaryStage.setScene(new Scene(root, 660, 620));
        if(defaultConf) {
            primaryStage.setOnShown((event -> {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setHeaderText("Configuration notification");
                a.setContentText("You are using default ISO8583 configuration, that may be defined by a config file and add name to the first parameter. See sample.conf to create a new.");
                a.showAndWait();
            }));
        }
        primaryStage.getIcons().add(new Image(this.getClass().getResourceAsStream("icon.png")));
        primaryStage.show();
        myController = loader.getController();
        if(defaultConf) {
            myController.populateInitData(new File(System.getProperty("user.home")+"/jpostest.properties"), null);
        }
        else
            myController.populateInitData(new File(System.getProperty("user.home")+"/jpostest.properties"), ISOConf);
        System.out.println("Scene started.");
    }

    public static void main(String[] args) { launch(args); }
}
