package com.ptit.oj.model;

/** Trung tam / to chuc dao tao (Module 2). Mot to chuc co nhieu lop hoc. */
public class Organization extends Entity {

    private final String name;
    private final String address;
    private final String description;

    public Organization(String id, String name, String address, String description) {
        super(id);
        this.name = name;
        this.address = address;
        this.description = description;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
}
