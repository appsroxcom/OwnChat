package com.example.ownchat;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.stfalcon.chatkit.sample.common.data.model.Dialog;
import com.stfalcon.chatkit.sample.common.data.model.User;
import com.stfalcon.chatkit.sample.features.demo.custom.layout.CustomLayoutDialogsActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import live.chatkit.android.CallbackListener;
import live.chatkit.android.ChatKit;
import live.chatkit.android.ChatRepository;
import live.chatkit.android.ChatUtil;
import live.chatkit.android.ResultListener;
import live.chatkit.android.UserRepository;
import live.chatkit.android.model.ChatVO;
import live.chatkit.android.model.KeyVO;
import live.chatkit.android.model.UserVO;

import static com.example.ownchat.CommonUtil.CHATS_LIMIT;
import static com.example.ownchat.CommonUtil.REFRESH_INTERVAL;
import static live.chatkit.android.Constants.CHAT_ID;

public class ChatsActivity extends CustomLayoutDialogsActivity {

    private SharedPreferences mPrefs;
    private Set<String> mHiddenChats;
    private boolean mShowAll;
    private List<Dialog> mHiddenItems;
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
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mHiddenChats = mPrefs.getStringSet("hiddenChats", new HashSet<String>());
        mHiddenItems = new ArrayList<>();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (super.dialogsAdapter != null)
            super.dialogsAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("showAll", mShowAll);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mShowAll = savedInstanceState.getBoolean("showAll");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chats, menu);
        MenuItem showAllItem = menu.findItem(R.id.action_show_all);
        showAllItem.setVisible(!mHiddenChats.isEmpty() && !mShowAll);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_show_all) {
            mShowAll = true;
            super.dialogsAdapter.addItems(mHiddenItems);
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void refresh() {
        super.refresh();
        invalidateOptionsMenu();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPrefs.edit().putStringSet("hiddenChats", mHiddenChats).apply();
    }

    @Override
    public void onDialogClick(Dialog dialog) {
        dialog.setUnreadCount(0);
        Intent intent = new Intent(this, MessagesActivity.class);
        intent.putExtra(CHAT_ID, dialog.getId());
        startActivity(intent);
    }

    @Override
    public void onDialogLongClick(final Dialog dialog) {
        final String chatId = dialog.getId();
        final boolean isHidden = mHiddenChats.contains(chatId);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(dialog.getDialogName());
        builder.setItems(isHidden ? R.array.unhide_leave : R.array.hide_leave, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface alert, int which) {
                        switch(which) {
                            case 0:
                                if (isHidden) {//unhide
                                    mHiddenChats.remove(chatId);
                                    mHiddenItems.remove(dialog);
                                } else {//hide
                                    mHiddenChats.add(chatId);
                                    mHiddenItems.add(dialog);

                                    mShowAll = false;
                                    ChatsActivity.super.dialogsAdapter.deleteById(chatId);
                                    refresh();
                                }
                                break;

                            case 1:
                                new AlertDialog.Builder(ChatsActivity.this)
                                        .setTitle(dialog.getDialogName())
                                        .setMessage(R.string.leave_confirm)
                                        .setPositiveButton(android.R.string.yes,  new DialogInterface.OnClickListener() {

                                            @Override
                                            public void onClick(DialogInterface confirm, int which) {
                                                ChatRepository.getInstance().leaveChat(chatId, getCurrentUser().getId(), new ResultListener() {
                                                    @Override
                                                    public void onSuccess(Object result) {
                                                        mHiddenChats.remove(chatId);
                                                        mHiddenItems.remove(dialog);

                                                        ChatsActivity.super.dialogsAdapter.deleteById(chatId);
                                                        refresh();
                                                    }
                                                });
                                            }
                                        })
                                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

                                            @Override
                                            public void onClick(DialogInterface confirm, int which) {
                                                confirm.dismiss();
                                            }
                                        })
                                        .create().show();
                                break;
                        }
                    }
                });
        builder.create().show();
    }

    @Override
    protected void onInit(final User currentUser) {
        ChatKit.getInstance().onInit(currentUser.getId());
        final boolean isCached = !isRefresh();
        ChatRepository.getInstance().fetchChats(isCached, currentUser.getId(), CHATS_LIMIT, new ResultListener() {
            @Override
            public void onSuccess(Object result) {
                final List<ChatVO> chats = (List<ChatVO>) result;
                if (!isCached && !chats.isEmpty()) setRefresh();

                Set<String> users = new HashSet<>();
                final Map<String, Integer> unreadCountMap = new HashMap<>();
                final Map<String, String> chatKeyMap = new ConcurrentHashMap<>();

                Map<String, KeyVO> keyMap = ChatRepository.getInstance().getKeyMap();
                for (Map.Entry<String, KeyVO> entry : keyMap.entrySet()) {
                    String chatKey = getChatKey(entry.getValue());
                    if (!TextUtils.isEmpty(chatKey)) chatKeyMap.put(entry.getKey(), chatKey);
                }

                for (final ChatVO chat : chats) {
                    users.addAll(chat.participants);
                    unreadCountMap.put(chat.id, ChatRepository.getInstance().getUnreadCount(chat.id, getApplicationContext()));

                    if (!chatKeyMap.containsKey(chat.id)) {
                        fetchChatKey(chat.id, currentUser.getId(), new CallbackListener() {
                            @Override
                            public void onResult(Object result) {
                                String chatKey = getChatKey((KeyVO) result);
                                if (!TextUtils.isEmpty(chatKey)) chatKeyMap.put(chat.id, chatKey);
                            }
                        });
                    }
                }

                if (!users.isEmpty()) {
                    final Map<String, UserVO> userMap = UserRepository.getInstance().getUserMap();
                    List<String> _users = new ArrayList<>(users);
                    _users.removeAll(userMap.keySet());
                    boolean isNew = !_users.isEmpty();
                    UserRepository.getInstance().fetchUsers(!isNew, isNew ? _users : new ArrayList<>(users), new ResultListener() {
                        @Override
                        public void onSuccess(Object result) {
                            ChatUtil.populateChatDetails(chats, userMap, currentUser.getId());
                            initAdapter(ChatVO.toDialogList(getApplicationContext(), chats, userMap, unreadCountMap, chatKeyMap));
                        }
                    });
                } else {
                    initAdapter(new ArrayList<Dialog>());
                }
            }
        });
    }

    @Override
    protected void initAdapter(List<Dialog> items) {
        if (!mHiddenChats.isEmpty()) {
            mHiddenItems.clear();
            for (Dialog chat : items) {
                if (mHiddenChats.contains(chat.getId())) mHiddenItems.add(chat);
            }
            items.removeAll(mHiddenItems);
        }
        super.initAdapter(items);
    }

    private String getChatKey(KeyVO key) {
        try {
            return ChatUtil.decryptKey(getApplicationContext(), key.key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
