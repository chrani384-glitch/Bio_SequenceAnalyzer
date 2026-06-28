package com.bioanalyzer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.Objects;

public class MainApp extends Application {

    public static final String APP_TITLE   = "BioSequenceAnalyzer v1.0";
    public static final double WINDOW_W    = 1300;
    public static final double WINDOW_H    = 820;
    public static final String BACKEND_URL = "http://localhost:5001";

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/com/bioanalyzer/fxml/MainView.fxml")
        );

        Scene scene = new Scene(loader.load(), WINDOW_W, WINDOW_H);
        scene.getStylesheets().add(
            Objects.requireNonNull(
                getClass().getResource("/com/bioanalyzer/css/dark-theme.css")
            ).toExternalForm()
        );

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(650);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
