package com.xforg.android_dy_load.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created By apple on 2019/3/16
 * github: https://github.com/xianfeng92
 */
public class HookUtil {
    private static final String TAG = "HookUtil";

    private Class<?> proxyActivity;
    private Context context;

    public HookUtil(Class<?> proxyActivity, Context context){
        this.proxyActivity = proxyActivity;
        this.context = context;
    }

    public void hookAMS(){

        try {
            Class<?> ActivityManager = Class.forName("android.app.ActivityManager");
            Field defaultField = ActivityManager.getDeclaredField("IActivityManagerSingleton");
            defaultField.setAccessible(true);
            Object defaultValue = defaultField.get(null);


            //反射SingleTon
            Class<?> SingletonClass = Class.forName("android.util.Singleton");
            Field mInstance = SingletonClass.getDeclaredField("mInstance");
            mInstance.setAccessible(true);
            Object iActivityManagerObject = mInstance.get(defaultValue);

            //开始动态代理，用代理对象替换掉真实的 ActivityManager，瞒天过海
            Class<?> IActivityManagerIntercept = Class.forName("android.app.IActivityManager");
            AmsInvocationHandler handler = new AmsInvocationHandler(iActivityManagerObject);
            Object proxy = Proxy.newProxyInstance(Thread.currentThread().
                    getContextClassLoader(), new Class<?>[]{IActivityManagerIntercept}, handler);
            Log.d(TAG, "hookAMS: ");
            //现在替换掉这个对象
            mInstance.set(defaultValue, proxy);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private class AmsInvocationHandler implements InvocationHandler{

        private Object iActivityManagerObject;

        public AmsInvocationHandler(Object iActivityManagerObject){
            this.iActivityManagerObject = iActivityManagerObject;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "invoke: "+method.getName());
            // check the method system call
            if ("startActivity".contains(method.getName())){
                Log.d(TAG, "invoke: "+"Hook到Activity已经开始启动");
                Intent intent = null;
                int index = 0;
                for(int i = 0; i < args.length;i++){
                    Object arg = args[i];
                    if (arg instanceof Intent){
                        //说明找到了 startActivity 的Intent参数
                        intent = (Intent) args[i];
                        //这个意图是不能被启动的，因为 Acitivity 没有在清单文件中注册
                        index = i;
                        Log.d(TAG, "invoke: "+i);
                    }
                }
                //伪造一个代理的Intent，代理 Intent 启动的是 proxyActivity
                Intent proxyIntent = new Intent();
                ComponentName componentName = new ComponentName(context,proxyActivity);
                proxyIntent.setComponent(componentName);
                proxyIntent.putExtra("oldIntent",intent);
                args[index] = proxyIntent;
            }
            return method.invoke(iActivityManagerObject,args);
        }
    }

    private class ActivityThreadHandlerCallback implements Handler.Callback{

        private Handler handler;

        public ActivityThreadHandlerCallback(Handler handler){
            this.handler = handler;
        }


        @Override
        public boolean handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: "+msg);
              // 在 Android P中无效
//            if (msg.what == 159) {
//                Log.d(TAG, "handleMessage: handleLaunchActivity");
//                handleLaunchActivity(msg);
//            }
            handler.handleMessage(msg);
            return true;
        }

        private void handleLaunchActivity(Message msg){

            Object obj = msg.obj;// ActivityClientRecord
            try {
                Field intentField = obj.getClass().getDeclaredField("intent");
                intentField.setAccessible(true);
                Intent proxyIntent = (Intent) intentField.get(obj);
                Intent realIntent = proxyIntent.getParcelableExtra("oldIntent");
                if (realIntent != null){
                    proxyIntent.setComponent(realIntent.getComponent());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public void hookSystemHandler() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            //获取主线程对象
            Object activityThread = currentActivityThreadMethod.invoke(null);
            //获取mH字段
            Field mH = activityThreadClass.getDeclaredField("mH");
            mH.setAccessible(true);
            //获取Handler
            Handler handler = (Handler) mH.get(activityThread);
            //获取原始的mCallBack字段
            Field mCallBack = Handler.class.getDeclaredField("mCallback");
            mCallBack.setAccessible(true);
            //这里设置了我们自己实现了接口的 CallBack 对象
            mCallBack.set(handler, new ActivityThreadHandlerCallback(handler)) ;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void hookOnclickListener(View view){
        try {
            Method getListenerInfo = View.class.getDeclaredMethod("getListenerInfo");
            getListenerInfo.setAccessible(true);
            // 通过反射调用 view 的 getListenerInfo 方法
            Object listenerInfo = getListenerInfo.invoke(view);

            // 通过反射，重新构建一个 ListenerInfo 的Class对象
            Class<?> listenenerInfoClazz = Class.forName("android.view.View$ListenerInfo");
            // 获取 ListenerInfo Class对象中的 mOnClickListener
            Field mOnClickListener = listenenerInfoClazz.getDeclaredField("mOnClickListener");
            mOnClickListener.setAccessible(true);
            // 返回 listenerInfo 的 mOClickListener 属性的值
            View.OnClickListener  originOnClickListener = (View.OnClickListener) mOnClickListener.get(listenerInfo);

            // 用自定义的 OnClickListener 替换原始的 OnClickListener
            View.OnClickListener hookedOnCLickListener = new HookedOnClickListener(originOnClickListener);
            mOnClickListener.set(listenerInfo,hookedOnCLickListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class HookedOnClickListener implements View.OnClickListener{

        private View.OnClickListener origin;

        public HookedOnClickListener(View.OnClickListener origin){
            this.origin = origin;
        }

        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: Before click, do what you want to to.");
            if (origin != null) {
                origin.onClick(v);
            }
            Log.d(TAG, "onClick: After click, do what you want to to.");
        }
    }
}
