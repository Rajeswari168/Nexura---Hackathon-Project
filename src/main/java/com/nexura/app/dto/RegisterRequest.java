package com.nexura.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    private String fullName;
    private String email;
    private String password;
    private Integer age;
    private String emergencyCaregiverName;
    private String caregiverPhoneNumber;
}
