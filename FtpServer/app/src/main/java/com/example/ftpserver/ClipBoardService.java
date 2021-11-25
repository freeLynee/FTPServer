package com.example.ftpserver;

import android.app.Activity;
import android.content.Context;

public class ClipBoardService {
    private Context contxt;
    private MainActivity activity;

    public Context getContxt() {
        return contxt;
    }

    public void setContxt(Context contxt) {
        this.contxt = (MainActivity) contxt;
    }

    public Activity getActivity() {
        return (MainActivity) activity;
    }

    public void setActivity(Activity activity) {
        this.activity = (MainActivity) activity;
    }

    public ClipBoardService(Context context, MainActivity activity) {
        this.setContxt(context);
        this.setActivity(activity);
    }

}
