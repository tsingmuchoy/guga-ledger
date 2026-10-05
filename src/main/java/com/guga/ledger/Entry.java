package com.guga.ledger;

public class Entry {
    public long id;
    public int type;          // 0=支出, 1=收入
    public long amountCents;  // 金额（分）
    public String major;      // 一级分类
    public String minor;      // 二级分类
    public String note;       // 备注
    public long dateMillis;   // 日期（当天 0 点时间戳）

    public Entry() {}

    public Entry(int type, long amountCents, String major, String minor, String note, long dateMillis) {
        this.type = type;
        this.amountCents = amountCents;
        this.major = major;
        this.minor = minor;
        this.note = note;
        this.dateMillis = dateMillis;
    }
}
