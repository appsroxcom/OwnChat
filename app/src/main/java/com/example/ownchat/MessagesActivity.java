package com.example.ownchat;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.stfalcon.chatkit.sample.common.data.model.Dialog;
import com.stfalcon.chatkit.sample.common.data.model.Message;
import com.stfalcon.chatkit.sample.common.data.model.User;
import com.stfalcon.chatkit.sample.features.demo.custom.media.CustomMediaMessagesActivity;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import live.chatkit.android.CallbackListener;
import live.chatkit.android.ChatKit;
import live.chatkit.android.ChatRepository;
import live.chatkit.android.ChatUtil;
import live.chatkit.android.FileRepository;
import live.chatkit.android.FileUtil;
import live.chatkit.android.ResultListener;
import live.chatkit.android.UserRepository;
import live.chatkit.android.crypto.CryptoUtil;
import live.chatkit.android.model.AttachmentVO;
import live.chatkit.android.model.ChatVO;
import live.chatkit.android.model.KeyVO;
import live.chatkit.android.model.MessageVO;
import live.chatkit.android.model.UserVO;

import static com.example.ownchat.CommonUtil.CHATS_LIMIT;
import static com.example.ownchat.CommonUtil.MESSAGES_LIMIT;
import static com.example.ownchat.CommonUtil.REFRESH_INTERVAL;
import static live.chatkit.android.Constants.ADDED;
import static live.chatkit.android.Constants.CHAT_ID;
import static live.chatkit.android.Constants.MODIFIED;
import static live.chatkit.android.Constants.REMOVED;
import static live.chatkit.android.Constants.USER_ID;

public class MessagesActivity extends CustomMediaMessagesActivity {

    private static final int REQUEST_ATTACH = 101;

    private String mChatId;
    private String mParticipantId;
    private Date mLastLoadedDate, mListenerStartDate;
    private String mChatKey;
    private static long sLastRefresh;

    private boolean isRefresh() {
        return System.currentTimeMillis() - sLastRefresh > REFRESH_INTERVAL;
    }
    private void setRefresh() {
        sLastRefresh = System.currentTimeMillis();
    }

    public static void startChat(String chatId, Context context) {
        context.startActivity(new Intent(context, MessagesActivity.class).putExtra(CHAT_ID, chatId));
    }

    public static void startChatWith(String participantId, Context context) {
        context.startActivity(new Intent(context, MessagesActivity.class).putExtra(USER_ID, participantId));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ATTACH) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    saveAttachment(data.getData());
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mChatId = getIntent().getStringExtra(CHAT_ID);
        mParticipantId = getIntent().getStringExtra(USER_ID);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("chatId", mChatId);
        outState.putString("participantId", mParticipantId);
        outState.putSerializable("lastLoadedDate", mLastLoadedDate);
        outState.putSerializable("listenerStartDate", mListenerStartDate);
        outState.putString("chatKey", mChatKey);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mChatId = savedInstanceState.getString("chatId");
        mParticipantId = savedInstanceState.getString("participantId");
        mLastLoadedDate = (Date) savedInstanceState.getSerializable("lastLoadedDate");
        mListenerStartDate = (Date) savedInstanceState.getSerializable("listenerStartDate");
        mChatKey = savedInstanceState.getString("chatKey");
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!TextUtils.isEmpty(mChatId))
            ChatRepository.getInstance().resetUnreadCount(mChatId, getApplicationContext());
    }

    @Override
    protected void onStop() {
        detachListener();
        super.onStop();
    }

    @Override
    public boolean onSubmit(CharSequence input) {
        if (getCurrentUser() == null) return false;
        showProgress();
        Message message = new Message(null, getCurrentUser(), input.toString());
        save(message);
        return true;
    }

    @Override
    public void onAddAttachments() {
        if (getCurrentUser() == null) return;
        showPicker();
    }

    public void showPicker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_ATTACH);
        } else {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");//image/*
                /*intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                //intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);*/
            startActivityForResult(intent, REQUEST_ATTACH);
        }
    }

    private void saveAttachment(final Uri localUri) {
        Pair fileInfo = FileUtil.getFileInfo(localUri, getApplicationContext());
        final String fileName = String.valueOf(fileInfo.first);
        long fileSize = Long.parseLong(String.valueOf(fileInfo.second));

        long maxSize = Long.parseLong(getString(R.string.max_size_in_bytes));
        if (fileSize > maxSize) {
            Toast.makeText(this, getString(R.string.err_size, FileUtil.getReadableFileSize(maxSize)), Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.upload_confirm, fileName, FileUtil.getReadableFileSize(fileSize)))
                .setPositiveButton(android.R.string.yes,  new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface confirm, int which) {
                        showProgress();
                        FileRepository.getInstance().putFile(localUri, ChatUtil.getStorageDir(getApplicationContext()), new ResultListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                hideProgress();
                            }

                            @Override
                            public void onSuccess(Object result) {
                                AttachmentVO attachment = (AttachmentVO) result;
                                Message message = new Message(null, getCurrentUser(), fileName);
                                if (FileUtil.isImage(attachment.type)) message.setImage(new Message.Image(attachment.url));
                                else message.setFile(new Message.File(attachment.url, attachment.type, attachment.size));
                                save(message);
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
    }

    private void save(final Message message) {
        if (!TextUtils.isEmpty(mChatId)) {
            saveMessage(message, mChatId);

        } else if (!TextUtils.isEmpty(mParticipantId)) {
            final String currentUserId = getCurrentUser().getId();
            ChatRepository.getInstance().fetchChats(false, currentUserId, CHATS_LIMIT, new ResultListener() {
                @Override
                public void onSuccess(Object result) {
                    for (ChatVO chat : (List<ChatVO>) result) {
                        if (chat.participants.contains(mParticipantId) && chat.participants.size() == 2) {
                            mChatId = chat.id;
                            saveMessage(message, mChatId);
                            return;
                        }
                    }

                    ChatVO chat = new ChatVO();
                    chat.participants = Arrays.asList(mParticipantId, currentUserId);
                    ChatRepository.getInstance().addChat(chat, new ResultListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            super.onFailure(e);
                        }

                        @Override
                        public void onSuccess(Object result) {
                            mChatId = (String) result;

                            mChatKey = UUID.randomUUID().toString();
                            fetchUser(mParticipantId, new CallbackListener() {
                                @Override
                                public void onResult(Object result) {
                                    setKey(mChatKey, mChatId, (UserVO) result);
                                }
                            });
                            fetchUser(currentUserId, new CallbackListener() {
                                @Override
                                public void onResult(Object result) {
                                    setKey(mChatKey, mChatId, (UserVO) result);
                                }
                            });

                            saveMessage(message, mChatId);
                        }
                    });
                }
            });
        }
    }

    private void setKey(String chatKey, String chatId, UserVO user) {
        try {
            String encryptedKey = CryptoUtil.encrypt(chatKey, user.publicKey);
            ChatRepository.getInstance().setKey(new KeyVO(encryptedKey), chatId, user.id, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveMessage(final Message message, final String chatId) {
        if (mChatKey == null) {
            //init keys
            fetchChatKey(chatId, getCurrentUser().getId(), new CallbackListener() {
                @Override
                public void onResult(Object result) {
                    try {
                        mChatKey = ChatUtil.decryptKey(getApplicationContext(), ((KeyVO) result).key);
                        saveMessage(message, chatId, mChatKey);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } else {
            saveMessage(message, chatId, mChatKey);
        }
    }

    private void saveMessage(final Message message, final String chatId, String chatKey) {
        ChatRepository.getInstance().addMessage(chatId, new MessageVO(message, chatKey), new ResultListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                hideProgress();
            }

            @Override
            public void onSuccess(Object result) {
                hideProgress();
                message.setId((String) result);
                if (mLastLoadedDate == null) {
                    ChatRepository.getInstance().fetchMessage(false, chatId, message.getId(), new ResultListener() {
                        @Override
                        public void onSuccess(Object result) {
                            message.setCreatedAt(((MessageVO) result).createdAt);
                            mLastLoadedDate = message.getCreatedAt();

                            messagesAdapter.addToStart(
                                    message, true);
                        }
                    });
                } else {
                    messagesAdapter.addToStart(
                            message, true);
                }
            }
        });
    }

    @Override
    protected void onInit(User currentUser) {
        ChatKit.getInstance().onInit(currentUser.getId());
        initAdapter();

        if (!TextUtils.isEmpty(mChatId)) {
            fetchChat(mChatId);
        } else if (!TextUtils.isEmpty(mParticipantId)) {
            fetchChats(mParticipantId);
        }
    }

    private void fetchChats(final String participantId) {
        final String currentUserId = getCurrentUser().getId();
        ChatRepository.getInstance().fetchChats(true, currentUserId, CHATS_LIMIT, new ResultListener() {
            @Override
            public void onSuccess(Object result) {
                for (ChatVO chat : (List<ChatVO>) result) {
                    if (chat.participants.contains(participantId) && chat.participants.size() == 2) {
                        mChatId = chat.id;
                        fetchChat(mChatId);
                        return;
                    }
                }

                ChatRepository.getInstance().fetchChats(false, currentUserId, CHATS_LIMIT, new ResultListener() {
                    @Override
                    public void onSuccess(Object result) {
                        for (ChatVO chat : (List<ChatVO>) result) {
                            if (chat.participants.contains(participantId) && chat.participants.size() == 2) {
                                mChatId = chat.id;
                                fetchChat(mChatId);
                                return;
                            }
                        }
                    }
                });
            }
        });
    }

    private void fetchChat(final String chatId) {
        ChatRepository.getInstance().fetchChat(true, chatId, new ResultListener() {
            @Override
            public void onSuccess(Object result) {
                if (result == null) {
                    ChatRepository.getInstance().fetchChat(false, chatId, new ResultListener() {
                        @Override
                        public void onSuccess(Object result) {
                            if (result != null) {
                                initChat((ChatVO) result);
                            }
                        }
                    });
                } else {
                    initChat((ChatVO) result);
                }
            }
        });
    }

    private void initChat(final ChatVO chat) {
        final String currentUserId = getCurrentUser().getId();
        mParticipantId = ChatUtil.getParticipantId(chat, currentUserId);

        final Map<String, UserVO> userMap = UserRepository.getInstance().getUserMap();
        if (!userMap.containsKey(mParticipantId)) {
            fetchUser(mParticipantId, new CallbackListener() {
                @Override
                public void onResult(Object result) {
                    ChatUtil.populateChatDetails(chat, userMap, currentUserId);
                    onInitChat(ChatVO.toDialog(getApplicationContext(), chat, userMap, null, mChatKey));
                }
            });
        } else {
            ChatUtil.populateChatDetails(chat, userMap, currentUserId);
            onInitChat(ChatVO.toDialog(getApplicationContext(), chat, userMap, null, mChatKey));
        }

        if (isRefresh()) UserRepository.getInstance().fetchUser(false, mParticipantId, new ResultListener() {
            @Override
            public void onSuccess(Object result) {
                setRefresh();
            }
        });
    }

    private void onInitChat(Dialog currentChat) {
        setTitle(currentChat.getDialogName());

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            Map<String, UserVO> userMap = UserRepository.getInstance().getUserMap();
            if (userMap.containsKey(mParticipantId)) actionBar.setSubtitle(userMap.get(mParticipantId).bio);
        }

        //init keys
        fetchChatKey(currentChat.getId(), getCurrentUser().getId(), new CallbackListener() {
            @Override
            public void onResult(Object result) {
                try {
                    mChatKey = ChatUtil.decryptKey(getApplicationContext(), ((KeyVO) result).key);
                    loadMessages();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void loadMessages() {
        showLoader();
        ChatRepository.getInstance().fetchMessages(mChatId, mLastLoadedDate, MESSAGES_LIMIT, new ResultListener() {
            @Override
            public void onSuccess(Object result) {
                hideLoader();
                Date listenerStartDate = new Date(0);
                try {
                    List<MessageVO> messages = (List<MessageVO>) result;
                    if (messages.isEmpty()) return;

                    listenerStartDate = messages.get(0).createdAt;
                    mLastLoadedDate = messages.get(messages.size() - 1).createdAt;
                    Map<String, UserVO> userMap = UserRepository.getInstance().getUserMap();
                    messagesAdapter.addToEnd(MessageVO.toMessageList(messages, userMap, mChatKey), false);

                } finally {
                    if (mListenerStartDate == null) {
                        mListenerStartDate = listenerStartDate;
                        attachListener();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Exception e) {
                hideLoader();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.action_delete) {
            for (Message message : messagesAdapter.getSelectedMessages()) {
                if (message.getImageUrl() != null) {
                    FileRepository.getInstance().deleteFile(message.getImageUrl(), null);
                }
                if (message.getFile() != null) {
                    FileRepository.getInstance().deleteFile(message.getFile().getUrl(), null);
                }

                ChatRepository.getInstance().deleteMessage(mChatId, message.getId(), null);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSelectionChanged(int count) {
        super.onSelectionChanged(count);
        if (count > 0) {
            for (Message message : messagesAdapter.getSelectedMessages()) {
                if (!getCurrentUser().getId().equals(message.getUser().getId())) {
                    menu.findItem(R.id.action_delete).setVisible(false);
                    break;
                }
            }
        }
    }

    @Override
    protected void initAdapter() {
        super.initAdapter();
        super.messagesAdapter.registerViewClickListener(R.id.download,
                new MessagesListAdapter.OnMessageViewClickListener<Message>() {
                    @Override
                    public void onMessageViewClick(View view, Message message) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(message.getFile().getUrl())));
                    }
                });
        super.messagesAdapter.registerViewClickListener(R.id.open,
                new MessagesListAdapter.OnMessageViewClickListener<Message>() {
                    @Override
                    public void onMessageViewClick(View view, Message message) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(message.getImageUrl())));
                    }
                });
    }

    private void attachListener() {
        if (mChatId == null || mListenerStartDate == null) return;
        final Map<String, UserVO> userMap = UserRepository.getInstance().getUserMap();
        ChatRepository.getInstance().registerMessageListener(mChatId, mListenerStartDate, new ResultListener() {
            @Override
            public void onSuccess(Object result) {
                Pair payload = (Pair) result;
                Message message = MessageVO.toMessage((MessageVO) payload.second, userMap, mChatKey);
                if (getCurrentUser().getId().equals(message.getUser().getId())) return;

                byte change = (byte) payload.first;
                if (change == REMOVED) {
                    messagesAdapter.delete(message);
                } else if (change == ADDED) {
                    if (mLastLoadedDate == null)
                        mLastLoadedDate = message.getCreatedAt();
                    messagesAdapter.upsert(message);
                } else if (change == MODIFIED) {
                    messagesAdapter.upsert(message);
                }
            }
        });
    }

    private void detachListener() {
        ChatRepository.getInstance().unregisterMessageListener();
    }

}
