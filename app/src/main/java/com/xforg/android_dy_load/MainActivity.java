package com.xforg.android_dy_load;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.tbruyelle.rxpermissions.RxPermissions;
import com.xforg.android_dy_load.util.HookUtil;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import dalvik.system.DexClassLoader;
import rx.functions.Action1;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private AssetManager mAssetManager;
    private Resources mResources;

    private Button button;
    private Button button2;
    private Button button3;
    private ImageView iv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: ");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = findViewById(R.id.button);
        button.setOnClickListener(this);
        button2 = findViewById(R.id.button2);
        button2.setOnClickListener(this);
        button3 = findViewById(R.id.button3);
        button3.setOnClickListener(this);
        iv = findViewById(R.id.iv);
        RxPermissions.getInstance(this).request(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE).subscribe(new Action1<Boolean>() {
            @Override
            public void call(Boolean aBoolean) {
                Log.d(TAG, "call: failed");
                if (aBoolean) {
                    Log.d(TAG, "call: WRITE STORAGE Permission 获取成功");
                }
            }
        });
    }


    @Override
    public void onClick(View v) {
        HookUtil.hookOnclickListener(v);
        switch (v.getId()){
            case R.id.button:
                Intent intent = new Intent(MainActivity.this,SecActivity.class);
                startActivity(intent);
                break;
            case R.id.button2:
                start();
                break;
            case R.id.button3:
                final String path = Environment.getExternalStorageDirectory() + File.separator + "resourse_plugin-debug.apk";
                final String pkgName = getUninstallApkPkgName(this, path);
                dynamicLoadApk(path,pkgName);
                Toast.makeText(this,"get Meizi from resourse_plugin-debug.apk",Toast.LENGTH_SHORT).show();
                break;
                default:
                    break;
        }
    }

    public void start(){
        //dex解压释放后的目录
        final File dexOutPutDir = getDir("dex", 0);
        //dex所在目录
        final String dexPath = Environment.getExternalStorageDirectory().toString() +
                File.separator + "new_plugox.jar";
        Log.d(TAG, "start: "+dexPath);
        //第一个参数：是dex压缩文件的路径
        //第二个参数：是dex解压缩后存放的目录
        //第三个参数：是C/C++依赖的本地库文件目录,可以为null
        //第四个参数：是上一级的类加载器
        DexClassLoader classLoader=new DexClassLoader(dexPath,dexOutPutDir.getAbsolutePath(),
                null,getClassLoader());
        try {
            final Class<?> loadClazz = classLoader.loadClass("com.xforg.plugin.plugin");
            final Object o = loadClazz.newInstance();
            final Method printLogMethod = loadClazz.getDeclaredMethod("func");
            printLogMethod.setAccessible(true);
            printLogMethod.invoke(o);
            Log.d(TAG, "start: ");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取未安装apk的信息
     * @param context
     * @param pApkFilePath apk文件的path
     * @return
     */
    private String getUninstallApkPkgName(Context context, String pApkFilePath) {
        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageArchiveInfo(pApkFilePath, PackageManager.GET_ACTIVITIES);
        if (pkgInfo != null) {
            ApplicationInfo appInfo = pkgInfo.applicationInfo;
            return appInfo.packageName;
        }
        return "";
    }

    private  void dynamicLoadApk(String pApkFilePath,String pApkPacketName){
        File file=getDir("dex", Context.MODE_PRIVATE);
        //第一个参数：是dex压缩文件的路径
        //第二个参数：是dex解压缩后存放的目录
        //第三个参数：是C/C++依赖的本地库文件目录,可以为null
        //第四个参数：是上一级的类加载器
        DexClassLoader  classLoader=new DexClassLoader(pApkFilePath,file.getAbsolutePath(),null,getClassLoader());
        try {
            final Class<?> loadClazz = classLoader.loadClass(pApkPacketName + ".R$drawable");
            //插件中meizi图片的名称
            final Field meizi = loadClazz.getDeclaredField("meizi");
            meizi.setAccessible(true);
            //反射获取 meizi 的resousreId
            final int resousreId = (int) meizi.get(R.id.class);

            //可以加载插件资源的 Resources
            final Resources resources = createResources(pApkFilePath);
            mResources = resources;
            if (resources != null) {
                final Drawable drawable = resources.getDrawable(resousreId);
                iv.setBackground(drawable);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //这个Resources就可以加载非宿主apk中的资源
    private Resources createResources(String pFilePath){
        final AssetManager assetManager = createAssetManager(pFilePath);
        Resources superRes = this.getResources();
        return new Resources(assetManager, superRes.getDisplayMetrics(), superRes.getConfiguration());
    }

    /**
     * 获取AssetManager   用来加载插件资源
     * @param pFilePath  插件的路径
     * @return
     */
    private AssetManager createAssetManager(String pFilePath) {
        try {
            final AssetManager assetManager = AssetManager.class.newInstance();
            final Class<?> assetManagerClazz = Class.forName("android.content.res.AssetManager");
            final Method addAssetPathMethod = assetManagerClazz.getDeclaredMethod("addAssetPath", String.class);
            addAssetPathMethod.setAccessible(true);
            addAssetPathMethod.invoke(assetManager, pFilePath);
            mAssetManager = assetManager;
            return assetManager;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public AssetManager getAssets() {
        return mAssetManager == null ? super.getAssets():mAssetManager;
    }

    @Override
    public Resources getResources() {
        return mResources == null ? super.getResources():mResources;
    }

}
