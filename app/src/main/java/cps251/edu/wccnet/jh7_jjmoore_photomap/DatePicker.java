package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

import java.util.Calendar;

public class DatePicker extends DialogFragment implements DatePickerDialog.OnDateSetListener {
    private DatabaseManager dbManager;
    private int id;

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        dbManager = (DatabaseManager) getArguments().getSerializable("dbManager");
        id = getArguments().getInt("id");

        final Calendar calendar = Calendar.getInstance();
        final int year = getArguments().getInt("year", calendar.get(Calendar.YEAR));
        final int month = getArguments().getInt("month", calendar.get(Calendar.MONTH));
        final int day = getArguments().getInt("day", calendar.get(Calendar.DAY_OF_MONTH));
        return new DatePickerDialog(getActivity(), this, year, month, day);
    }

    public void onDateSet(android.widget.DatePicker view, int year, int month, int date) {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, date);
        dbManager.updateDate(id, calendar.getTimeInMillis());
    }

}
