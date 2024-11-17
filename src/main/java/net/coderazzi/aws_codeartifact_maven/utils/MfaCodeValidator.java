package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.ui.InputValidator;

import java.util.regex.Pattern;

public class MfaCodeValidator implements InputValidator {

    private final Pattern mfaPattern = Pattern.compile("^\\d+$");

    @Override
    public boolean checkInput(String s) {
        return s.length() >=6 && mfaPattern.matcher(s).matches();
    }

    @Override
    public boolean canClose(String s) {
        return checkInput(s);
    }
}
