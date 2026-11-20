package ru.amazy.crm.nativeclient;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A small native modal sheet with the same neutral surfaces as the mobile inbox. */
final class MobileSheet {
    interface Selection { void select(int which); }
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    private static GradientDrawable background(Activity a,int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;
    }
    private static TextView label(Activity a,String value,int size,int color,boolean strong){
        TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);
        return t;
    }
    static void show(Activity a,String title,String[] options,Selection selection) {
        if(a.isFinishing() || a.isDestroyed())return;
        Dialog dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=new LinearLayout(a);sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(a,16),dp(a,9),dp(a,16),dp(a,16));
        GradientDrawable surface=new GradientDrawable();surface.setColor(Color.WHITE);
        float r=dp(a,24);surface.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});
        sheet.setBackground(surface);
        View handle=new View(a);handle.setBackground(background(a,0xffd7d7d7,3));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(a,36),dp(a,4));
        hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(a,16);sheet.addView(handle,hp);
        TextView heading=label(a,title,18,0xff202020,true);heading.setPadding(dp(a,8),0,dp(a,8),dp(a,12));
        sheet.addView(heading);
        ScrollView scroll=new ScrollView(a);
        LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        for(int i=0;i<options.length;i++){
            int choice=i;TextView item=label(a,options[i],15,0xff202020,false);
            item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));
            item.setMinHeight(dp(a,49));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16000000),background(a,0xfff7f7f7,10),null));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(a,4);
            rows.addView(item,p);item.setOnClickListener(v->{dialog.dismiss();selection.select(choice);});
        }
        int h=Math.min(dp(a,options.length*54+5),a.getResources().getDisplayMetrics().heightPixels/2);
        sheet.addView(scroll,new LinearLayout.LayoutParams(-1,h));
        TextView cancel=label(a,"Отмена",14,0xff656565,true);cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v->dialog.dismiss());sheet.addView(cancel,new LinearLayout.LayoutParams(-1,dp(a,48)));
        dialog.setContentView(sheet);dialog.setCanceledOnTouchOutside(true);dialog.show();
        Window w=dialog.getWindow();if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setGravity(Gravity.BOTTOM);
            w.setLayout(-1,-2);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.28f);
            w.setNavigationBarColor(Color.WHITE);
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
}
