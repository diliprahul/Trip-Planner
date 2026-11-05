package com.tripplanner.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequest {
    private String password;
    private boolean confirmed;
}
