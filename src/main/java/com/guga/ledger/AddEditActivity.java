package com.guga.ledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddEditActivity extends Activity {

    private LedgerDb db;
    private long entryId = -1;
    private int type = 0; // 0=支出, 1=收入
    private String selectedCategory = null;
    private long dateMillis;

    private TextView tvTypeExpense, tvTypeIncome, tvDate, tvPageTitle;
    private EditText etAmount, etNote;
    private GridView gvCategories;
    private Button btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit);
        db = new LedgerDb(this);

        tvTypeExpense = findViewById(R.id.tvTypeExpense);
        tvTypeIncome = findViewById(R.id.tvTypeIncome);
        tvDate = findViewById(R.id.tvDate);
        tvPageTitle = findViewById(R.id.tvPageTitle);
        etAmount = findViewById(R.id.etAmount);
        etNote = findViewById(R.id.etNote);
        gvCategories = findViewById(R.id.gvCategories);
        btnDelete = findViewById(R.id.btnDelete);

        dateMillis = LedgerDb.dayStart(System.currentTimeMillis());
        gvCategories.setAdapter(catAdapter);
        gvCategories.setOnItemClickListener((p, v, pos, id) -> {
            selectedCategory = Categories.of(type).get(pos);
            catAdapter.notifyDataSetChanged();
        });

        tvTypeExpense.setOnClickListener(v -> setType(0));
        tvTypeIncome.setOnClickListener(v -> setType(1));

        tvDate.setOnClickListener(v -> pickDate());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());

        entryId = getIntent().getLongExtra("entry_id", -1);
        if (entryId > 0) {
            Entry e = loadEntry(entryId);
            if (e != null) {
                tvPageTitle.setText("编辑记录");
                type = e.type;
                selectedCategory = e.major;
                dateMillis = e.dateMillis;
                etAmount.setText(String.format(Locale.US, "%.2f", e.amountCents / 100.0));
                etNote.setText(e.note == null ? "" : e.note);
                btnDelete.setVisibility(View.VISIBLE);
            }
        }
        setType(type);
        updateDateLabel();
    }

    private Entry loadEntry(long id) {
        // 通过全范围查询取单条（数据量小，足够）
        List<Entry> all = db.list(0, Long.MAX_VALUE);
        for (Entry e : all) if (e.id == id) return e;
        return null;
    }

    private void setType(int t) {
        type = t;
        styleToggle(tvTypeExpense, t == 0);
        styleToggle(tvTypeIncome, t == 1);
        List<String> cats = Categories.of(t);
        if (selectedCategory == null || !cats.contains(selectedCategory)) {
            selectedCategory = cats.get(0);
        }
        // GridView 在 ScrollView 里需要明确高度：按行数算（每行约 78dp）
        int rows = (cats.size() + 3) / 4;
        float density = getResources().getDisplayMetrics().density;
        android.view.ViewGroup.LayoutParams lp = gvCategories.getLayoutParams();
        lp.height = (int) (rows * 78 * density);
        gvCategories.setLayoutParams(lp);
        catAdapter.notifyDataSetChanged();
    }

    private void styleToggle(TextView tv, boolean selected) {
        tv.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        tv.setTextColor(getColor(selected ? R.color.black : R.color.text_secondary));
        if (selected) tv.setTextColor(0xFF1A1206);
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateMillis);
        new DatePickerDialog(this, (view, y, m, d) -> {
            Calendar c = Calendar.getInstance();
            c.set(y, m, d, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            dateMillis = c.getTimeInMillis();
            updateDateLabel();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy年M月d日 E", Locale.CHINA);
        tvDate.setText("📅  " + df.format(dateMillis));
    }

    private void save() {
        String amtStr = etAmount.getText().toString().trim();
        if (amtStr.isEmpty()) {
            Toast.makeText(this, "先输金额啦 🐧", Toast.LENGTH_SHORT).show();
            return;
        }
        long cents;
        try {
            cents = new BigDecimal(amtStr).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (Exception ex) {
            Toast.makeText(this, "金额格式不对", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cents <= 0) {
            Toast.makeText(this, "金额要大于 0", Toast.LENGTH_SHORT).show();
            return;
        }
        Entry e = new Entry(type, cents, selectedCategory, "", etNote.getText().toString().trim(), dateMillis);
        if (entryId > 0) {
            e.id = entryId;
            db.update(e);
        } else {
            db.insert(e);
        }
        // 告诉主页：跳到这笔所在的月份显示，避免记完看不见
        getSharedPreferences("ledger_ui", MODE_PRIVATE).edit()
                .putLong("jump_to_date", dateMillis).apply();
        Toast.makeText(this, "记好啦 ✅", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage("确定删除这笔记录？")
                .setPositiveButton("删除", (d, w) -> { db.delete(entryId); finish(); })
                .setNegativeButton("取消", null)
                .show();
    }

    private final BaseAdapter catAdapter = new BaseAdapter() {
        @Override public int getCount() { return Categories.of(type).size(); }
        @Override public Object getItem(int p) { return Categories.of(type).get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AddEditActivity.this).inflate(R.layout.item_category, parent, false);
            }
            String cat = Categories.of(type).get(p);
            boolean sel = cat.equals(selectedCategory);
            convertView.setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            TextView icon = convertView.findViewById(R.id.tvCatIcon);
            TextView name = convertView.findViewById(R.id.tvCatName);
            icon.setText(Categories.icon(cat));
            name.setText(cat);
            name.setTextColor(sel ? 0xFF1A1206 : getColor(R.color.text_secondary));
            return convertView;
        }
    };
}
