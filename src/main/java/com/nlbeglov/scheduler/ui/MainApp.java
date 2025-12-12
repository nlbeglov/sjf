package com.nlbeglov.scheduler.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.nlbeglov.scheduler.core.SJFWithPriorityScheduler;
import com.nlbeglov.scheduler.core.SchedulerConfig;
import com.nlbeglov.scheduler.sim.SimulationEngine;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {

        SJFWithPriorityScheduler scheduler = new SJFWithPriorityScheduler();
        SchedulerConfig config = SchedulerConfig.defaultConfig();
        scheduler.setConfig(config);

        MainController controller = new MainController(scheduler, config);
        SimulationEngine engine = controller.getEngine();

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200), e -> engine.step()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        Scene scene = new Scene(controller.getRoot(), 1200, 700);
        primaryStage.setTitle("Симулятор планировщика SJF с приоритетами");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}