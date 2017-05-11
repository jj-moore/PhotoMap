package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

class DialogCaption extends Dialog {
    private Context context;
    private ActivityInterface activityInterface;
    private String caption;

    DialogCaption(Context context, ActivityInterface activityInterface, String caption) {
        super(context);
        this.context = context;
        this.activityInterface = activityInterface;
        this.caption = caption;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Set Caption");
        setContentView(R.layout.dialog_caption);
        final EditText viewCaption = (EditText) findViewById(R.id.dialog_caption);
        viewCaption.setText(caption);
        findViewById(R.id.dialog_save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final InputMethodManager mgr = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                mgr.hideSoftInputFromWindow(viewCaption.getWindowToken(), 0);
                activityInterface.saveCaption(viewCaption.getText());
                DialogCaption.this.dismiss();
            }
        });
    }
}
