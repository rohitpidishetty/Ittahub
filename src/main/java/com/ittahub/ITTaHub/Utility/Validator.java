package com.ittahub.ITTaHub.Utility;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class Validator {
    public boolean emailAddress(String email) {
        return Pattern.matches("^[a-zA-Z0-9._%+-]+@gmail\\.com$", email);
    }

    public boolean phoneNumber(String phone) {
        return Pattern.matches("^(?:\\+91[\\-\\s]?|91[\\-\\s]?|0?)[6-9]\\d{9}$", phone);
    }

    public boolean firstName(String name) {
        return Pattern.matches("^[A-Za-z]{2,}( [A-Za-z]*)?$", name);
    }



    public boolean lastName(String name) {
        return Pattern.matches("^[A-Za-z]+$", name);
    }
}

