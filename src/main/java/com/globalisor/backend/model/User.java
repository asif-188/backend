package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String firstName;
    private String lastName;
    @Indexed(unique = true)
    private String email;
    private String password;
    @Indexed
    private String role = "USER";
    @Indexed
    private String status = "ACTIVE";
    private String plainPassword;
    private Long lastSeenTime;
    private Long lastLoginTime;
    private String lastLoginAt;
    @Indexed
    private String companyName;
    private String phone;
    
    // HR & Staff ID Card Fields
    @Indexed
    private String employeeId;
    private String designation;
    private String department;
    private String staffPhoto;
    @Indexed
    private String cardStatus = "ACTIVE";
    private String cardIssueDate;
    private String cardValidUntil;
    private String onlineStatus = "OFFLINE";
    private String attendanceStatus = "SIGNED_OUT";

    // Staff Client Assignment Fields
    @Indexed
    private String assignedStaffId;
    private String assignedStaffName;
    private String assignedStaffEmail;
    private Long assignedAt;
    private String assignedBy;

    public User(String firstName, String lastName, String email, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
    }
}
