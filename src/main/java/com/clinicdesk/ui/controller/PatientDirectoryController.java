package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Patient;
import com.clinicdesk.model.Role;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.PatientService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Patient Directory screen.
 */
public class PatientDirectoryController {

    private static final Logger LOGGER = AppLogger.getLogger(PatientDirectoryController.class);

    @FXML private TextField searchField;
    @FXML private CheckBox showArchivedCheck;
    @FXML private TableView<Patient> patientsTable;
    @FXML private TableColumn<Patient, String> colPatientNumber;
    @FXML private TableColumn<Patient, String> colFullName;
    @FXML private TableColumn<Patient, String> colAgeDob;
    @FXML private TableColumn<Patient, String> colGender;
    @FXML private TableColumn<Patient, String> colPhone;
    @FXML private TableColumn<Patient, String> colBloodGroup;
    @FXML private TableColumn<Patient, String> colStatus;
    @FXML private TableColumn<Patient, Void> colActions;

    @FXML private Label pageInfoLabel;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;

    private final PatientService patientService;
    private final ObservableList<Patient> patientsData = FXCollections.observableArrayList();
    private int currentPage = 0;
    private static final int PAGE_SIZE = 25;

    public PatientDirectoryController() {
        this.patientService = new PatientService(new MySQLPatientDAO());
    }

    public PatientDirectoryController(PatientService patientService) {
        this.patientService = patientService;
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        loadPatients();

        // Double click to view patient details
        patientsTable.setRowFactory(tv -> {
            TableRow<Patient> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    viewPatientDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupTableColumns() {
        colPatientNumber.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPatientNumber()));
        colFullName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFullName()));
        colAgeDob.setCellValueFactory(data -> {
            Patient p = data.getValue();
            return new SimpleStringProperty(p.getAge() + " yrs (" + p.getDateOfBirth() + ")");
        });
        colGender.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getGender() != null ? data.getValue().getGender().getDisplayName() : "—"));
        colPhone.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPhone()));
        colBloodGroup.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBloodGroup() != null ? data.getValue().getBloodGroup() : "—"));

        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isArchived() ? "Archived" : "Active"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    if ("Archived".equalsIgnoreCase(item)) {
                        badge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #B91C1C; -fx-padding: 2px 8px; -fx-background-radius: 8px; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else {
                        badge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D; -fx-padding: 2px 8px; -fx-background-radius: 8px; -fx-font-size: 11px; -fx-font-weight: bold;");
                    }
                    setGraphic(badge);
                }
            }
        });

        // Action Buttons Column
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final Button editBtn = new Button("Edit");
            private final Button archiveBtn = new Button("Archive");
            private final HBox pane = new HBox(6, viewBtn, editBtn, archiveBtn);

            {
                viewBtn.getStyleClass().add("btn-secondary");
                editBtn.getStyleClass().add("btn-secondary");
                archiveBtn.getStyleClass().add("btn-secondary");

                viewBtn.setOnAction(e -> {
                    Patient p = getTableView().getItems().get(getIndex());
                    viewPatientDetails(p);
                });

                editBtn.setOnAction(e -> {
                    Patient p = getTableView().getItems().get(getIndex());
                    openEditDialog(p);
                });

                archiveBtn.setOnAction(e -> {
                    Patient p = getTableView().getItems().get(getIndex());
                    toggleArchive(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Patient p = getTableView().getItems().get(getIndex());
                    archiveBtn.setText(p.isArchived() ? "Restore" : "Archive");
                    // Receptionists cannot archive/restore
                    archiveBtn.setVisible(SessionContext.hasRole(Role.ADMIN, Role.DOCTOR));
                    archiveBtn.setManaged(SessionContext.hasRole(Role.ADMIN, Role.DOCTOR));
                    setGraphic(pane);
                }
            }
        });

        patientsTable.setItems(patientsData);
    }

    public void loadPatients() {
        try {
            boolean includeArchived = showArchivedCheck.isSelected();
            String query = searchField.getText();
            List<Patient> result;

            if (query != null && !query.isBlank()) {
                result = patientService.searchPatients(query, includeArchived);
            } else {
                result = patientService.getPatients(currentPage, PAGE_SIZE, includeArchived);
            }

            patientsData.setAll(result);
            pageInfoLabel.setText("Page " + (currentPage + 1));
            prevPageBtn.setDisable(currentPage == 0);
            nextPageBtn.setDisable(result.size() < PAGE_SIZE);
        } catch (ClinicDeskException e) {
            LOGGER.log(Level.SEVERE, "Failed to load patients", e);
            showAlert("Error loading patients: " + e.getMessage());
        }
    }

    @FXML
    public void handleSearch(KeyEvent event) {
        currentPage = 0;
        loadPatients();
    }

    @FXML
    public void handleFilterToggle(ActionEvent event) {
        currentPage = 0;
        loadPatients();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadPatients();
    }

    @FXML
    public void handlePrevPage(ActionEvent event) {
        if (currentPage > 0) {
            currentPage--;
            loadPatients();
        }
    }

    @FXML
    public void handleNextPage(ActionEvent event) {
        currentPage++;
        loadPatients();
    }

    @FXML
    public void handleOpenRegistration(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PatientFormDialog.fxml"));
            Parent root = loader.load();

            PatientFormDialogController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Register New Patient");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                loadPatients();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open patient registration dialog", e);
            showAlert("Error opening registration form: " + e.getMessage());
        }
    }

    private void openEditDialog(Patient patient) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PatientFormDialog.fxml"));
            Parent root = loader.load();

            PatientFormDialogController controller = loader.getController();
            controller.setPatientToEdit(patient);

            Stage stage = new Stage();
            stage.setTitle("Edit Patient — " + patient.getPatientNumber());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                loadPatients();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open edit patient dialog", e);
            showAlert("Error opening edit form: " + e.getMessage());
        }
    }

    private void viewPatientDetails(Patient patient) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PatientDetailView.fxml"));
            Parent root = loader.load();

            PatientDetailController controller = loader.getController();
            controller.setPatient(patient);

            // Replace content in parent StackPane if embedded
            StackPane contentArea = (StackPane) patientsTable.getScene().lookup("#contentArea");
            if (contentArea != null) {
                contentArea.getChildren().setAll(root);
            } else {
                Stage stage = new Stage();
                stage.setTitle("Patient Profile — " + patient.getFullName());
                stage.setScene(new Scene(root, 1050, 700));
                stage.show();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load patient detail view", e);
            showAlert("Error loading patient details: " + e.getMessage());
        }
    }

    private void toggleArchive(Patient patient) {
        try {
            if (patient.isArchived()) {
                patientService.unarchivePatient(patient.getId());
            } else {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Are you sure you want to archive patient " + patient.getFullName() + "?\n" +
                        "Archived patients cannot receive new appointments, but existing medical records will remain accessible.",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Patient Archival");
                confirm.showAndWait();
                if (confirm.getResult() == ButtonType.YES) {
                    patientService.archivePatient(patient.getId());
                } else {
                    return;
                }
            }
            loadPatients();
        } catch (ClinicDeskException e) {
            LOGGER.log(Level.WARNING, "Archival action failed", e);
            showAlert(e.getMessage());
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
