package com.example.ownchat;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.stfalcon.chatkit.sample.common.data.model.Dialog;
import com.stfalcon.chatkit.sample.common.data.model.Message;
import com.stfalcon.chatkit.sample.common.data.model.User;
import com.stfalcon.chatkit.sample.features.demo.custom.holder.CustomHolderDialogsActivity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import live.chatkit.android.ChatKit;
import live.chatkit.android.ResultListener;
import live.chatkit.android.UserRepository;
import live.chatkit.android.model.UserVO;

import static com.example.ownchat.CommonUtil.REFRESH_INTERVAL;
import static com.example.ownchat.CommonUtil.USERS_LIMIT;
import static live.chatkit.android.Constants.USER_ID;

public class UsersActivity extends CustomHolderDialogsActivity {

    private static final int TIME_INTERVAL = 2000; // # milliseconds, desired time passed between two back presses.
    private long mBackPressed;
    private static long sLastRefresh;

    private boolean isRefresh() {
        return System.currentTimeMillis() - sLastRefresh > REFRESH_INTERVAL;
    }
    private void setRefresh() {
        sLastRefresh = System.currentTimeMillis();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.users, menu);
        MenuItem chatsItem = menu.findItem(R.id.action_chats);
        chatsItem.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_forum_24));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_chats) {
            startActivity(new Intent(this, ChatsActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mBackPressed + TIME_INTERVAL > System.currentTimeMillis()) {
            super.onBackPressed();
            return;
        } else {
            Toast.makeText(getBaseContext(), R.string.press_exit, Toast.LENGTH_SHORT).show();
        }
        mBackPressed = System.currentTimeMillis();
    }

    @Override
    public void onDialogClick(final Dialog dialog) {
        Intent intent = new Intent(this, MessagesActivity.class);
        intent.putExtra(USER_ID, dialog.getId());
        startActivity(intent);
    }

    @Override
    public void onDialogLongClick(Dialog dialog) {
    }

    @Override
    protected void onInit(final User currentUser) {
        ChatKit.getInstance().onInit(currentUser.getId());
        final boolean isCached = !isRefresh();
        UserRepository.getInstance().fetchUsers(isCached, currentUser.getId(), USERS_LIMIT, new ResultListener() {
            @Override
            public void onSuccess(Object result) {
                List<UserVO> users = (List<UserVO>) result;
                if (!isCached && !users.isEmpty()) setRefresh();

                List<Dialog> items = new ArrayList<>();
                int index = -1;
                for (User user : UserVO.toUserList(users)) {
                    index++;
                    ArrayList<User> participants = new ArrayList<User>();
                    participants.add(user);
                    participants.add(currentUser);

                    Date lastActive = users.get(index).updatedAt;
                    if (lastActive == null) lastActive = new Date();

                    items.add(new Dialog(user.getId(), user.getName(), user.getAvatar(),
                            participants, new Message(null, new User(user.getId(), null, null, false), users.get(index).bio, lastActive), 0));
                }
                initAdapter(items);
            }
        });
    }
}
