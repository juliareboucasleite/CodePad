package org.example;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.example.controllers.EditorController;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("editor.fxml"));

        Scene scene = new Scene(loader.load());
        scene.setFill(Color.TRANSPARENT);
        stage.setTitle("CodePad — Notas e planejamento");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("codePadLogo.png")));
        stage.setScene(scene);
        if (loader.getController() instanceof EditorController editorController) {
            editorController.attachMainWindow(stage, scene);
            stage.setOnCloseRequest(event -> {
                event.consume();
                editorController.requestExit();
            });
        }
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
