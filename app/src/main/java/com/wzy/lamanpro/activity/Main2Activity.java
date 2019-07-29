package com.wzy.lamanpro.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.storage.StorageManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.fs.UsbFileOutputStream;
import com.wzy.lamanpro.R;
import com.wzy.lamanpro.bean.HisData;
import com.wzy.lamanpro.common.LaManApplication;
import com.wzy.lamanpro.dao.HisDaoUtils;
import com.wzy.lamanpro.dao.UserDaoUtils;
import com.wzy.lamanpro.utils.ChartUtil;
import com.wzy.lamanpro.utils.FileUtils;
import com.wzy.lamanpro.utils.PermissionGetting;
import com.wzy.lamanpro.utils.SPUtility;
import com.wzy.lamanpro.utils.SystemUtils;
import com.wzy.lamanpro.utils.UsbHelper;
import com.wzy.lamanpro.utils.UsbUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static com.wzy.lamanpro.common.LaManApplication.easyMode;
import static com.wzy.lamanpro.utils.UsbUtils.readFromUsb;
import static com.wzy.lamanpro.utils.UsbUtils.showTmsg;

public class Main2Activity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, View.OnClickListener {
    private static final byte[] SET_INIT_TIME = {0x09, 0x4F, 0x69, 0x74};//设置积分时间
    private static final byte[] SET_POWER = {0x09, 0x4F, 0x70, 0x77, 0x00, 0x00, 0x00, 0x00, (byte) 0xE8, 0x03, 0x00, 0x00};//设置能量大小
    private static final byte[] OPEN_PORT = {0x09, 0x4f, 0x65, 0x70, 0x02, 0x00, 0x00, 0x00};//打开激光
    private static final byte[] GET_DATA = {0x09, 0x4F, 0x53, 0x4F};//获取波形
    private static final byte[] CLOSE_PORT = {0x09, 0x4f, 0x65, 0x70, 0x00, 0x00, 0x00, 0x00};//关闭激光
    private static final String TAG = Main2Activity.class.getSimpleName();
    private static byte[][] results;
    private static float[] finalsResults;
    private LineChart lineChart;
    private Button button_start;
    private TextView text_report;
    private TextView text_location;
    private String stateText = "";
    private Toolbar toolbar;
    private TextView state;
    private DrawerLayout drawer;
    private String locationName;
    List<String> xDataList = new ArrayList<>();// x轴数据源
    List<Entry> yDataList = new ArrayList<Entry>();// y轴数据数据源
    //定位都要通过LocationManager这个类实现
    private LocationManager locationManager;
    private int once;
    private int time;
    private int power;
    private String provider;
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    UsbUtils.sendToUsb(CLOSE_PORT);
                    for (int i = 0; i < finalsResults.length; i++) {
                        xDataList.add(String.valueOf(i));
                        yDataList.add(new Entry(finalsResults[i], i));
                    }
                    button_start.setEnabled(true);
                    button_start.setText("开始测试");
//                    stateText.
                    stateText = "获取波形完成\n";
                    testCount = 0;
                    handler.sendEmptyMessage(2);
                    handler.sendEmptyMessage(3);

                    break;
                case 1:
                    Toast.makeText(Main2Activity.this, "请输入合理范围内的设置", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    state.setText(stateText);
                    lineChart.notifyDataSetChanged();
                    lineChart.invalidate();
                    break;
                case 3:
                    ChartUtil.showChart(Main2Activity.this, lineChart, xDataList, yDataList, "光谱图", "波长/时间", "");

                    break;
                case 4:
                    text_location.setText("位置是：" + locationName);
                    break;

            }
        }
    };
    private FloatingActionButton fab;
    private ProgressBar progress_bar;
    private NavigationView nav_view;
    private DrawerLayout drawer_layout;

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, usbFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                LaManApplication.canUseUsb = UsbUtils.initUsbData(Main2Activity.this, true);

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Toast.makeText(Main2Activity.this, "设备已移除！", Toast.LENGTH_SHORT).show();
                LaManApplication.canUseUsb = UsbUtils.initUsbData(Main2Activity.this, false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        initView();

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if (keyCode==KeyEvent.KEYCODE_VOLUME_UP){
//            Toast.makeText(Main2Activity.this,"关机",Toast.LENGTH_SHORT).show();
//            return true;
//        }
//        return super.onKeyDown(keyCode, event);
//
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main2, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                startActivity(new Intent(Main2Activity.this, SettingTest.class));
                return true;
            case R.id.use_info:
                new AlertDialog.Builder(Main2Activity.this)
                        .setMessage("内容正在完善中。。。")
                        .setTitle("使用说明:")
                        .setPositiveButton("我知道了", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).create().show();
                break;
            case R.id.data_copy:
                try {
                    UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this /* Context or Activity */);
                    if (devices.length == 0) {
                        showTmsg("备份失败，请插入u盘！！");
                        return false;
                    }
                    for (UsbMassStorageDevice device : devices) {
                        // before interacting with a device you need to call init()!
                        device.init();
                        // Only uses the first partition on the device
                        FileSystem currentFs = device.getPartitions().get(0).getFileSystem();
//                        Log.d("u盘连接成功", "Capacity: " + currentFs.getCapacity());

                        UsbFile root = currentFs.getRootDirectory();
                        UsbFile[] files = root.listFiles();
                        UsbFile destination = null;
                        for (UsbFile file : files) {
                            if (file.getName().equalsIgnoreCase("拉曼数据导出") && file.isDirectory()) {
                                destination = file;
                            }
                        }
                        if (destination == null || !destination.getName().equalsIgnoreCase("拉曼数据导出")) {
                            destination = root.createDirectory("拉曼数据导出");
                        }
                        File[] dirFiles = Environment.getExternalStorageDirectory().listFiles();
                        int count = 0;
                        for (File dirFile : dirFiles) {
//                            FileChannel inputChannel = null;

                            if (dirFile.getName().startsWith("拉曼测试报告-") && dirFile.getName().endsWith(".pdf")) {
//                            showTmsg(sdDirect);

                                FileUtils.saveSDFileToUsb(dirFile, destination, new UsbHelper.DownloadProgressListener() {
                                    @Override
                                    public void downloadProgress(int progress) {
//                                        Toast.makeText(Main2Activity.this, "复制文件中，已复制" + progress + "%", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                count++;

                            }
                        }

                        if (count == 0) {
                            showTmsg("没有数据文件！");
                        } else {
                            showTmsg("导出数据完成，一共导出" + count + "个文件！");
                        }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
//                    showTmsg("备份失败，请插入u盘！！");
                }


                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        switch (item.getItemId()) {
            case R.id.nav_camera:
                startActivity(new Intent(Main2Activity.this, AddLibrary.class));
                break;
            case R.id.nav_gallery:
                startActivity(new Intent(Main2Activity.this, ManageData.class));
                break;
            case R.id.nav_slideshow:
                startActivity(new Intent(Main2Activity.this, ManageHis.class));
                break;
            case R.id.nav_manage:
                startActivity(new Intent(Main2Activity.this, ManageUsers.class));
                break;
            case R.id.nav_setting:
                startActivity(new Intent(Main2Activity.this, SettingsActivity.class));
                break;
            case R.id.nav_logout:
                showStyleDialog();
                break;
            case R.id.nav_shutdown:
                SystemUtils.shutDowm();
                break;
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    protected void showStyleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("您确定注销吗？");
        builder.setTitle("温 馨 提 示 :");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SPUtility.putSPBoolean(Main2Activity.this, "isAutoLogin", false);
                finish();
                Intent intent = new Intent(Main2Activity.this, LoginActivity.class);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private void initView() {
//        stateText = new StringBuffer();
        lineChart = findViewById(R.id.lineChart);
        button_start = findViewById(R.id.button_start);
        text_report = findViewById(R.id.text_report);
        text_location = findViewById(R.id.text_location);
        button_start.setOnClickListener(this);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setOnClickListener(this);
        state = (TextView) findViewById(R.id.state);
        state.setMovementMethod(ScrollingMovementMethod.getInstance());

        setSupportActionBar(toolbar);
        LaManApplication.canUseUsb = UsbUtils.initUsbData(this, true);
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        View headerView = navigationView.getHeaderView(0);
        TextView textView = (TextView) headerView.findViewById(R.id.textView);
        textView.setText(new UserDaoUtils(this).queryUserName(SPUtility.getUserId(this)));

        //获取定位服务
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //获取当前可用的位置控制器
        List<String> list = locationManager.getProviders(true);
        if (list.contains(LocationManager.GPS_PROVIDER)) {
            //是否为GPS位置控制器
            provider = LocationManager.GPS_PROVIDER;
        } else if (list.contains(LocationManager.NETWORK_PROVIDER)) {
            //是否为网络位置控制器
            provider = LocationManager.NETWORK_PROVIDER;

        } else {
            Toast.makeText(this, "请检查网络或GPS是否打开",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Toast.makeText(Main2Activity.this, "我们需要获取位置的权限，请授予！", Toast.LENGTH_SHORT).show();
            PermissionGetting.showToAppSettingDialog();
        } else {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                //获取当前位置，这里只用到了经纬度
                locationName = getLocationAddress(location);
                handler.sendEmptyMessage(4);
            } else

                //绑定定位事件，监听位置是否改变
                //第一个参数为控制器类型第二个参数为监听位置变化的时间间隔（单位：毫秒）
                //第三个参数为位置变化的间隔（单位：米）第四个参数为位置监听器
                locationManager.requestLocationUpdates(provider, 2000, 2,
                        locationListener);
        }


        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);
        handler.sendEmptyMessage(3);

        AlertDialog.Builder builder = new AlertDialog.Builder(Main2Activity.this);
        builder.setMessage(LaManApplication.canUseUsb ? "仪器自检正常！" : "仪器没有连接！");
        builder.setTitle("仪器自检:");
        builder.setPositiveButton("我知道了", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
        alertDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent keyEvent) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    alertDialog.dismiss();
                    return true;

                }
                return false;
            }
        });

        progress_bar = (ProgressBar) findViewById(R.id.progress_bar);
        progress_bar.setOnClickListener(this);
        nav_view = (NavigationView) findViewById(R.id.nav_view);
        nav_view.setOnClickListener(this);
        drawer_layout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer_layout.setOnClickListener(this);
    }

    LocationListener locationListener = new LocationListener() {

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProviderEnabled(String arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProviderDisabled(String arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onLocationChanged(Location arg0) {
            // TODO Auto-generated method stub
            // 更新当前经纬度
            if (TextUtils.isEmpty(locationName)) {
                locationName = getLocationAddress(arg0);
                handler.sendEmptyMessage(4);
            }
//            locationName = getLocationAddress(arg0);
//            stateText.append("位置是：" + locationName);
//            handler.sendEmptyMessage(2);

        }
    };

    /**
     * 将经纬度转换成中文地址
     *
     * @param location
     * @return
     */
    private String getLocationAddress(Location location) {
        String add = "";
        Geocoder geoCoder = new Geocoder(getBaseContext(), Locale.CHINESE);
        try {
            List<Address> addresses = geoCoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(),
                    1);
            Address address = addresses.get(0);
            Log.i(TAG, "getLocationAddress: " + address.toString());
            // Address[addressLines=[0:"中国",1:"北京市海淀区",2:"华奥饭店公司写字间中关村创业大街"]latitude=39.980973,hasLongitude=true,longitude=116.301712]
//            int maxLine = address.getMaxAddressLineIndex();
//            if (maxLine >= 2) {
//                add = address.getAddressLine(1) + address.getAddressLine(2);
//            } else {
//                add = address.getAddressLine(1);
//            }
            add = address.getAddressLine(0);
        } catch (IOException e) {
            add = "";
            e.printStackTrace();
        } catch (IndexOutOfBoundsException e) {
            add = "获取位置失败！";
        }
        return add;
    }

    //关闭时解除监听器
    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    private int testCount = 0;

    //音量键，唤起开始测试
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
//            Toast.makeText(Main2Activity.this, testCount + "", Toast.LENGTH_SHORT).show();

            switch (testCount) {
                case 0:
                    test();
//                    testCount++;
                    return true;

                default:

                    return super.onKeyDown(keyCode, event);
            }

        }
        return super.onKeyDown(keyCode, event);

    }

    private void test() {
        if (LaManApplication.canUseUsb) {
            ImageView imageView = new ImageView(Main2Activity.this);
            imageView.setImageResource(R.drawable.adangerous);
//                TextView textView=new TextView(Main2Activity.this);
//                textView.setText("当心激光辐射\n执行扫描时请勿将眼睛对着出射窗口！");

            AlertDialog.Builder builder = new AlertDialog.Builder(Main2Activity.this);

            builder.setView(imageView)
//                        .setView(textView)
                    .setMessage("当心激光辐射\n执行扫描时请勿将眼睛对着出射窗口！")
                    .setTitle("温 馨 提 示 :")
                    .setPositiveButton("我知道了", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            testNow();
                            testCount++;
                        }
                    });
            final AlertDialog alertDialog = builder.create();
            alertDialog.show();

            new Thread(new Runnable() {
                @Override
                public void run() {

                    try {
                        Thread.sleep(300); // 休眠1秒
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    /**
                     * 延时执行的代码
                     */
                    alertDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent keyEvent) {
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                testNow();
                                alertDialog.dismiss();
                                return true;
                            }
                            return false;
                        }
                    });
                }
            }).start();
        } else {
            Toast.makeText(Main2Activity.this, "请先连接光谱仪设备！", Toast.LENGTH_SHORT).show();
            testCount = 0;
        }

    }

    private void testNow() {
        if (!LaManApplication.canUseUsb) {
            Toast.makeText(Main2Activity.this, "请先连接光谱仪设备！", Toast.LENGTH_SHORT).show();

            return;
        }
        lineChart.clear();
        xDataList.clear();
        yDataList.clear();
        final int[] count = {0, 0};
        once = easyMode || TextUtils.isEmpty(SPUtility.getSPString(Main2Activity.this, "once")) ? 10 : Integer.valueOf(SPUtility.getSPString(Main2Activity.this, "once"));
        time = easyMode || TextUtils.isEmpty(SPUtility.getSPString(Main2Activity.this, "time")) ? 500 : Integer.valueOf(SPUtility.getSPString(Main2Activity.this, "time"));
        power = TextUtils.isEmpty(SPUtility.getSPString(Main2Activity.this, "power")) ? 66 : Integer.valueOf(SPUtility.getSPString(Main2Activity.this, "power"));
//        stateText = new StringBuffer();
        stateText = "开始测试，积分次数为" + once + "次\n";
        button_start.setEnabled(false);
        button_start.setText("正在测试");
        handler.sendEmptyMessage(2);
        testCount = 2;
        results = new byte[once][4200];
        finalsResults = new float[2100];
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        final String fileName = Environment.getExternalStorageDirectory() + File.separator + "拉曼测试原始数据-" + new Date().getTime() + ".txt";
        final Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                switch (count[0]) {
                    case 0:
                        try {
                            UsbUtils.sendToUsb(UsbUtils.addBytes(SET_INIT_TIME, UsbUtils.intTobyteLH(time * 1000)));
                            stateText = "第" + (count[1] + 1) + "次积分：  积分时间为" + time + "毫秒。  功率为：" + power + "。";
//                                        stateText.append("积分时间设置完毕，积分时间为" + time + "毫秒，发送的内容是：" + Arrays.toString(UsbUtils.addBytes(SET_INIT_TIME, UsbUtils.intTobyteLH(time * 1000))) + "\n");
                            handler.sendEmptyMessage(2);
                        } catch (NumberFormatException e) {
                            handler.sendEmptyMessage(1);
                        }
                        break;
                    case 1:
                        try {
                            UsbUtils.sendToUsb(UsbUtils.addBytes(SET_POWER, UsbUtils.intTobyteLH(power)));
//                            stateText = "功率为：" + power + "。";
//                                    stateText.append("功率设置发送完毕，发送的内容是：" + Arrays.toString(SET_POWER) + "\n");
//                            handler.sendEmptyMessage(2);
                        } catch (NumberFormatException e) {
                            handler.sendEmptyMessage(1);
                        }
                        break;
                    case 2:
                        UsbUtils.sendToUsb(OPEN_PORT);
//                                    stateText.append("打开激光发送完毕。\n");
//                                    stateText.append("打开激光发送完毕，发送的内容是：" + Arrays.toString(OPEN_PORT) + "\n");
//                        handler.sendEmptyMessage(2);
                        break;
                    case 3:
                        UsbUtils.sendToUsb(GET_DATA);
//                                    stateText.append("获取波形发送完毕。\n");
//                                    stateText.append("获取波形发送完毕，发送的内容是：" + Arrays.toString(GET_DATA) + "\n");
//                        handler.sendEmptyMessage(2);
                        break;
                    case 4:
                        results[count[1]++] = readFromUsb();
                        progress_bar.setProgress(count[1] * 100 / once);
                        FileUtils.writeFile(fileName, String.valueOf(results[count[1] - 1]) + "\r\n", true);
//                                    stateText.append("返回结果完毕并存储。\n");
//                                    stateText.append("返回的内容是：" + Arrays.toString(results) + "\n");
                        break;
                    case 5:
                        for (int i = 0; i < finalsResults.length; i++) {
                            finalsResults[i] = UsbUtils.twoByteToUnsignedInt(results[0][2 * i + 1], results[0][2 * i]);
                        }
                        label1:
                        for (int i = 0; i < results.length; i++) {
                            for (int j = 0; j < finalsResults.length; j++) {
                                if (i == 0) {
                                    continue label1;
                                } else {
                                    finalsResults[j] = (finalsResults[j] + UsbUtils.twoByteToUnsignedInt(results[i][2 * j + 1], results[i][2 * j])) / 2;
                                }
                            }
                        }
                        handler.sendEmptyMessage(0);
                        timer.cancel();
                        break;
                }
                count[0]++;
                if (once >= count[1] + 1)
                    count[0] %= 5;
            }
        };
        timer.schedule(timerTask, 50, 50);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_start:
                test();

                break;
            case R.id.fab:
                Snackbar.make(v, "点击此处可以保存测试数据，是否要保存？", Snackbar.LENGTH_LONG)
                        .setAction("保存", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (finalsResults == null || finalsResults.length == 0) {
                                    Toast.makeText(Main2Activity.this, "还没有测试数据，请先测试！", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                final EditText et = new EditText(Main2Activity.this);
                                new AlertDialog.Builder(Main2Activity.this)
                                        .setIcon(android.R.drawable.ic_dialog_info)
                                        .setView(et)
                                        .setTitle("请输入要保存的名字")
                                        .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                ProgressDialog pd2 = ProgressDialog.show(Main2Activity.this, "温馨提示", "正在保存...");
                                                String input = et.getText().toString();
                                                String userid = SPUtility.getUserId(Main2Activity.this);
                                                StringBuffer stringBuffer = new StringBuffer();
                                                for (float finalsResult : finalsResults) {
                                                    stringBuffer.append(finalsResult + ",");
                                                }
                                                boolean canSave = new HisDaoUtils(Main2Activity.this).insertHisDataList(new HisData(stringBuffer.toString(), new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()), input, new UserDaoUtils(Main2Activity.this).queryUserName(userid),
                                                        userid, String.valueOf(time), String.valueOf(power),
                                                        locationName));
                                                pd2.dismiss();
                                                if (canSave)
                                                    dialog.dismiss();
                                            }
                                        }).setNegativeButton("取消", null).create().show();
                            }
                        }).show();
                break;
        }
    }
}
