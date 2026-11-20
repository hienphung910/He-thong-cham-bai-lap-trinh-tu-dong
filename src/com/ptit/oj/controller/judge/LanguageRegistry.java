package com.ptit.oj.controller.judge;

import java.util.Arrays;
import java.util.List;

/**
 * Noi duy nhat biet danh sach ngon ngu duoc ho tro.
 * Them ngon ngu moi = viet mot lop con Language roi them mot dong vao danh sach duoi day.
 */
public class LanguageRegistry {

    private List<Language> languages = Arrays.asList(
            new JavaLanguage(),
            new CppLanguage(),
            new PythonLanguage(),
            new JavaScriptLanguage());

    public List<Language> all() {
        return languages;
    }

    /** Tim ngon ngu theo ten, khong phan biet hoa thuong. Tra ve null neu khong co. */
    public Language byName(String name) {
        for (Language l : languages) {
            if (l.getName().equalsIgnoreCase(name)) return l;
        }
        return null;
    }
}
