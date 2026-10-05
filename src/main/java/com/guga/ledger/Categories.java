package com.guga.ledger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 一级分类（不细分）：type 0=支出, 1=收入 */
public class Categories {

    public static final List<String> EXPENSE = Arrays.asList(
            "餐饮", "交通", "房租", "水费", "电费", "燃气费", "话费网费",
            "购物", "日用品", "娱乐", "游戏", "会员订阅",
            "学习", "医疗", "运动健身", "旅游", "人情往来", "宠物", "其他支出");

    public static final List<String> INCOME = Arrays.asList(
            "工资", "生活费", "兼职", "红包", "退款", "其他收入");

    public static List<String> of(int type) {
        return type == 1 ? INCOME : EXPENSE;
    }

    public static String icon(String category) {
        switch (category) {
            case "餐饮": return "🍜";
            case "交通": return "🚌";
            case "房租": return "🏠";
            case "水费": return "💧";
            case "电费": return "💡";
            case "燃气费": return "🔥";
            case "话费网费": return "📶";
            case "购物": return "🛍️";
            case "日用品": return "🧴";
            case "娱乐": return "🎬";
            case "游戏": return "🎮";
            case "会员订阅": return "👑";
            case "学习": return "📚";
            case "医疗": return "💊";
            case "运动健身": return "🏃";
            case "旅游": return "✈️";
            case "人情往来": return "🧧";
            case "宠物": return "🐱";
            case "工资": return "💼";
            case "生活费": return "🏦";
            case "兼职": return "🧑‍💻";
            case "红包": return "🧧";
            case "退款": return "↩️";
            case "其他收入": return "💰";
            default: return "📦";
        }
    }

    public static List<String> all() {
        List<String> l = new ArrayList<>(EXPENSE);
        l.addAll(INCOME);
        return l;
    }
}
