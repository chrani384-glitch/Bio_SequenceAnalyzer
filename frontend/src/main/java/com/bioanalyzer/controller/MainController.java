package com.bioanalyzer.controller;

import com.bioanalyzer.model.AnalysisResult;
import com.bioanalyzer.service.ApiService;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.*;
import java.net.URL;
import java.util.*;

public class MainController implements Initializable {

    // ── Header ─────────────────────────────────────────────────
    @FXML private Label backendStatusLabel;

    // ── Input panel ────────────────────────────────────────────
    @FXML private TextArea sequenceInput;
    @FXML private ComboBox<String> seqTypeCombo;
    @FXML private Label detectedTypeLabel;
    @FXML private Button analyzeButton;
    @FXML private Button clearButton;
    @FXML private Button exportButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    // ── Mutation tab ───────────────────────────────────────────
    @FXML private TextArea seq1Input;
    @FXML private TextArea seq2Input;
    @FXML private Button mutationButton;

    // ── Results table ──────────────────────────────────────────
    @FXML private TableView<AnalysisResult> resultsTable;
    @FXML private TableColumn<AnalysisResult, String> colProperty;
    @FXML private TableColumn<AnalysisResult, String> colValue;
    @FXML private TableColumn<AnalysisResult, String> colDescription;
    @FXML private TableColumn<AnalysisResult, String> colCategory;

    // ── Sequence display ───────────────────────────────────────
    @FXML private TextArea sequenceOutputArea;

    // ── Charts ─────────────────────────────────────────────────
    @FXML private BarChart<String, Number>  compositionChart;
    @FXML private CategoryAxis              compXAxis;
    @FXML private NumberAxis                compYAxis;
    @FXML private PieChart                  distributionPie;

    // ── Tab pane ───────────────────────────────────────────────
    @FXML private TabPane mainTabPane;

    // ── Summary labels ─────────────────────────────────────────
    @FXML private Label labelLength;
    @FXML private Label labelType;
    @FXML private Label labelGC;
    @FXML private Label labelMW;

    // ── Internal state ─────────────────────────────────────────
    private final ApiService apiService = new ApiService();
    private final ObservableList<AnalysisResult> results = FXCollections.observableArrayList();
    private JsonNode lastResult = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupTypeCombo();
        setupCharts();
        checkBackend();
    }

    // ── Setup ──────────────────────────────────────────────────
    private void setupTable() {
        colProperty.setCellValueFactory(new PropertyValueFactory<>("property"));
        colValue.setCellValueFactory(new PropertyValueFactory<>("value"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));

        // Color-code value column by category
        colValue.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); setStyle(""); return; }
                setText(val);
                AnalysisResult row = getTableView().getItems().get(getIndex());
                String cat = row != null ? row.getCategory() : "";
                switch (cat) {
                    case "DNA"     -> setStyle("-fx-text-fill: #4EC9B0; -fx-font-family: Consolas;");
                    case "RNA"     -> setStyle("-fx-text-fill: #CE9178; -fx-font-family: Consolas;");
                    case "PROTEIN" -> setStyle("-fx-text-fill: #DCDCAA; -fx-font-family: Consolas;");
                    case "METRIC"  -> setStyle("-fx-text-fill: #9CDCFE; -fx-font-weight: bold;");
                    case "MUTATION"-> setStyle("-fx-text-fill: #F44747; -fx-font-weight: bold;");
                    case "GOOD"    -> setStyle("-fx-text-fill: #00e676; -fx-font-weight: bold;");
                    default        -> setStyle("-fx-text-fill: #D4D4D4;");
                }
            }
        });

        colCategory.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); setStyle(""); return; }
                setText(val);
                switch (val) {
                    case "DNA"     -> setStyle("-fx-text-fill: #4EC9B0;");
                    case "RNA"     -> setStyle("-fx-text-fill: #CE9178;");
                    case "PROTEIN" -> setStyle("-fx-text-fill: #DCDCAA;");
                    case "METRIC"  -> setStyle("-fx-text-fill: #9CDCFE;");
                    case "MUTATION"-> setStyle("-fx-text-fill: #F44747;");
                    case "GOOD"    -> setStyle("-fx-text-fill: #00e676;");
                    default        -> setStyle("-fx-text-fill: #8B949E;");
                }
            }
        });

        resultsTable.setItems(results);
    }

    private void setupTypeCombo() {
        seqTypeCombo.setItems(FXCollections.observableArrayList(
            "AUTO DETECT", "DNA", "RNA", "PROTEIN"
        ));
        seqTypeCombo.setValue("AUTO DETECT");
    }

    private void setupCharts() {
        compositionChart.setAnimated(true);
        compositionChart.setTitle("Nucleotide / AA Composition");
        compXAxis.setLabel("Base / Amino Acid");
        compYAxis.setLabel("Frequency (%)");
        distributionPie.setTitle("Composition Distribution");
        distributionPie.setAnimated(true);
    }

    private void checkBackend() {
        new Thread(() -> {
            boolean alive = apiService.isBackendAlive();
            Platform.runLater(() -> {
                if (alive) {
                    backendStatusLabel.setText("🟢 Backend Online");
                    backendStatusLabel.setStyle("-fx-text-fill: #00e676;");
                } else {
                    backendStatusLabel.setText("🔴 Backend Offline — run: python app.py");
                    backendStatusLabel.setStyle("-fx-text-fill: #ef5350;");
                }
            });
        }).start();
    }

    // ── ANALYZE button ─────────────────────────────────────────
    @FXML
    private void onAnalyzeClicked() {
        String seq = sequenceInput.getText().trim()
            .replaceAll("\\s+", "").toUpperCase();

        if (seq.isEmpty()) {
            showAlert("No Input", "Please enter a sequence in the input area.");
            return;
        }

        String selectedType = seqTypeCombo.getValue();
        String type = selectedType.equals("AUTO DETECT") ? "AUTO" : selectedType;

        analyzeButton.setDisable(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Analyzing sequence...");
        results.clear();

        Task<JsonNode> task = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                return apiService.analyze(seq, type);
            }
        };

        task.setOnSucceeded(e -> {
            lastResult = task.getValue();
            populateResults(lastResult);
            updateCharts(lastResult);
            updateSummary(lastResult);
            updateSequenceOutput(lastResult);
            progressBar.setProgress(1.0);
            statusLabel.setText("Analysis complete for "
                + lastResult.get("type").asText()
                + " sequence — length: " + lastResult.get("length").asInt());
            analyzeButton.setDisable(false);
            exportButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            progressBar.setProgress(0);
            statusLabel.setText("Error: " + task.getException().getMessage());
            analyzeButton.setDisable(false);
            showAlert("Analysis Failed",
                "Could not analyze sequence.\n\n"
                + task.getException().getMessage()
                + "\n\nMake sure backend is running: python app.py");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── MUTATION button ────────────────────────────────────────
    @FXML
    private void onMutationClicked() {
        String s1 = seq1Input.getText().trim().replaceAll("\\s+", "").toUpperCase();
        String s2 = seq2Input.getText().trim().replaceAll("\\s+", "").toUpperCase();

        if (s1.isEmpty() || s2.isEmpty()) {
            showAlert("Missing Input", "Please enter BOTH sequences for mutation comparison.");
            return;
        }

        mutationButton.setDisable(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Detecting mutations...");
        results.clear();

        Task<JsonNode> task = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                return apiService.detectMutations(s1, s2);
            }
        };

        task.setOnSucceeded(e -> {
            JsonNode res = task.getValue();
            populateMutationResults(res);
            progressBar.setProgress(1.0);
            int mutCount = res.get("mutation_count").asInt();
            double sim   = res.get("similarity_percent").asDouble();
            statusLabel.setText(String.format(
                "Mutation analysis done — %d mutation(s) found, %.1f%% similarity",
                mutCount, sim));
            mutationButton.setDisable(false);
            // Switch to results tab
            mainTabPane.getSelectionModel().select(2);
        });

        task.setOnFailed(e -> {
            progressBar.setProgress(0);
            statusLabel.setText("Error: " + task.getException().getMessage());
            mutationButton.setDisable(false);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── Populate results table ─────────────────────────────────
    private void populateResults(JsonNode r) {
        results.clear();
        String type = r.get("type").asText();

        // Common fields
        add("Sequence Type",  type,                          "Detected molecule type",        type);
        add("Length",         r.get("length").asInt() + " bp/aa", "Total sequence length",   "METRIC");

        if (type.equals("DNA")) {
            add("GC Content",    r.get("gc_content").asDouble() + "%",   "Guanine + Cytosine content",    "METRIC");
            add("AT Content",    r.get("at_content").asDouble() + "%",   "Adenine + Thymine content",     "METRIC");
            add("Melting Temp",  r.get("melting_temp").asDouble() + " °C", "DNA duplex melting temperature", "METRIC");
            add("Mol. Weight",   r.get("molecular_weight").asDouble() + " Da", "Molecular weight",         "METRIC");
            add("Is Palindrome", r.get("is_palindrome").asBoolean() ? "Yes" : "No", "Sequence equals reverse complement", "METRIC");
            add("ORFs Found",    r.get("orf_count").asInt() + " ORFs",   "Open Reading Frames detected",  "METRIC");

            JsonNode bc = r.get("base_counts");
            add("Adenine (A)",   bc.get("A").asInt() + " bases",  "Count of Adenine",   "DNA");
            add("Thymine (T)",   bc.get("T").asInt() + " bases",  "Count of Thymine",   "DNA");
            add("Guanine (G)",   bc.get("G").asInt() + " bases",  "Count of Guanine",   "DNA");
            add("Cytosine (C)",  bc.get("C").asInt() + " bases",  "Count of Cytosine",  "DNA");

            String rc  = r.get("reverse_complement").asText();
            String rna = r.get("rna_transcript").asText();
            add("Complement",         r.get("complement").asText().substring(0, Math.min(40, r.get("complement").asText().length())) + "...", "5' to 3' complement strand", "DNA");
            add("Reverse Complement", rc.substring(0, Math.min(40, rc.length())) + "...",  "Reverse complement (antisense)", "DNA");
            add("RNA Transcript",     rna.substring(0, Math.min(40, rna.length())) + "...", "Transcribed mRNA sequence",     "RNA");

            // Top ORFs
            JsonNode orfs = r.get("orfs");
            if (orfs != null && orfs.isArray()) {
                for (int i = 0; i < Math.min(3, orfs.size()); i++) {
                    JsonNode orf = orfs.get(i);
                    add("ORF " + (i+1),
                        "Start: " + orf.get("start").asInt() + " | Len: " + orf.get("length").asInt() + " bp",
                        "Frame " + orf.get("frame").asInt() + " | " + orf.get("aa_length").asInt() + " aa",
                        "METRIC");
                }
            }

        } else if (type.equals("RNA")) {
            add("GC Content",     r.get("gc_content").asDouble() + "%",     "G + C content",              "METRIC");
            add("Protein Length", r.get("protein_length").asInt() + " aa",  "Translated protein length",  "METRIC");
            add("Has Start (AUG)",r.get("has_start_codon").asBoolean() ? "Yes ✅" : "No ❌", "AUG codon present", "METRIC");
            add("Has Stop Codon", r.get("has_stop_codon").asBoolean() ? "Yes ✅" : "No ❌",  "Stop codon present","METRIC");

            JsonNode bc = r.get("base_counts");
            add("Adenine (A)",    bc.get("A").asInt() + " bases", "Count of Adenine",   "RNA");
            add("Uracil (U)",     bc.get("U").asInt() + " bases", "Count of Uracil",    "RNA");
            add("Guanine (G)",    bc.get("G").asInt() + " bases", "Count of Guanine",   "RNA");
            add("Cytosine (C)",   bc.get("C").asInt() + " bases", "Count of Cytosine",  "RNA");

            String prot = r.get("protein_translation").asText();
            add("Protein Translation",
                prot.substring(0, Math.min(60, prot.length())) + (prot.length() > 60 ? "..." : ""),
                "Amino acid sequence",
                "PROTEIN");

            add("DNA Template", r.get("dna_template").asText().substring(0, Math.min(40, r.get("dna_template").asText().length())) + "...", "Back-transcribed DNA", "DNA");

            // First 10 codons
            JsonNode codons = r.get("codon_details");
            if (codons != null && codons.isArray()) {
                for (int i = 0; i < Math.min(10, codons.size()); i++) {
                    JsonNode cod = codons.get(i);
                    add("Codon " + cod.get("position").asInt(),
                        cod.get("codon").asText() + " → " + cod.get("amino_acid").asText(),
                        "Codon at position " + cod.get("position").asInt(),
                        "RNA");
                }
            }

        } else if (type.equals("PROTEIN")) {
            add("Molecular Weight",   String.format("%.2f Da", r.get("molecular_weight").asDouble()), "Protein molecular weight", "METRIC");
            add("Isoelectric Point",  String.format("%.2f",    r.get("isoelectric_point").asDouble()), "pH at zero net charge",   "METRIC");
            add("Hydrophobicity",     String.format("%.3f",    r.get("hydrophobicity").asDouble()),    "Kyte-Doolittle score",    "METRIC");
            add("Instability Index",  String.format("%.2f",    r.get("instability_index").asDouble()), "< 40 = stable",          "METRIC");
            add("Stability",          r.get("is_stable").asBoolean() ? "Stable ✅" : "Unstable ❌",   "Protein stability",       r.get("is_stable").asBoolean() ? "GOOD" : "MUTATION");
            add("Extinction Coeff.",  r.get("extinction_coefficient").asInt() + " M-1 cm-1",          "UV absorbance at 280nm",  "METRIC");
            add("Charge at pH 7",     r.get("charge_at_ph7").asInt() + "",                             "Net charge at pH 7",     "METRIC");
            add("Positive AA",        r.get("positive_aa").asInt() + " (R, K, H)",                    "Positively charged AA",   "PROTEIN");
            add("Negative AA",        r.get("negative_aa").asInt() + " (D, E)",                       "Negatively charged AA",   "PROTEIN");
            add("Aromatic AA",        r.get("aromatic_aa").asInt() + " (W, Y, F)",                    "Aromatic amino acids",    "PROTEIN");

            // Secondary structure
            JsonNode ss = r.get("secondary_structure");
            if (ss != null) {
                add("Alpha Helix",   ss.get("helix").asDouble() + "%",  "Predicted helix content",  "PROTEIN");
                add("Beta Sheet",    ss.get("sheet").asDouble() + "%",  "Predicted sheet content",  "PROTEIN");
                add("Turns",         ss.get("turn").asDouble() + "%",   "Predicted turn content",   "PROTEIN");
                add("Random Coil",   ss.get("coil").asDouble() + "%",   "Predicted coil content",   "PROTEIN");
            }

            // Top 10 amino acids
            JsonNode comp = r.get("aa_composition");
            if (comp != null) {
                comp.fields().forEachRemaining(entry -> {
                    JsonNode v = entry.getValue();
                    if (v.get("count").asInt() > 0) {
                        add(v.get("name").asText() + " (" + entry.getKey() + ")",
                            v.get("count").asInt() + " (" + v.get("percentage").asDouble() + "%)",
                            "Amino acid composition",
                            "PROTEIN");
                    }
                });
            }
        }
    }

    private void populateMutationResults(JsonNode r) {
        results.clear();
        add("Sequence 1 Length",  r.get("length1").asInt() + " bp",               "Reference sequence length",  "METRIC");
        add("Sequence 2 Length",  r.get("length2").asInt() + " bp",               "Query sequence length",      "METRIC");
        add("Mutations Found",    r.get("mutation_count").asInt() + " mutations",  "Total mutations detected",   r.get("mutation_count").asInt() > 0 ? "MUTATION" : "GOOD");
        add("Similarity",         r.get("similarity_percent").asDouble() + "%",    "Sequence similarity",        "METRIC");
        add("Mutation Rate",      r.get("mutation_rate").asDouble() + "%",         "Percentage of positions mutated", "MUTATION");

        JsonNode mutations = r.get("mutations");
        if (mutations != null && mutations.isArray()) {
            for (JsonNode m : mutations) {
                add(m.get("type").asText() + " at pos " + m.get("position").asInt(),
                    m.get("original").asText() + " → " + m.get("mutated").asText(),
                    m.get("description").asText(),
                    "MUTATION");
            }
        }

        if (r.get("mutation_count").asInt() == 0) {
            add("Result", "No mutations detected", "Sequences are identical", "GOOD");
        }
    }

    private void add(String prop, String val, String desc, String cat) {
        results.add(new AnalysisResult(prop, val, desc, cat));
    }

    // ── Update charts ──────────────────────────────────────────
    private void updateCharts(JsonNode r) {
        compositionChart.getData().clear();
        distributionPie.getData().clear();

        String type = r.get("type").asText();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Frequency (%)");

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        if (type.equals("DNA") || type.equals("RNA")) {
            JsonNode freq = r.get("nucleotide_freq");
            if (freq != null) {
                freq.fields().forEachRemaining(e -> {
                    double val = e.getValue().asDouble();
                    series.getData().add(new XYChart.Data<>(e.getKey(), val));
                    pieData.add(new PieChart.Data(e.getKey() + " (" + val + "%)", val));
                });
            }
        } else if (type.equals("PROTEIN")) {
            JsonNode comp = r.get("aa_composition");
            if (comp != null) {
                comp.fields().forEachRemaining(e -> {
                    double pct = e.getValue().get("percentage").asDouble();
                    if (pct > 0) {
                        series.getData().add(new XYChart.Data<>(e.getKey(), pct));
                        if (pct > 3) { // Only show significant ones in pie
                            pieData.add(new PieChart.Data(
                                e.getKey() + " " + pct + "%", pct));
                        }
                    }
                });
            }
        }

        compositionChart.getData().add(series);
        distributionPie.setData(pieData);
    }

    // ── Update summary stat labels ─────────────────────────────
    private void updateSummary(JsonNode r) {
        String type = r.get("type").asText();
        labelLength.setText(r.get("length").asInt() + " bp");
        labelType.setText(type);

        if (r.has("gc_content")) {
            labelGC.setText(r.get("gc_content").asDouble() + "%");
        } else if (r.has("molecular_weight")) {
            labelGC.setText("N/A");
        }

        if (r.has("molecular_weight")) {
            labelMW.setText(String.format("%.0f Da", r.get("molecular_weight").asDouble()));
        } else {
            labelMW.setText("N/A");
        }
    }

    // ── Show full sequences in output area ─────────────────────
    private void updateSequenceOutput(JsonNode r) {
        StringBuilder sb = new StringBuilder();
        String type = r.get("type").asText();
        sb.append("=== ORIGINAL SEQUENCE (").append(type).append(") ===\n");
        sb.append(r.get("sequence").asText()).append("\n\n");

        if (r.has("complement")) {
            sb.append("=== COMPLEMENT ===\n").append(r.get("complement").asText()).append("\n\n");
        }
        if (r.has("reverse_complement")) {
            sb.append("=== REVERSE COMPLEMENT ===\n").append(r.get("reverse_complement").asText()).append("\n\n");
        }
        if (r.has("rna_transcript")) {
            sb.append("=== RNA TRANSCRIPT ===\n").append(r.get("rna_transcript").asText()).append("\n\n");
        }
        if (r.has("protein_translation")) {
            sb.append("=== PROTEIN TRANSLATION ===\n").append(r.get("protein_translation").asText()).append("\n\n");
        }
        if (r.has("dna_template")) {
            sb.append("=== DNA TEMPLATE ===\n").append(r.get("dna_template").asText()).append("\n\n");
        }
        if (r.has("orfs")) {
            sb.append("=== OPEN READING FRAMES ===\n");
            JsonNode orfs = r.get("orfs");
            for (int i = 0; i < Math.min(5, orfs.size()); i++) {
                JsonNode orf = orfs.get(i);
                sb.append(String.format("ORF %d: Start=%d End=%d Length=%d bp Frame=%d\n",
                    i+1, orf.get("start").asInt(), orf.get("end").asInt(),
                    orf.get("length").asInt(), orf.get("frame").asInt()));
                sb.append("  Protein: ").append(orf.get("protein").asText()).append("\n\n");
            }
        }

        sequenceOutputArea.setText(sb.toString());
    }

    // ── Clear ──────────────────────────────────────────────────
    @FXML
    private void onClearClicked() {
        sequenceInput.clear();
        results.clear();
        compositionChart.getData().clear();
        distributionPie.getData().clear();
        sequenceOutputArea.clear();
        labelLength.setText("0");
        labelType.setText("-");
        labelGC.setText("-");
        labelMW.setText("-");
        progressBar.setProgress(0);
        statusLabel.setText("Cleared. Enter a new sequence.");
        exportButton.setDisable(true);
    }

    // ── Export CSV ─────────────────────────────────────────────
    @FXML
    private void onExportClicked() {
        if (results.isEmpty()) {
            showAlert("No Data", "Run an analysis first.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Results");
        fc.setInitialFileName("biosequence_results.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = fc.showSaveDialog(exportButton.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(file)) {
            pw.println("Property,Value,Description,Category");
            for (AnalysisResult r : results) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    r.getProperty(), r.getValue(), r.getDescription(), r.getCategory());
            }
            statusLabel.setText("Exported " + results.size() + " rows to " + file.getName());
        } catch (IOException e) {
            showAlert("Export Error", e.getMessage());
        }
    }

    // ── Load sample sequences ──────────────────────────────────
    @FXML
    private void onLoadDNASample() {
        sequenceInput.setText("ATGCGATCGATCGGCTAGCTAGCATCGATCGATCGTAGCTAGCATCGATCGATCGTAGCTAGCATCGATCGATCG");
        seqTypeCombo.setValue("DNA");
        detectedTypeLabel.setText("Sample DNA loaded");
    }

    @FXML
    private void onLoadRNASample() {
        sequenceInput.setText("AUGGCUAGCUAGCUAGCUAGCUAGCUAGCUAGCUAGCUAGCUAGCUAA");
        seqTypeCombo.setValue("RNA");
        detectedTypeLabel.setText("Sample RNA loaded");
    }

    @FXML
    private void onLoadProteinSample() {
        sequenceInput.setText("MKTAYIAKQRQISFVKSHFSRQLEERLGLIEVQAPILSRVGDGTQDNLSGAEKAVQVKVKALPDAQFEVVHSLAKWKRQTLGQHDFSAGEGLYTHMKALRPDEDRLSPLHSVYVDQWLFRTLKFRENLK");
        seqTypeCombo.setValue("PROTEIN");
        detectedTypeLabel.setText("Sample Protein loaded");
    }

    @FXML
    private void onLoadMutationSample() {
        seq1Input.setText("ATGCGATCGATCGGCTAGCTAGCATCGATCG");
        seq2Input.setText("ATGCGATCGATCGGCTAGCAAGCATCGATCG");
        mainTabPane.getSelectionModel().select(1);
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
