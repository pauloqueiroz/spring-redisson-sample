package br.com.example.sample.log;

public class SensitiveDataFilter {

    private static final String[] SENSITIVE_KEYS = {"password", "secret", "token"};

    public static String filterSensitiveData(String input) {
        String filtered = input;
        for (String key : SENSITIVE_KEYS) {
            filtered = filtered.replaceAll("(?i)(\\"?" + key + "\\"?\\s*[:=]\\s*)(\\".*?\\"|[^,}\]]+)", "$1***");
        }
        return filtered;
    }

}
