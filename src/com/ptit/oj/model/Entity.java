package com.ptit.oj.model;

import java.util.Objects;

/** Lop cha truu tuong cho moi thuc the co ma dinh danh trong he thong. */
public abstract class Entity implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;

    protected Entity(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id không được rỗng");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** Hai thuc the bang nhau khi cung lop va cung ma - can cho viec gom bai nop theo nguoi dung. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id.equals(((Entity) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + id + "}";
    }
}
