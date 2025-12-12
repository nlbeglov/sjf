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
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(10));
        buildProcessTable();
        HBox procButtons = buildProcessButtons();

        leftPane.getChildren().addAll(new Label("Процессы"), processTable, procButtons);

        TabPane centerTabs = new TabPane();
        centerTabs.setTabMinWidth(140);
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        centerTabs.getTabs().add(buildSimulationTab());
        centerTabs.getTabs().add(buildLoadTestTab());

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

        TableColumn<ScheduledProcess, Number> arrCol = new TableColumn<>("Прибытие");
        arrCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getArrivalTime()));

        TableColumn<ScheduledProcess, Number> burstCol = new TableColumn<>("Длительность");
        burstCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getBurstTime()));

        TableColumn<ScheduledProcess, Number> prioCol = new TableColumn<>("Приоритет");
        prioCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getBasePriority()));

        TableColumn<ScheduledProcess, Number> waitCol = new TableColumn<>("Ожидание");
        waitCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getWaitingTime()));

        processTable.getColumns().addAll(idCol, arrCol, burstCol, prioCol, waitCol);
        processTable.setItems(processes);
    }

    private HBox buildProcessButtons() {
        Button addBtn = new Button("Добавить");
        addBtn.setTooltip(new Tooltip("Добавить новый процесс и пересчитать очередь"));
        addBtn.setOnAction(e -> addProcessDialog());

        Button clearBtn = new Button("Очистить");
        clearBtn.setTooltip(new Tooltip("Удалить все процессы и сбросить симуляцию"));
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

        Tab tab = new Tab("Симуляция", pane);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildLoadTestTab() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(8);

        TextField runsField = new TextField("20");
        runsField.setTooltip(new Tooltip("Сколько раз сгенерировать нагрузку для усреднения результатов"));

        TextField countField = new TextField("50");
        countField.setTooltip(new Tooltip("Количество процессов в каждом прогоне"));

        TextField maxArrivalField = new TextField("50");
        maxArrivalField.setTooltip(new Tooltip("Максимальное время прихода процесса"));

        TextField burstRangeField = new TextField("1-10");
        burstRangeField.setTooltip(new Tooltip("Диапазон длительности выполнения через дефис (например, 1-10)"));

        TextField priorityRangeField = new TextField("0-5");
        priorityRangeField.setTooltip(new Tooltip("Диапазон базового приоритета (0 — самый высокий)"));

        Button runBtn = new Button("Запустить тест");
        runBtn.setTooltip(new Tooltip("Сгенерировать нагрузку и посчитать средние показатели"));

        controls.addRow(0, new Label("Число прогонов:"), runsField);
        controls.addRow(1, new Label("Кол-во процессов:"), countField);
        controls.addRow(2, new Label("Максимальное прибытие:"), maxArrivalField);
        controls.addRow(3, new Label("Диапазон длительности (мин-макс):"), burstRangeField);
        controls.addRow(4, new Label("Диапазон приоритета (мин-макс):"), priorityRangeField);
        controls.add(runBtn, 0, 5, 2, 1);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(170);
        ColumnConstraints inputCol = new ColumnConstraints();
        inputCol.setHgrow(Priority.ALWAYS);
        controls.getColumnConstraints().addAll(labelCol, inputCol);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPromptText("Здесь появится итог нагрузочного теста");
        resultArea.setPrefRowCount(12);
        resultArea.setPrefColumnCount(40);

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
                        "Прогонов: %d%nСреднее ожидание: %.3f%nСреднее время обращения: %.3f",
                        runs, avgWait, avgTurn
                ));
            } catch (Exception ex) {
                resultArea.setText("Проверьте параметры: " + ex.getMessage());
            }
        });

        HBox content = new HBox(15, controls, resultArea);
        content.setPadding(new Insets(5, 0, 0, 0));
        HBox.setHgrow(resultArea, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        pane.setCenter(scrollPane);

        Tab tab = new Tab("Нагрузочный тест", pane);
        tab.setClosable(false);
        return tab;
    }

    private VBox buildControlPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        Button runBtn = new Button("Старт");
        runBtn.setTooltip(new Tooltip("Запустить симуляцию со всеми текущими процессами"));
        Button pauseBtn = new Button("Пауза");
        pauseBtn.setTooltip(new Tooltip("Приостановить симуляцию"));
        Button stepBtn = new Button("Шаг");
        stepBtn.setTooltip(new Tooltip("Продвинуть симуляцию на один такт"));
        Button resetBtn = new Button("Сброс");
        resetBtn.setTooltip(new Tooltip("Сбросить время, очистить диаграмму Ганта и журнал"));

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton preemptiveBtn = new RadioButton("Вытесняющий");
        preemptiveBtn.setToggleGroup(modeGroup);
        preemptiveBtn.setSelected(true);
        RadioButton nonPreemptiveBtn = new RadioButton("Невытесняющий");
        nonPreemptiveBtn.setToggleGroup(modeGroup);

        CheckBox agingBox = new CheckBox("Старение приоритета");
        agingBox.setTooltip(new Tooltip("Со временем ожидания приоритет процесса повышается"));
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
                new Label("Управление симуляцией:"),
                new HBox(5, runBtn, pauseBtn, stepBtn, resetBtn),
                new Separator(),
                new Label("Режим:"),
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
        logList.setPlaceholder(new Label("Сообщения симуляции появятся здесь"));
        box.getChildren().addAll(new Label("Журнал:"), logList);
        return box;
    }

    private void addProcessDialog() {
        Dialog<ScheduledProcess> dialog = new Dialog<>();
        dialog.setTitle("Добавление процесса");

        Label idLabel = new Label("ID:");
        TextField idField = new TextField("P" + (processes.size() + 1));
        idField.setTooltip(new Tooltip("Идентификатор процесса"));

        Label aLabel = new Label("Прибытие:");
        TextField aField = new TextField("0");
        aField.setTooltip(new Tooltip("Момент времени, когда процесс становится доступным"));

        Label bLabel = new Label("Длительность:");
        TextField bField = new TextField("5");
        bField.setTooltip(new Tooltip("Сколько тиков CPU требуется"));

        Label pLabel = new Label("Приоритет (0 — высший):");
        TextField pField = new TextField("1");
        pField.setTooltip(new Tooltip("Меньшее значение — более высокий приоритет"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, idLabel, idField);
        grid.addRow(1, aLabel, aField);
        grid.addRow(2, bLabel, bField);
        grid.addRow(3, pLabel, pField);
        dialog.getDialogPane().setContent(grid);

        ButtonType okType = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
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
            onLogEvent(String.format("t=%d: выполняется %s (приор.=%d, осталось=%d)",
                    time, runningProcess.getId(), runningProcess.getEffectivePriority(),
                    runningProcess.getRemainingTime()));
        } else {
            onLogEvent(String.format("t=%d: ЦП простаивает", time));
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
        onLogEvent("Симуляция завершена");
        SimulationStats stats = SimulationStats.fromProcesses(new ArrayList<>(scheduler.getAllProcesses()));
        onLogEvent(String.format("Среднее ожидание=%.3f, среднее время обращения=%.3f",
                stats.getAvgWaitingTime(), stats.getAvgTurnaroundTime()));
    }
}
