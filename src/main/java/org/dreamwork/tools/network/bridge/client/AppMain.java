package org.dreamwork.tools.network.bridge.client;

import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by seth.yang on 2019/12/20
 */
public class AppMain extends Application {
    @FXML private Button btnNew;
    @FXML private Button btnRemove;
    @FXML private Button btnEdit;
    @FXML private Button btnConnect;
    @FXML private ListView serverList;

    public static void main (String[] args) {
        launch (args);
    }

    @Override
    public void start (Stage stage) throws IOException {
        ResourceBundle bundle = ResourceBundle.getBundle ("value.strings");

        ClassLoader loader = getClass ().getClassLoader ();
        URL url = loader.getResource ("jfx/main.fxml");
        URL icon = loader.getResource ("images/64x64/route.png");

        if (icon != null) {
            stage.getIcons ().addAll (new Image (icon.openStream ()));
        }

        if (url != null) {
            Parent root = FXMLLoader.load (url, bundle);
            Scene scene = new Scene (root);
            stage.setScene (scene);
            stage.show ();

            stage.setTitle (bundle.getString ("main.title"));
        }
    }
}
