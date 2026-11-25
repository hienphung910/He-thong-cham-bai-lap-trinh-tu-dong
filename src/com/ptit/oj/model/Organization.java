package com.ptit.oj.model;

/** Trung tam / to chuc dao tao (Module 2). Mot to chuc co nhieu lop hoc. */
public class Organization extends Entity {

    /** Lop nay duoc luu xuong o dia (StateStore): giu nguyen so nay de ban luu cu van doc duoc khi them truong. */
    private static final long serialVersionUID = 1L;

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
