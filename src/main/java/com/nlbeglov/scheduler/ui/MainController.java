package com.nlbeglov.scheduler.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import com.nlbeglov.scheduler.core.SJFWithPriorityScheduler;
import com.nlbeglov.scheduler.core.SchedulerConfig;
import com.nlbeglov.scheduler.core.SchedulingMode;
import com.nlbeglov.scheduler.model.ScheduledProcess;
import com.nlbeglov.scheduler.model.SimulationStats;
import com.nlbeglov.scheduler.sim.SimulationEngine;
import com.nlbeglov.scheduler.sim.SimulationListener;
import com.nlbeglov.scheduler.testing.LoadTester;

import java.util.*;
import java.util.stream.Collectors;

public class MainController implements SimulationListener {

    private final BorderPane root = new BorderPane();

    private final SJFWithPriorityScheduler scheduler;
    private SchedulerConfig config;
    private final SimulationEngine engine;

    private final ObservableList<ScheduledProcess> processes = FXCollections.observableArrayList();
    private final ObservableList<String> logLines = FXCollections.observableArrayList();

    private final TableView<ScheduledProcess> processTable = new TableView<>();
    private final ListView<String> logList = new ListView<>(logLines);

    private final Canvas ganttCanvas = new Canvas(800, 300);
    private final Map<String, Color> processColors = new HashMap<>();

    private final LoadTester loadTester = new LoadTester();

    private int lastDrawnTime = 0;

    public MainController(SJFWithPriorityScheduler scheduler, SchedulerConfig config) {
        this.scheduler = scheduler;
        this.config = config;
        this.engine = new SimulationEngine(scheduler, this);

        buildUI();
    }

    public Parent getRoot() {
        return root;
    }

    public SimulationEngine getEngine() {
        return engine;
    }

    private void buildUI() {
        // left: process table + buttons
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(10));
        buildProcessTable();
        HBox procButtons = buildProcessButtons();

        leftPane.getChildren().addAll(new Label("Processes"), processTable, procButtons);

        // center: tabs - simulation / load test
        TabPane centerTabs = new TabPane();
        centerTabs.getTabs().add(buildSimulationTab());
        centerTabs.getTabs().add(buildLoadTestTab());

        // bottom: controls + log
        SplitPane bottomPane = new SplitPane();
        bottomPane.setOrientation(Orientation.HORIZONTAL);
        bottomPane.getItems().add(buildControlPane());
        bottomPane.getItems().add(buildLogPane());
        bottomPane.setDividerPositions(0.5);

        root.setLeft(leftPane);
        root.setCenter(centerTabs);
        root.setBottom(bottomPane);
    }

    private void buildProcessTable() {
        TableColumn<ScheduledProcess, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getId()));

        TableColumn<ScheduledProcess, Number> arrCol = new TableColumn<>("Arrival");
        arrCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getArrivalTime()));

        TableColumn<ScheduledProcess, Number> burstCol = new TableColumn<>("Burst");
        burstCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getBurstTime()));

        TableColumn<ScheduledProcess, Number> prioCol = new TableColumn<>("Priority");
        prioCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getBasePriority()));

        TableColumn<ScheduledProcess, Number> waitCol = new TableColumn<>("Waiting");
        waitCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getWaitingTime()));

        processTable.getColumns().addAll(idCol, arrCol, burstCol, prioCol, waitCol);
        processTable.setItems(processes);
    }

    private HBox buildProcessButtons() {
        Button addBtn = new Button("Add");
        addBtn.setOnAction(e -> addProcessDialog());

        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> {
            processes.clear();
            scheduler.setProcesses(Collections.emptyList());
            engine.reset();
            clearGantt();
        });

        HBox box = new HBox(10, addBtn, clearBtn);
        return box;
    }

    private Tab buildSimulationTab() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        pane.setCenter(ganttCanvas);
        clearGantt();

        Tab tab = new Tab("Simulation", pane);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildLoadTestTab() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        // Простая форма параметров и текстовый вывод средних значений
        VBox controls = new VBox(8);
        TextField runsField = new TextField("20");
        TextField countField = new TextField("50");
        TextField maxArrivalField = new TextField("50");
        TextField burstRangeField = new TextField("1-10");
        TextField priorityRangeField = new TextField("0-5");
        Button runBtn = new Button("Run load test");
        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);

        controls.getChildren().addAll(
                new Label("Runs:"), runsField,
                new Label("Process count:"), countField,
                new Label("Max arrival:"), maxArrivalField,
                new Label("Burst range (min-max):"), burstRangeField,
                new Label("Priority range (min-max):"), priorityRangeField,
                runBtn
        );

        runBtn.setOnAction(e -> {
            try {
                int runs = Integer.parseInt(runsField.getText().trim());
                int count = Integer.parseInt(countField.getText().trim());
                int maxArrival = Integer.parseInt(maxArrivalField.getText().trim());
                String[] br = burstRangeField.getText().trim().split("-");
                int minBurst = Integer.parseInt(br[0]);
                int maxBurst = Integer.parseInt(br[1]);
                String[] pr = priorityRangeField.getText().trim().split("-");
                int minPrio = Integer.parseInt(pr[0]);
                int maxPrio = Integer.parseInt(pr[1]);

                List<SimulationStats> statsList = loadTester.runMany(
                        runs, count, maxArrival, minBurst, maxBurst, minPrio, maxPrio, config
                );
                double avgWait = statsList.stream().mapToDouble(SimulationStats::getAvgWaitingTime).average().orElse(0);
                double avgTurn = statsList.stream().mapToDouble(SimulationStats::getAvgTurnaroundTime).average().orElse(0);

                resultArea.setText(String.format(
                        "Runs: %d%nAvg waiting (avg by runs): %.3f%nAvg turnaround (avg by runs): %.3f",
                        runs, avgWait, avgTurn
                ));
            } catch (Exception ex) {
                resultArea.setText("Error in parameters: " + ex.getMessage());
            }
        });

        pane.setLeft(controls);
        pane.setCenter(resultArea);

        Tab tab = new Tab("Load test", pane);
        tab.setClosable(false);
        return tab;
    }

    private VBox buildControlPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        Button runBtn = new Button("Run");
        Button pauseBtn = new Button("Pause");
        Button stepBtn = new Button("Step");
        Button resetBtn = new Button("Reset");

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton preemptiveBtn = new RadioButton("Preemptive");
        preemptiveBtn.setToggleGroup(modeGroup);
        preemptiveBtn.setSelected(true);
        RadioButton nonPreemptiveBtn = new RadioButton("Non-preemptive");
        nonPreemptiveBtn.setToggleGroup(modeGroup);

        CheckBox agingBox = new CheckBox("Aging");
        agingBox.setSelected(false);

        runBtn.setOnAction(e -> {
            syncSchedulerConfig(preemptiveBtn.isSelected(), agingBox.isSelected());
            engine.start();
        });
        pauseBtn.setOnAction(e -> engine.pause());
        resetBtn.setOnAction(e -> {
            engine.reset();
            clearGantt();
            logLines.clear();
        });
        stepBtn.setOnAction(e -> {
            syncSchedulerConfig(preemptiveBtn.isSelected(), agingBox.isSelected());
            if (!engine.isRunning()) {
                engine.start();
                engine.step();
                engine.pause();
            }
        });

        box.getChildren().addAll(
                new Label("Simulation control:"),
                new HBox(5, runBtn, pauseBtn, stepBtn, resetBtn),
                new Separator(),
                new Label("Mode:"),
                preemptiveBtn,
                nonPreemptiveBtn,
                agingBox
        );
        return box;
    }

    private void syncSchedulerConfig(boolean preemptive, boolean aging) {
        config = new SchedulerConfig(
                preemptive ? SchedulingMode.PREEMPTIVE : SchedulingMode.NON_PREEMPTIVE,
                aging,
                5
        );
        scheduler.setConfig(config);
    }

    private VBox buildLogPane() {
        VBox box = new VBox(5);
        box.setPadding(new Insets(10));
        box.getChildren().addAll(new Label("Log:"), logList);
        return box;
    }

    private void addProcessDialog() {
        Dialog<ScheduledProcess> dialog = new Dialog<>();
        dialog.setTitle("Add process");

        Label idLabel = new Label("ID:");
        TextField idField = new TextField("P" + (processes.size() + 1));

        Label aLabel = new Label("Arrival:");
        TextField aField = new TextField("0");

        Label bLabel = new Label("Burst:");
        TextField bField = new TextField("5");

        Label pLabel = new Label("Priority (0=highest):");
        TextField pField = new TextField("1");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, idLabel, idField);
        grid.addRow(1, aLabel, aField);
        grid.addRow(2, bLabel, bField);
        grid.addRow(3, pLabel, pField);
        dialog.getDialogPane().setContent(grid);

        ButtonType okType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == okType) {
                try {
                    String id = idField.getText().trim();
                    int arrival = Integer.parseInt(aField.getText().trim());
                    int burst = Integer.parseInt(bField.getText().trim());
                    int prio = Integer.parseInt(pField.getText().trim());
                    return new ScheduledProcess(id, arrival, burst, prio);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return null;
        });

        Optional<ScheduledProcess> res = dialog.showAndWait();
        res.ifPresent(p -> {
            processes.add(p);
            scheduler.setProcesses(cloneProcesses(processes));
            engine.reset();
            clearGantt();
        });
    }

    private List<ScheduledProcess> cloneProcesses(List<ScheduledProcess> src) {
        return src.stream()
                .map(p -> new ScheduledProcess(p.getId(), p.getArrivalTime(),
                        p.getBurstTime(), p.getBasePriority()))
                .collect(Collectors.toList());
    }

    private void clearGantt() {
        GraphicsContext gc = ganttCanvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());
        gc.setStroke(Color.GRAY);
        gc.strokeRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());
        lastDrawnTime = 0;
        processColors.clear();
    }

    @Override
    public void onTimeAdvanced(int time, ScheduledProcess runningProcess, List<ScheduledProcess> snapshot) {
        // обновляем таблицу
        processes.setAll(snapshot);

        // логика отрисовки Ганта: рисуем по времени time
        if (runningProcess != null) {
            drawGanttSlice(time, runningProcess);
        }

        // лог: можно писать менее подробно, но для наглядности — так
        if (runningProcess != null) {
            onLogEvent(String.format("t=%d: running %s (prio=%d, rem=%d)",
                    time, runningProcess.getId(), runningProcess.getEffectivePriority(),
                    runningProcess.getRemainingTime()));
        } else {
            onLogEvent(String.format("t=%d: CPU idle", time));
        }
    }

    private void drawGanttSlice(int time, ScheduledProcess proc) {
        GraphicsContext gc = ganttCanvas.getGraphicsContext2D();
        Color color = processColors.computeIfAbsent(proc.getId(), id -> randomColor());
        gc.setFill(color);

        double widthPerUnit = 10; // масштаб
        double height = 30;
        double y = 20 + (processColors.keySet().stream().sorted().collect(Collectors.toList())
                .indexOf(proc.getId()) * (height + 5));
        double x = time * widthPerUnit;

        gc.fillRect(x, y, widthPerUnit, height);
        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, widthPerUnit, height);
    }

    private Color randomColor() {
        Random r = new Random();
        return Color.color(r.nextDouble(), r.nextDouble(), r.nextDouble());
    }

    @Override
    public void onLogEvent(String message) {
        logLines.add(message);
        if (logLines.size() > 500) {
            logLines.remove(0);
        }
    }

    @Override
    public void onSimulationFinished() {
        onLogEvent("Simulation finished");
        SimulationStats stats = SimulationStats.fromProcesses(new ArrayList<>(scheduler.getAllProcesses()));
        onLogEvent(String.format("Avg waiting=%.3f, avg turnaround=%.3f",
                stats.getAvgWaitingTime(), stats.getAvgTurnaroundTime()));
    }
}
