package com.wzy.lamanpro.activity;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ListView;
import android.widget.Toast;

import com.wzy.lamanpro.R;
import com.wzy.lamanpro.adapter.UserAdapter;
import com.wzy.lamanpro.bean.Users;
import com.wzy.lamanpro.dao.UserDaoUtils;

import java.util.List;

public class ManageUsers extends AppCompatActivity implements View.OnClickListener {

//    private Toolbar toolbar;
    private ListView userList;
    private FloatingActionButton fab;
    private List<Users> users;
    private UserAdapter userAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);
        initView();
        initData();
    }

    private void initData() {

        users = new UserDaoUtils(this).queryAllUsers();
        userAdapter = new UserAdapter(users, this, R.layout.item_users);
        userList.setAdapter(userAdapter);

    }

    private void initView() {
//        toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        userList = (ListView) findViewById(R.id.userList);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab:
                Snackbar.make(v, "点击此处可以添加用户，是否要添加？", Snackbar.LENGTH_LONG)
                        .setAction("添加", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Toast.makeText(ManageUsers.this, "添加", Toast.LENGTH_SHORT).show();
                            }
                        }).show();
                break;
        }
    }
}
