package com.nlbeglov.scheduler.ui;

import com.nlbeglov.scheduler.core.SJFWithPriorityScheduler;
import com.nlbeglov.scheduler.core.SchedulerConfig;
import com.nlbeglov.scheduler.core.SchedulingMode;
import com.nlbeglov.scheduler.model.ScheduledProcess;
import com.nlbeglov.scheduler.model.SimulationStats;
import com.nlbeglov.scheduler.sim.SimulationEngine;
import com.nlbeglov.scheduler.sim.SimulationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

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

    private final Random random = new Random();
    private boolean needsSimulationReload = true;

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

        VBox simulationPane = buildSimulationPane();

        SplitPane bottomPane = new SplitPane();
        bottomPane.setOrientation(Orientation.HORIZONTAL);
        bottomPane.getItems().add(buildControlPane());
        bottomPane.getItems().add(buildLogPane());
        bottomPane.setDividerPositions(0.5);

        root.setLeft(leftPane);
        root.setCenter(simulationPane);
        root.setBottom(bottomPane);
    }

    private void buildProcessTable() {
        TableColumn<ScheduledProcess, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getId()));

        TableColumn<ScheduledProcess, Number> prioCol = new TableColumn<>("Приоритет");
        prioCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getBasePriority()));

        TableColumn<ScheduledProcess, Number> arrCol = new TableColumn<>("Прибытие");
        arrCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getArrivalTime()));

        TableColumn<ScheduledProcess, Number> burstCol = new TableColumn<>("Длительность");
        burstCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getBurstTime()));

        processTable.getColumns().addAll(idCol, prioCol, arrCol, burstCol);
        processTable.setItems(processes);
        processTable.setRowFactory(tv -> {
            TableRow<ScheduledProcess> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    editProcessDialog(row.getItem());
                }
            });
            return row;
        });
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
            needsSimulationReload = true;
        });

        HBox box = new HBox(10, addBtn, clearBtn);
        return box;
    }

    private VBox buildSimulationPane() {
        VBox pane = new VBox(6);
        pane.setPadding(new Insets(10));

        Label title = new Label("Симуляция");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        pane.getChildren().addAll(title, ganttCanvas);
        clearGantt();
        return pane;
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
        preemptiveBtn.setTooltip(new Tooltip("Планировщик может переключить процесс при появлении более приоритетной или более короткой задачи"));
        RadioButton nonPreemptiveBtn = new RadioButton("Невытесняющий");
        nonPreemptiveBtn.setToggleGroup(modeGroup);
        nonPreemptiveBtn.setTooltip(new Tooltip("Процесс дорабатывает до конца, даже если появился более приоритетный"));

        CheckBox agingBox = new CheckBox("Старение приоритета");
        agingBox.setTooltip(new Tooltip("Со временем ожидания приоритет процесса повышается"));
        agingBox.setSelected(false);

        Button resultsBtn = new Button("Итоги симуляции");
        resultsBtn.setOnAction(e -> showResultsWindow());

        Label modeHint = new Label(
                "Вытесняющий режим подходит для интерактивных задач: как только приходит более важный процесс, " +
                        "процессор переключается на него. В невытесняющем режиме процесс всегда дорабатывает квант до конца, " +
                        "что упрощает планирование, но может увеличивать задержки для коротких задач."
        );
        modeHint.setWrapText(true);
        modeHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        preemptiveBtn.setOnAction(e -> needsSimulationReload = true);
        nonPreemptiveBtn.setOnAction(e -> needsSimulationReload = true);
        agingBox.setOnAction(e -> needsSimulationReload = true);

        runBtn.setOnAction(e -> {
            if (needsSimulationReload || scheduler.isFinished()) {
                prepareSimulation(preemptiveBtn.isSelected(), agingBox.isSelected());
            } else {
                syncSchedulerConfig(preemptiveBtn.isSelected(), agingBox.isSelected());
            }
            engine.start();
        });
        pauseBtn.setOnAction(e -> engine.pause());
        resetBtn.setOnAction(e -> {
            engine.reset();
            clearGantt();
            logLines.clear();
            needsSimulationReload = true;
        });
        stepBtn.setOnAction(e -> {
            if (needsSimulationReload || scheduler.isFinished()) {
                prepareSimulation(preemptiveBtn.isSelected(), agingBox.isSelected());
            } else {
                syncSchedulerConfig(preemptiveBtn.isSelected(), agingBox.isSelected());
            }
            engine.start();
            engine.step();
            engine.pause();
        });

        box.getChildren().addAll(
                new Label("Управление симуляцией:"),
                new HBox(5, runBtn, pauseBtn, stepBtn, resetBtn),
                new Separator(),
                new Label("Режим:"),
                preemptiveBtn,
                nonPreemptiveBtn,
                agingBox,
                resultsBtn,
                modeHint
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
        showProcessDialog(null);
    }

    private void editProcessDialog(ScheduledProcess existing) {
        showProcessDialog(existing);
    }

    private void showProcessDialog(ScheduledProcess toEdit) {
        Dialog<ScheduledProcess> dialog = new Dialog<>();
        boolean editing = toEdit != null;
        dialog.setTitle(editing ? "Редактирование процесса" : "Добавление процесса");

        Label idLabel = new Label("ID:");
        TextField idField = new TextField(editing ? toEdit.getId() : "P" + (processes.size() + 1));
        idField.setTooltip(new Tooltip("Идентификатор процесса"));

        int randomPriority = random.nextInt(6);
        int randomArrival = random.nextInt(11);
        int randomBurst = random.nextInt(10) + 1;

        Label pLabel = new Label("Приоритет (0 — высший):");
        TextField pField = new TextField(String.valueOf(editing ? toEdit.getBasePriority() : randomPriority));
        pField.setTooltip(new Tooltip("Меньшее значение — более высокий приоритет"));

        Label aLabel = new Label("Прибытие:");
        TextField aField = new TextField(String.valueOf(editing ? toEdit.getArrivalTime() : randomArrival));
        aField.setTooltip(new Tooltip("Момент времени, когда процесс становится доступным"));

        Label bLabel = new Label("Длительность CPU:");
        TextField bField = new TextField(String.valueOf(editing ? toEdit.getBurstTime() : randomBurst));
        bField.setTooltip(new Tooltip("Сколько тиков CPU требуется"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, idLabel, idField);
        grid.addRow(1, pLabel, pField);
        grid.addRow(2, aLabel, aField);
        grid.addRow(3, bLabel, bField);
        dialog.getDialogPane().setContent(grid);

        ButtonType okType = new ButtonType(editing ? "Сохранить" : "Добавить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, cancelType);

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
            if (editing) {
                int idx = processes.indexOf(toEdit);
                if (idx >= 0) {
                    processes.set(idx, p);
                }
            } else {
                processes.add(p);
            }
            scheduler.setProcesses(cloneProcesses(processes));
            engine.reset();
            clearGantt();
            needsSimulationReload = true;
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
        processColors.clear();
    }

    @Override
    public void onTimeAdvanced(int time, ScheduledProcess runningProcess, List<ScheduledProcess> snapshot) {
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

        double widthPerUnit = 20; // масштаб
        double height = 30;
        double y = 20 + (processColors.keySet().stream().sorted().collect(Collectors.toList())
                .indexOf(proc.getId()) * (height + 5));
        double x = time * widthPerUnit;

        gc.fillRect(x, y, widthPerUnit, height);
        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, widthPerUnit, height);

        gc.setFill(textColorFor(color));
        gc.setFont(Font.font(11));
        gc.fillText(proc.getId(), x + 2, y + height - 8);
    }

    private Color randomColor() {
        return Color.color(random.nextDouble(), random.nextDouble(), random.nextDouble());
    }

    private Color textColorFor(Color background) {
        double brightness = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue());
        return brightness < 0.5 ? Color.WHITE : Color.BLACK;
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
        appendProcessSummary(scheduler.getAllProcesses());
        needsSimulationReload = true;
    }

    private void appendProcessSummary(List<ScheduledProcess> finishedProcesses) {
        onLogEvent("Итоги по процессам:");
        onLogEvent("Процесс|Приоритет|Время прибытия|Длительность CPU|Время завершения|Время ожидания|Время пребывания");
        finishedProcesses.stream()
                .sorted(Comparator.comparing(ScheduledProcess::getId))
                .forEach(p -> onLogEvent(String.format(
                        "%s|%d|%d|%d|%d|%d|%d",
                        p.getId(),
                        p.getBasePriority(),
                        p.getArrivalTime(),
                        p.getBurstTime(),
                        Optional.ofNullable(p.getFinishTime()).orElse(0),
                        p.getWaitingTime(),
                        p.getTurnaroundTime()
                )));
    }

    private void prepareSimulation(boolean preemptive, boolean aging) {
        syncSchedulerConfig(preemptive, aging);
        scheduler.setProcesses(cloneProcesses(processes));
        engine.reset();
        clearGantt();
        needsSimulationReload = false;
    }

    private void showResultsWindow() {
        TableView<ProcessResult> resultsTable = new TableView<>();
        resultsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<ProcessResult, String> idCol = new TableColumn<>("Процесс");
        idCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().id()));

        TableColumn<ProcessResult, Number> prioCol = new TableColumn<>("Приоритет");
        prioCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().priority()));

        TableColumn<ProcessResult, Number> arrCol = new TableColumn<>("Время прибытия");
        arrCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().arrival()));

        TableColumn<ProcessResult, Number> burstCol = new TableColumn<>("Длительность CPU");
        burstCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().burst()));

        TableColumn<ProcessResult, Number> finishCol = new TableColumn<>("Время завершения");
        finishCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().finish()));

        TableColumn<ProcessResult, Number> waitCol = new TableColumn<>("Время ожидания");
        waitCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().waiting()));

        TableColumn<ProcessResult, Number> turnCol = new TableColumn<>("Время пребывания");
        turnCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().turnaround()));

        resultsTable.getColumns().addAll(idCol, prioCol, arrCol, burstCol, finishCol, waitCol, turnCol);
        List<ProcessResult> data = scheduler.getAllProcesses().stream()
                .sorted(Comparator.comparing(ScheduledProcess::getId))
                .map(p -> new ProcessResult(
                        p.getId(),
                        p.getBasePriority(),
                        p.getArrivalTime(),
                        p.getBurstTime(),
                        Optional.ofNullable(p.getFinishTime()).orElse(0),
                        p.getWaitingTime(),
                        p.getTurnaroundTime()
                ))
                .toList();
        resultsTable.setItems(FXCollections.observableArrayList(data));

        VBox wrapper = new VBox(10, new Label("Итоги симуляции"), resultsTable);
        wrapper.setPadding(new Insets(10));

        Stage stage = new Stage();
        stage.setTitle("Итоги симуляции");
        stage.setScene(new Scene(wrapper, 700, 400));
        stage.show();
    }

    private record ProcessResult(String id, int priority, int arrival, int burst,
                                 int finish, int waiting, int turnaround) {
    }
}
