package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Gender;
import com.clinicdesk.model.Patient;
import com.clinicdesk.service.PatientService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for Patient Registration and Profile Editing dialog.
 */
public class PatientFormDialogController {

    private static final Logger LOGGER = AppLogger.getLogger(PatientFormDialogController.class);

    @FXML private Label dialogTitle;
    @FXML private Label formErrorLabel;
    @FXML private TextField fullNameField;
    @FXML private DatePicker dobPicker;
    @FXML private ComboBox<Gender> genderComboBox;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> bloodGroupComboBox;
    @FXML private TextField emergencyContactField;
    @FXML private TextArea allergiesArea;
    @FXML private TextArea addressArea;
    @FXML private Button saveButton;

    private final PatientService patientService;
    private Patient patientToEdit;
    private boolean saved = false;

    public PatientFormDialogController() {
        this.patientService = new PatientService(new MySQLPatientDAO());
    }

    public PatientFormDialogController(PatientService patientService) {
        this.patientService = patientService;
    }

    @FXML
    public void initialize() {
        genderComboBox.setItems(FXCollections.observableArrayList(Gender.values()));
        bloodGroupComboBox.setItems(FXCollections.observableArrayList(
                "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
        ));
        hideError();
    }

    public void setPatientToEdit(Patient patient) {
        this.patientToEdit = patient;
        if (patient != null) {
            dialogTitle.setText("Edit Patient Profile — " + patient.getPatientNumber());
            saveButton.setText("Update Patient");

            fullNameField.setText(patient.getFullName());
            dobPicker.setValue(patient.getDateOfBirth());
            genderComboBox.setValue(patient.getGender());
            phoneField.setText(patient.getPhone());
            emailField.setText(patient.getEmail());
            bloodGroupComboBox.setValue(patient.getBloodGroup());
            emergencyContactField.setText(patient.getEmergencyContact());
            allergiesArea.setText(patient.getAllergies());
            addressArea.setText(patient.getAddress());
        }
    }

    public boolean isSaved() {
        return saved;
    }

    public Patient getSavedPatient() {
        return patientToEdit;
    }

    @FXML
    public void handleSave(ActionEvent event) {
        hideError();
        String name = fullNameField.getText();
        LocalDate dob = dobPicker.getValue();
        String phone = phoneField.getText();

        if (name == null || name.trim().length() < 2) {
            showError("Full Name is required (minimum 2 characters).");
            return;
        }
        if (dob == null || dob.isAfter(LocalDate.now())) {
            showError("Valid Date of Birth is required (must be in the past).");
            return;
        }
        if (phone == null || phone.trim().length() < 7) {
            showError("Valid phone number is required.");
            return;
        }

        try {
            if (patientToEdit == null) {
                // New registration
                Patient newPatient = new Patient();
                populatePatient(newPatient);
                patientToEdit = patientService.registerPatient(newPatient);
            } else {
                // Updating existing
                populatePatient(patientToEdit);
                patientService.updatePatient(patientToEdit);
            }

            saved = true;
            closeDialog();
        } catch (ClinicDeskException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Failed to save patient form", e);
            showError(e.getMessage());
        }
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        closeDialog();
    }

    private void populatePatient(Patient p) {
        p.setFullName(fullNameField.getText().trim());
        p.setDateOfBirth(dobPicker.getValue());
        p.setGender(genderComboBox.getValue());
        p.setPhone(phoneField.getText().trim());
        p.setEmail(emailField.getText() != null && !emailField.getText().isBlank() ? emailField.getText().trim() : null);
        p.setBloodGroup(bloodGroupComboBox.getValue());
        p.setEmergencyContact(emergencyContactField.getText() != null && !emergencyContactField.getText().isBlank() ? emergencyContactField.getText().trim() : null);
        p.setAllergies(allergiesArea.getText() != null && !allergiesArea.getText().isBlank() ? allergiesArea.getText().trim() : null);
        p.setAddress(addressArea.getText() != null && !addressArea.getText().isBlank() ? addressArea.getText().trim() : null);
    }

    private void closeDialog() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        formErrorLabel.setText(message);
        formErrorLabel.setVisible(true);
        formErrorLabel.setManaged(true);
    }

    private void hideError() {
        formErrorLabel.setText("");
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);
    }
}
