package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String role = "USER";
    private String plainPassword;
    private Long lastSeenTime;
    private String companyName;
    private String phone;
    
    // HR & Staff ID Card Fields
    private String employeeId;
    private String designation;
    private String department;
    private String staffPhoto;
    private String cardStatus = "ACTIVE";
    private String cardIssueDate;
    private String cardValidUntil;
    private String onlineStatus = "OFFLINE";
    private String attendanceStatus = "SIGNED_OUT";

    public User(String firstName, String lastName, String email, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
    }
}
