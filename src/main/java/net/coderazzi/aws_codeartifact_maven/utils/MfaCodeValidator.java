package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.ui.InputValidator;

import java.util.regex.Pattern;

public class MfaCodeValidator implements InputValidator {

    private final static Pattern MFA_PATTERN = Pattern.compile("^\\d{6}$");

    @Override
    public boolean checkInput(String s) {
        return MFA_PATTERN.matcher(s).matches();
    }

    @Override
    public boolean canClose(String s) {
        return checkInput(s);
    }
}
