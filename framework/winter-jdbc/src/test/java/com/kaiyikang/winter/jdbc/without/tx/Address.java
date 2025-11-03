package com.kaiyikang.winter.jdbc.without.tx;

public class Address {
    public int id;
    public int userName;
    public String address;
    public int zipcode;

    public void setZip(Integer zip) {
        this.zipcode = zip == null ? 0 : zip.intValue();
    }
}
