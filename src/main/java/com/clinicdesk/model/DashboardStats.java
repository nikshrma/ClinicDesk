package com.clinicdesk.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Value object holding aggregated dashboard metrics.
 */
public class DashboardStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int activePatientsCount;
    private int todayTotalAppointments;
    private int todayCheckedIn;
    private int todayCompleted;
    private int todayCancelled;
    private int totalDocumentsCount;

    private List<Appointment> todayAppointments;
    private List<Patient> recentPatients;
    private List<DocumentMetadata> recentDocuments;

    public DashboardStats() {
    }

    public int getActivePatientsCount() {
        return activePatientsCount;
    }

    public void setActivePatientsCount(int activePatientsCount) {
        this.activePatientsCount = activePatientsCount;
    }

    public int getTodayTotalAppointments() {
        return todayTotalAppointments;
    }

    public void setTodayTotalAppointments(int todayTotalAppointments) {
        this.todayTotalAppointments = todayTotalAppointments;
    }

    public int getTodayCheckedIn() {
        return todayCheckedIn;
    }

    public void setTodayCheckedIn(int todayCheckedIn) {
        this.todayCheckedIn = todayCheckedIn;
    }

    public int getTodayCompleted() {
        return todayCompleted;
    }

    public void setTodayCompleted(int todayCompleted) {
        this.todayCompleted = todayCompleted;
    }

    public int getTodayCancelled() {
        return todayCancelled;
    }

    public void setTodayCancelled(int todayCancelled) {
        this.todayCancelled = todayCancelled;
    }

    public int getTotalDocumentsCount() {
        return totalDocumentsCount;
    }

    public void setTotalDocumentsCount(int totalDocumentsCount) {
        this.totalDocumentsCount = totalDocumentsCount;
    }

    public List<Appointment> getTodayAppointments() {
        return todayAppointments;
    }

    public void setTodayAppointments(List<Appointment> todayAppointments) {
        this.todayAppointments = todayAppointments;
    }

    public List<Patient> getRecentPatients() {
        return recentPatients;
    }

    public void setRecentPatients(List<Patient> recentPatients) {
        this.recentPatients = recentPatients;
    }

    public List<DocumentMetadata> getRecentDocuments() {
        return recentDocuments;
    }

    public void setRecentDocuments(List<DocumentMetadata> recentDocuments) {
        this.recentDocuments = recentDocuments;
    }
}
