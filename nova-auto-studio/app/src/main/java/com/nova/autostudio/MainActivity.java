package com.nova.autostudio;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.io.*;

public class MainActivity extends Activity {
  private final ArrayList<Uri> images = new ArrayList<>();
  private TextView status;
  private Button export;
  private Spinner ratio;
  private static final int PICK=101;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    LinearLayout root=new LinearLayout(this); root.setOrientation(1);root.setPadding(24,32,24,24);
    ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    TextView title=new TextView(this);title.setText("NOVA Auto Studio");title.setTextSize(28);root.addView(title);
    TextView note=new TextView(this);note.setText("نسخة تجريبية: مونتاج صور متحركة وتصدير MP4 محلياً. الصوت والفيديوهات الأصلية غير مدعومة بعد.");root.addView(note);
    Button pick=new Button(this);pick.setText("اختيار صور");root.addView(pick);
    pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);});
    ratio=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"9:16","16:9","1:1"});ratio.setAdapter(adapter);root.addView(ratio);
    export=new Button(this);export.setText("تصدير MP4 (720p)");root.addView(export);
    status=new TextView(this);status.setText("اختار الصور أولاً");root.addView(status);
    export.setOnClickListener(v->{
      if(images.isEmpty()){status.setText("اختار صورة واحدة على الأقل");return;}
      export.setEnabled(false);status.setText("التصدير جاري...");
      ArrayList<Uri> selected=new ArrayList<>(images);int selectedRatio=ratio.getSelectedItemPosition();
      new Thread(()->{try{
        File output=new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"nova-"+System.currentTimeMillis()+".mp4");
        render(selected,selectedRatio,output);
        runOnUiThread(()->{status.setText("نجح التصدير: "+output.getAbsolutePath());export.setEnabled(true);});
      }catch(Exception ex){runOnUiThread(()->{status.setText("فشل التصدير: "+ex.getMessage());export.setEnabled(true);});}}).start();
    });
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==PICK&&result==RESULT_OK&&data!=null){
      images.clear();
      if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)images.add(data.getClipData().getItemAt(j).getUri());}
      else if(data.getData()!=null)images.add(data.getData());
      status.setText("عدد الصور: "+images.size());
    }
  }
  private void render(ArrayList<Uri> inputs,int aspect,File output)throws Exception{
    int width=aspect==0?720:aspect==1?1280:720;
    int height=aspect==0?1280:aspect==1?720:720;
    final int fps=30, framesPerImage=75;
    MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);
    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    format.setInteger(MediaFormat.KEY_BIT_RATE,2500000);
    format.setInteger(MediaFormat.KEY_FRAME_RATE,fps);
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
    MediaCodec codec=MediaCodec.createEncoderByType("video/avc");
    codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
    Surface surface=codec.createInputSurface();
    MediaMuxer muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    boolean started=false;int track=-1;
    try{
      codec.start();MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
      for(int idx=0;idx<inputs.size();idx++){
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
        Bitmap bitmap;
        try(InputStream in=getContentResolver().openInputStream(inputs.get(idx))){bitmap=BitmapFactory.decodeStream(in,null,opts);}
        if(bitmap==null)throw new IOException("Unable to decode image "+idx);
        for(int f=0;f<framesPerImage;f++){
          Canvas canvas=surface.lockCanvas(null);
          canvas.drawColor(Color.BLACK);
          float scale=Math.max((float)width/bitmap.getWidth(),(float)height/bitmap.getHeight());
          scale*=1f+0.09f*f/framesPerImage;
          float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
          float dx=(width-w)/2f,dy=(height-h)/2f;
          canvas.drawBitmap(bitmap,null,new RectF(dx,dy,dx+w,dy+h),paint);
          surface.unlockCanvasAndPost(canvas);
          int attempts=0;
          while(attempts++<3){
            int out=codec.dequeueOutputBuffer(info,1000);
            if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;}
            else if(out>=0){
              java.nio.ByteBuffer buf=codec.getOutputBuffer(out);
              if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.size>0&&started){buf.position(info.offset);buf.limit(info.offset+info.size);muxer.writeSampleData(track,buf,info);}
              codec.releaseOutputBuffer(out,false);
            }else break;
          }
        }
        bitmap.recycle();
      }
      codec.signalEndOfInputStream();
      boolean done=false;int loops=0;
      while(!done&&loops++<2000){
        int out=codec.dequeueOutputBuffer(info,10000);
        if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(!started){track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;}}
        else if(out>=0){
          java.nio.ByteBuffer buf=codec.getOutputBuffer(out);
          if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.size>0&&started){buf.position(info.offset);buf.limit(info.offset+info.size);muxer.writeSampleData(track,buf,info);}
          done=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
          codec.releaseOutputBuffer(out,false);
        }
      }
      if(!done)throw new IOException("Encoder timeout");
    }finally{
      try{codec.stop();}catch(Exception ignored){}
      codec.release();surface.release();
      if(started)try{muxer.stop();}catch(Exception ignored){}
      muxer.release();
    }
  }
}
