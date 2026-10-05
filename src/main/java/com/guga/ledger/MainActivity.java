package com.guga.ledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private LedgerDb db;
    private int year, month; // month: 1-12
    private int tab = 0;     // 0=流水, 1=统计

    private TextView tvMonth, tvBalance, tvExpense, tvIncome, tvEmpty;
    private ListView lvEntries, lvStats;
    private TextView btnTabList, btnTabStats;

    private final List<Object> entryRows = new ArrayList<>(); // Header 或 Entry
    private final List<Object> statRows = new ArrayList<>();  // String(章节) 或 StatRow

    private static class Header {
        String dateLabel, sumLabel;
        Header(String d, String s) { dateLabel = d; sumLabel = s; }
    }

    private static class StatRow {
        String icon, name;
        long amount;
        int type;
        double pct, frac;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new LedgerDb(this);

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;

        tvMonth = findViewById(R.id.tvMonth);
        tvBalance = findViewById(R.id.tvBalance);
        tvExpense = findViewById(R.id.tvExpense);
        tvIncome = findViewById(R.id.tvIncome);
        tvEmpty = findViewById(R.id.tvEmpty);
        lvEntries = findViewById(R.id.lvEntries);
        lvStats = findViewById(R.id.lvStats);
        btnTabList = findViewById(R.id.btnTabList);
        btnTabStats = findViewById(R.id.btnTabStats);

        lvEntries.setAdapter(entryAdapter);
        lvStats.setAdapter(statAdapter);

        findViewById(R.id.btnPrevMonth).setOnClickListener(v -> shiftMonth(-1));
        findViewById(R.id.btnNextMonth).setOnClickListener(v -> shiftMonth(1));
        findViewById(R.id.btnAbout).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btnAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddEditActivity.class)));
        btnTabList.setOnClickListener(v -> { tab = 0; refresh(); });
        btnTabStats.setOnClickListener(v -> { tab = 1; refresh(); });

        lvEntries.setOnItemClickListener((parent, view, pos, id) -> {
            Object o = entryRows.get(pos);
            if (o instanceof Entry) openEdit((Entry) o);
        });
        lvEntries.setOnItemLongClickListener((parent, view, pos, id) -> {
            Object o = entryRows.get(pos);
            if (o instanceof Entry) {
                showEntryMenu((Entry) o);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 若刚保存的记录在别的月份，自动切过去显示
        android.content.SharedPreferences sp = getSharedPreferences("ledger_ui", MODE_PRIVATE);
        long jump = sp.getLong("jump_to_date", 0);
        if (jump > 0) {
            sp.edit().remove("jump_to_date").apply();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(jump);
            year = cal.get(Calendar.YEAR);
            month = cal.get(Calendar.MONTH) + 1;
            tab = 0;
        }
        refresh();
    }

    private void shiftMonth(int delta) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        cal.add(Calendar.MONTH, delta);
        year = cal.get(Calendar.YEAR);
        month = cal.get(Calendar.MONTH) + 1;
        refresh();
    }

    private void openEdit(Entry e) {
        Intent it = new Intent(this, AddEditActivity.class);
        it.putExtra("entry_id", e.id);
        startActivity(it);
    }

    private void showEntryMenu(Entry e) {
        new AlertDialog.Builder(this)
                .setTitle(Categories.icon(e.major) + " " + e.major + "  " + Utils.moneySigned(e.amountCents, e.type))
                .setItems(new String[]{"编辑", "删除"}, (d, which) -> {
                    if (which == 0) {
                        openEdit(e);
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage("确定删除这笔记录？")
                                .setPositiveButton("删除", (d2, w2) -> { db.delete(e.id); refresh(); })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    private void refresh() {
        tvMonth.setText(year + "年" + month + "月");
        long from = LedgerDb.monthStart(year, month);
        long to = LedgerDb.nextMonthStart(year, month);

        long exp = db.sum(from, to, 0);
        long inc = db.sum(from, to, 1);
        tvBalance.setText(Utils.money(inc - exp));
        tvExpense.setText("支出 " + Utils.money(exp));
        tvIncome.setText("收入 " + Utils.money(inc));

        // tab 样式
        btnTabList.setTextColor(getColor(tab == 0 ? R.color.amber : R.color.text_secondary));
        btnTabStats.setTextColor(getColor(tab == 1 ? R.color.amber : R.color.text_secondary));

        if (tab == 0) {
            lvEntries.setVisibility(View.VISIBLE);
            lvStats.setVisibility(View.GONE);
            buildEntryRows(from, to);
            tvEmpty.setVisibility(entryRows.isEmpty() ? View.VISIBLE : View.GONE);
            entryAdapter.notifyDataSetChanged();
        } else {
            lvEntries.setVisibility(View.GONE);
            lvStats.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            buildStatRows(from, to, exp, inc);
            statAdapter.notifyDataSetChanged();
        }
    }

    private void buildEntryRows(long from, long to) {
        entryRows.clear();
        List<Entry> list = db.list(from, to);
        SimpleDateFormat df = new SimpleDateFormat("M月d日 E", Locale.CHINA);
        long curDay = -1;
        long dayExp = 0, dayInc = 0;
        Header curHeader = null;
        for (Entry e : list) {
            long day = LedgerDb.dayStart(e.dateMillis);
            if (day != curDay) {
                if (curHeader != null) curHeader.sumLabel = daySumLabel(dayExp, dayInc);
                curDay = day;
                dayExp = 0;
                dayInc = 0;
                curHeader = new Header(df.format(e.dateMillis), "");
                entryRows.add(curHeader);
            }
            if (e.type == 0) dayExp += e.amountCents; else dayInc += e.amountCents;
            entryRows.add(e);
        }
        if (curHeader != null) curHeader.sumLabel = daySumLabel(dayExp, dayInc);
    }

    private String daySumLabel(long exp, long inc) {
        StringBuilder sb = new StringBuilder();
        if (exp > 0) sb.append("支 ").append(Utils.money(exp));
        if (inc > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("收 ").append(Utils.money(inc));
        }
        return sb.toString();
    }

    private void buildStatRows(long from, long to, long expTotal, long incTotal) {
        statRows.clear();
        addStatSection(from, to, 0, expTotal, "支出分布");
        addStatSection(from, to, 1, incTotal, "收入分布");
        if (statRows.isEmpty()) statRows.add("本月暂无数据");
    }

    private void addStatSection(long from, long to, int type, long total, String title) {
        Map<String, Long> byCat = db.sumByMajor(from, to, type);
        if (byCat.isEmpty() || total == 0) return;
        statRows.add(title + " · " + Utils.money(total));
        long max = 0;
        for (long v : byCat.values()) max = Math.max(max, v);
        for (Map.Entry<String, Long> en : byCat.entrySet()) {
            StatRow r = new StatRow();
            r.icon = Categories.icon(en.getKey());
            r.name = en.getKey();
            r.amount = en.getValue();
            r.type = type;
            r.pct = total == 0 ? 0 : en.getValue() * 100.0 / total;
            r.frac = max == 0 ? 0 : en.getValue() * 1.0 / max;
            statRows.add(r);
        }
    }

    // ---------------- 流水 adapter ----------------
    private final BaseAdapter entryAdapter = new BaseAdapter() {
        @Override public int getCount() { return entryRows.size(); }
        @Override public Object getItem(int p) { return entryRows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return entryRows.get(p) instanceof Header ? 0 : 1; }

        @Override
        public View getView(int p, View convertView, ViewGroup parent) {
            Object o = entryRows.get(p);
            if (o instanceof Header) {
                if (convertView == null || convertView.getTag() == null || !(convertView.getTag() instanceof HeaderHolder)) {
                    convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_header, parent, false);
                    convertView.setTag(new HeaderHolder(convertView));
                }
                HeaderHolder h = (HeaderHolder) convertView.getTag();
                Header hd = (Header) o;
                h.tvDate.setText(hd.dateLabel);
                h.tvDaySum.setText(hd.sumLabel);
                return convertView;
            }
            if (convertView == null || convertView.getTag() == null || !(convertView.getTag() instanceof EntryHolder)) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_entry, parent, false);
                convertView.setTag(new EntryHolder(convertView));
            }
            EntryHolder h = (EntryHolder) convertView.getTag();
            Entry e = (Entry) o;
            h.tvIcon.setText(Categories.icon(e.major));
            String title = e.major;
            h.tvTitle.setText(title);
            h.tvSub.setText(e.note == null || e.note.isEmpty() ? "无备注" : e.note);
            h.tvAmount.setText(Utils.moneySigned(e.amountCents, e.type));
            h.tvAmount.setTextColor(getColor(e.type == 1 ? R.color.income : R.color.expense));
            return convertView;
        }
    };

    private static class HeaderHolder {
        TextView tvDate, tvDaySum;
        HeaderHolder(View v) { tvDate = v.findViewById(R.id.tvDate); tvDaySum = v.findViewById(R.id.tvDaySum); }
    }

    private static class EntryHolder {
        TextView tvIcon, tvTitle, tvSub, tvAmount;
        EntryHolder(View v) {
            tvIcon = v.findViewById(R.id.tvIcon);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSub = v.findViewById(R.id.tvSub);
            tvAmount = v.findViewById(R.id.tvAmount);
        }
    }

    // ---------------- 统计 adapter ----------------
    private final BaseAdapter statAdapter = new BaseAdapter() {
        @Override public int getCount() { return statRows.size(); }
        @Override public Object getItem(int p) { return statRows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return statRows.get(p) instanceof String ? 0 : 1; }

        @Override
        public View getView(int p, View convertView, ViewGroup parent) {
            Object o = statRows.get(p);
            if (o instanceof String) {
                if (convertView == null || !(convertView.getTag() instanceof String)) {
                    convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_section, parent, false);
                    convertView.setTag("section");
                }
                ((TextView) convertView.findViewById(R.id.tvSection)).setText((String) o);
                return convertView;
            }
            StatRow r = (StatRow) o;
            if (convertView == null || convertView.getTag() == null || "section".equals(convertView.getTag())) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_stat, parent, false);
                convertView.setTag(new StatHolder(convertView));
            }
            StatHolder h = (StatHolder) convertView.getTag();
            h.tvName.setText(r.icon + "  " + r.name);
            h.tvAmount.setText(Utils.money(r.amount));
            h.tvAmount.setTextColor(getColor(r.type == 1 ? R.color.income : R.color.text_primary));
            h.tvPct.setText(String.format(Locale.CHINA, "%.1f%%", r.pct));
            View fill = h.vFill;
            fill.post(() -> {
                int w = ((View) fill.getParent()).getWidth();
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = Math.max(6, (int) (w * r.frac));
                fill.setLayoutParams(lp);
            });
            return convertView;
        }
    };

    private static class StatHolder {
        TextView tvName, tvAmount, tvPct;
        View vFill;
        StatHolder(View v) {
            tvName = v.findViewById(R.id.tvName);
            tvAmount = v.findViewById(R.id.tvAmount);
            tvPct = v.findViewById(R.id.tvPct);
            vFill = v.findViewById(R.id.vFill);
        }
    }
}
